package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.RabbitMQConstants;
import com.xs.sheepaimall.config.ChatProperties;
import com.xs.sheepaimall.dto.ChatMQMessage;
import com.xs.sheepaimall.dto.ChatSendDTO;
import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.mapper.*;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.ChatService;
import com.xs.sheepaimall.vo.ChatMessageVO;
import com.xs.sheepaimall.vo.ChatSessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** 智能客服 Service 实现 — Redis 优先 + MySQL 持久化，Redis 不可用时自动降级 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final String REDIS_MSG_KEY = "chat:msg:";
    private static final long MSG_TTL_HOURS = 168;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatUserProfileMapper chatUserProfileMapper;

    @Autowired
    private ChatRagDocMapper chatRagDocMapper;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ChatProperties chatProperties;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /** Redis 连接状态缓存，避免每次请求都 PING */
    private volatile boolean redisOk = true;

    @Override
    public Flux<String> sendMessage(ChatSendDTO dto) {
        Long currentUserId = UserContext.getUserId();
        ChatSession session = getOrCreateSession(currentUserId, dto.getSessionId());
        Long sessionId = session.getId();

        // 通过响应头告诉前端 sessionId（新建会话时前端需要）
        setHeader("X-Session-Id", String.valueOf(sessionId));
        if (StrUtil.isNotBlank(session.getTitle())) {
            setHeader("X-Session-Title", session.getTitle());
        }

        String userContent = dto.getContent();
        LocalDateTime now = LocalDateTime.now();

        // 用户消息：Redis 可用则存 Redis，否则直接存 MySQL
        if (redisOk) {
            saveMessageToRedis(sessionId, 0, userContent, now);
        }
        saveMessageToMySQL(sessionId, 0, userContent, now);

        // 组装 AI 上下文
        SystemMessage systemMessage = buildSystemMessage(currentUserId, userContent);
        List<Message> aiMessages = buildContext(systemMessage, sessionId, userContent);

        // 流式调用 AI
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder fullResponse = new StringBuilder();

        ChatClient chatClient = ChatClient.builder(chatModel).build();
        chatClient.prompt()
                .messages(aiMessages)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    fullResponse.append(chunk);
                    sink.tryEmitNext(chunk);
                })
                .doOnComplete(() -> {
                    String aiContent = fullResponse.toString();
                    LocalDateTime aiTime = LocalDateTime.now();

                    // AI 回复：Redis + MySQL
                    if (redisOk) {
                        saveMessageToRedis(sessionId, 1, aiContent, aiTime);
                    }
                    saveMessageToMySQL(sessionId, 1, aiContent, aiTime);

                    updateSession(session, userContent);
                    sendProfileUpdateAsync(currentUserId, sessionId, userContent);
                    sink.tryEmitComplete();
                })
                .doOnError(e -> {
                    log.error("AI 流式调用失败 sessionId={}", sessionId, e);
                    String errorMsg = "[系统] AI回复生成失败，请重试";
                    LocalDateTime errorTime = LocalDateTime.now();

                    if (redisOk) {
                        saveMessageToRedis(sessionId, 1, errorMsg, errorTime);
                    }
                    saveMessageToMySQL(sessionId, 1, errorMsg, errorTime);

                    updateSession(session, userContent);
                    sink.tryEmitNext(errorMsg);
                    sink.tryEmitComplete();
                })
                .subscribe();

        return sink.asFlux();
    }

    @Override
    public List<ChatSessionVO> listSessions() {
        Long currentUserId = UserContext.getUserId();

        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, currentUserId)
                        .orderByDesc(ChatSession::getLastMessageAt)
        );
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        return sessions.stream().map(session -> {
            ChatSessionVO vo = new ChatSessionVO();
            vo.setId(session.getId());
            vo.setTitle(session.getTitle());
            vo.setLastMessageAt(session.getLastMessageAt());

            // 尝试 Redis，失败则 MySQL
            if (!tryFillFromRedis(session.getId(), vo)) {
                fillFromMySQL(session.getId(), vo);
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<ChatMessageVO> getMessages(Long sessionId) {
        Long currentUserId = UserContext.getUserId();
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException("会话不存在");
        }
        if (!session.getUserId().equals(currentUserId)) {
            throw new BizException("无权访问此会话");
        }

        // 尝试 Redis
        List<ChatMessageVO> fromRedis = tryGetMessagesFromRedis(sessionId);
        if (fromRedis != null) {
            return fromRedis;
        }

        // 回退 MySQL
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId)
        );
        return messages.stream().map(msg -> {
            ChatMessageVO vo = new ChatMessageVO();
            BeanUtil.copyProperties(msg, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void deleteSession(Long sessionId) {
        Long currentUserId = UserContext.getUserId();
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException("会话不存在");
        }
        if (!session.getUserId().equals(currentUserId)) {
            throw new BizException("无权操作此会话");
        }

        chatSessionMapper.deleteById(sessionId);
        chatMessageMapper.delete(
                new LambdaUpdateWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
        );

        // Redis 清理：失败忽略
        safeRedisOp(() -> stringRedisTemplate.delete(REDIS_MSG_KEY + sessionId));
    }

    // ======================== Redis（带降级） ========================

    /** 安全执行 Redis 操作，失败时标记 redisOk=false 并静默跳过 */
    private void safeRedisOp(Runnable op) {
        if (!redisOk) return;
        try {
            op.run();
        } catch (RedisConnectionFailureException e) {
            redisOk = false;
            log.warn("Redis 不可用，降级为纯 MySQL 模式");
        } catch (Exception e) {
            log.warn("Redis 操作异常，跳过: {}", e.getMessage());
        }
    }

    /** 安全执行带返回值的 Redis 操作，失败时返回 null */
    private <T> T safeRedisGet(java.util.function.Supplier<T> op) {
        if (!redisOk) return null;
        try {
            return op.get();
        } catch (RedisConnectionFailureException e) {
            redisOk = false;
            log.warn("Redis 不可用，降级为纯 MySQL 模式");
            return null;
        } catch (Exception e) {
            log.warn("Redis 操作异常: {}", e.getMessage());
            return null;
        }
    }

    /** 消息存入 Redis */
    private void saveMessageToRedis(Long sessionId, int role, String content, LocalDateTime time) {
        safeRedisOp(() -> {
            String key = REDIS_MSG_KEY + sessionId;
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", role);
            msg.put("content", content);
            msg.put("createTime", time.toString());
            stringRedisTemplate.opsForList().leftPush(key, JSONUtil.toJsonStr(msg));
            stringRedisTemplate.expire(key, Duration.ofHours(MSG_TTL_HOURS));
        });
    }

    /** 组装 AI 上下文：优先 Redis，回退 MySQL */
    private List<Message> buildContext(SystemMessage systemMsg, Long sessionId, String userContent) {
        int maxHistory = chatProperties.getMaxHistory();

        // 尝试 Redis
        List<Map<String, Object>> historyMsgs = safeRedisGet(() -> {
            String redisKey = REDIS_MSG_KEY + sessionId;
            List<String> jsonList = stringRedisTemplate.opsForList().range(redisKey, 0, maxHistory - 1);
            if (jsonList == null || jsonList.isEmpty()) {
                return Collections.<Map<String, Object>>emptyList();
            }
            Collections.reverse(jsonList);
            return jsonList.stream().map(json -> {
                try {
                    return JSONUtil.<Map<String, Object>>parseObj(json);
                } catch (Exception e) {
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        });

        // Redis 不可用，从 MySQL 加载
        if (historyMsgs == null) {
            historyMsgs = getHistoryFromMySQL(sessionId, maxHistory);
        }

        // 组装 Spring AI Message 列表
        List<Message> aiMessages = new ArrayList<>();
        aiMessages.add(systemMsg);
        for (Map<String, Object> map : historyMsgs) {
            int role = (Integer) map.get("role");
            String content = (String) map.get("content");
            if (role == 0) {
                aiMessages.add(new UserMessage(content));
            } else {
                aiMessages.add(new AssistantMessage(content));
            }
        }
        aiMessages.add(new UserMessage(userContent));
        return aiMessages;
    }

    /** 从 MySQL 加载历史消息（用于上下文组装） */
    private List<Map<String, Object>> getHistoryFromMySQL(Long sessionId, int maxHistory) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT " + maxHistory)
        );
        Collections.reverse(messages);
        return messages.stream().map(msg -> {
            Map<String, Object> map = new HashMap<>();
            map.put("role", msg.getRole());
            map.put("content", msg.getContent());
            return map;
        }).collect(Collectors.toList());
    }

    /** 直接存消息到 MySQL */
    private void saveMessageToMySQL(Long sessionId, int role, String content, LocalDateTime time) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreateTime(time);
        chatMessageMapper.insert(msg);
    }

    /** 尝试从 Redis 填充会话摘要信息，失败返回 false */
    private boolean tryFillFromRedis(Long sessionId, ChatSessionVO vo) {
        Long size = safeRedisGet(() -> stringRedisTemplate.opsForList().size(REDIS_MSG_KEY + sessionId));
        if (size == null || size == 0) {
            return false;
        }
        vo.setMessageCount(size.intValue());
        String lastJson = safeRedisGet(() -> stringRedisTemplate.opsForList().index(REDIS_MSG_KEY + sessionId, 0));
        if (StrUtil.isNotBlank(lastJson)) {
            try {
                Map<String, Object> map = JSONUtil.parseObj(lastJson);
                String content = (String) map.get("content");
                vo.setLastMessage(content.length() > 30
                        ? content.substring(0, 30) + "..."
                        : content);
            } catch (Exception ignored) {}
        }
        return true;
    }

    /** 尝试从 Redis 获取消息列表，失败返回 null */
    private List<ChatMessageVO> tryGetMessagesFromRedis(Long sessionId) {
        List<String> jsonList = safeRedisGet(() ->
                stringRedisTemplate.opsForList().range(REDIS_MSG_KEY + sessionId, 0, -1));
        if (jsonList == null || jsonList.isEmpty()) {
            return null;
        }
        Collections.reverse(jsonList);
        return jsonList.stream().map(json -> {
            Map<String, Object> map = JSONUtil.parseObj(json);
            ChatMessageVO vo = new ChatMessageVO();
            vo.setRole((Integer) map.get("role"));
            vo.setContent((String) map.get("content"));
            vo.setCreateTime(parseDateTime((String) map.get("createTime")));
            return vo;
        }).collect(Collectors.toList());
    }

    // ======================== 辅助方法 ========================

    private ChatSession getOrCreateSession(Long userId, Long sessionId) {
        if (sessionId != null) {
            ChatSession session = chatSessionMapper.selectById(sessionId);
            if (session == null) {
                throw new BizException("会话不存在");
            }
            if (!session.getUserId().equals(userId)) {
                throw new BizException("无权访问此会话");
            }
            return session;
        }
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle("新会话");
        session.setLastMessageAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return session;
    }

    private SystemMessage buildSystemMessage(Long userId, String userContent) {
        StringBuilder sb = new StringBuilder();
        sb.append(chatProperties.getSystemPrompt());

        ChatUserProfile profile = chatUserProfileMapper.selectOne(
                new LambdaQueryWrapper<ChatUserProfile>()
                        .eq(ChatUserProfile::getUserId, userId)
        );
        if (profile != null && StrUtil.isNotBlank(profile.getSummary())) {
            sb.append("\n\n【用户背景】").append(profile.getSummary());
        }

        String ragContext = searchRag(userContent);
        if (StrUtil.isNotBlank(ragContext)) {
            sb.append("\n\n【相关知识库】\n").append(ragContext);
            sb.append("\n请结合以上知识库内容回答用户问题。");
        }
        return new SystemMessage(sb.toString());
    }

    private String searchRag(String userContent) {
        List<String> keywords = extractKeywords(userContent);
        if (keywords.isEmpty()) return "";

        LambdaQueryWrapper<ChatRagDoc> wrapper = new LambdaQueryWrapper<ChatRagDoc>()
                .eq(ChatRagDoc::getEnabled, 1);
        wrapper.and(w -> {
            for (int i = 0; i < keywords.size(); i++) {
                if (i == 0) {
                    w.like(ChatRagDoc::getKeywords, keywords.get(i));
                } else {
                    w.or().like(ChatRagDoc::getKeywords, keywords.get(i));
                }
            }
        });
        wrapper.last("LIMIT " + chatProperties.getRagMatchCount());

        List<ChatRagDoc> docs = chatRagDocMapper.selectList(wrapper);
        if (docs.isEmpty()) return "";

        return docs.stream()
                .map(doc -> "【" + doc.getTitle() + "】" + doc.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    private List<String> extractKeywords(String content) {
        if (StrUtil.isBlank(content)) return Collections.emptyList();
        String cleaned = content.replaceAll("[，。！？、；：\"\"''（）\\[\\]【】\\s]+", " ");
        return Arrays.stream(cleaned.split(" "))
                .map(String::trim)
                .filter(s -> s.length() >= 2)
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    private void updateSession(ChatSession session, String userContent) {
        LambdaUpdateWrapper<ChatSession> wrapper = new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, session.getId())
                .set(ChatSession::getLastMessageAt, LocalDateTime.now());

        if (StrUtil.isBlank(session.getTitle()) || "新会话".equals(session.getTitle())) {
            String title = userContent.length() > 20
                    ? userContent.substring(0, 20) + "..."
                    : userContent;
            wrapper.set(ChatSession::getTitle, title);
        }
        chatSessionMapper.update(null, wrapper);
    }

    private void sendProfileUpdateAsync(Long userId, Long sessionId, String content) {
        CompletableFuture.runAsync(() -> {
            try {
                ChatMQMessage msg = new ChatMQMessage();
                msg.setMessageId(UUID.randomUUID().toString());
                msg.setUserId(userId);
                msg.setSessionId(sessionId);
                msg.setContent(content);
                msg.setCreateTime(LocalDateTime.now());
                rabbitTemplate.convertAndSend(
                        RabbitMQConstants.CHAT_EXCHANGE,
                        RabbitMQConstants.CHAT_PROFILE_ROUTING_KEY,
                        msg);
            } catch (Exception e) {
                log.error("发送画像更新消息失败 userId={}", userId, e);
            }
        });
    }

    private void fillFromMySQL(Long sessionId, ChatSessionVO vo) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId)
        );
        if (!messages.isEmpty()) {
            vo.setMessageCount(messages.size());
            String content = messages.get(0).getContent();
            vo.setLastMessage(content.length() > 30
                    ? content.substring(0, 30) + "..."
                    : content);
        } else {
            vo.setMessageCount(0);
            vo.setLastMessage("");
        }
    }

    private LocalDateTime parseDateTime(String timeStr) {
        if (StrUtil.isBlank(timeStr)) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(timeStr);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /** 设置响应头（SSE 流开始前调用），失败静默忽略 */
    private void setHeader(String name, String value) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletResponse response = attrs.getResponse();
                if (response != null) {
                    response.setHeader(name, value);
                }
            }
        } catch (Exception ignored) {}
    }
}

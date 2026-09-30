package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.RabbitMQConstants;
import com.xs.sheepaimall.core.CsMessageTableNameHandler;
import com.xs.sheepaimall.dto.CsMQMessage;
import com.xs.sheepaimall.dto.CsSendDTO;
import com.xs.sheepaimall.entity.CsMessage;
import com.xs.sheepaimall.entity.CsSession;
import com.xs.sheepaimall.mapper.CsMessageMapper;
import com.xs.sheepaimall.mapper.CsSessionMapper;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.CsMessageService;
import com.xs.sheepaimall.vo.CsMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 客服消息 Service — Redis优先 + MQ异步落库 */
@Slf4j
@Service
public class CsMessageServiceImpl implements CsMessageService {

    private static final String REDIS_PENDING_KEY = "cs:pending:";
    private static final long PENDING_TTL_HOURS = 168; // 7天

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CsSessionMapper csSessionMapper;

    @Autowired
    private CsMessageMapper csMessageMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public CsMessageVO sendMessage(CsSendDTO dto) {
        Long currentUserId = UserContext.getUserId();
        Long sessionId = getOrCreateSession(currentUserId, dto.getSessionId());
        LocalDateTime now = LocalDateTime.now();

        // 构建消息
        CsMessageVO vo = new CsMessageVO();
        vo.setSessionId(sessionId);
        vo.setSenderId(currentUserId);
        vo.setSenderRole(0); // 用户
        vo.setMsgType(StrUtil.isBlank(dto.getMsgType()) ? "text" : dto.getMsgType());
        vo.setContent(dto.getContent());
        vo.setExtra(dto.getExtra());
        vo.setCreateTime(now);

        // 1. 写 Redis（同步，毫秒级）
        String redisKey = REDIS_PENDING_KEY + sessionId;
        String msgJson = JSONUtil.toJsonStr(toMap(vo));
        safeRedisOp(() -> {
            stringRedisTemplate.opsForList().leftPush(redisKey, msgJson);
            stringRedisTemplate.expire(redisKey, Duration.ofHours(PENDING_TTL_HOURS));
        });

        // 2. 发送 MQ：异步落库
        sendMqMessage(sessionId, currentUserId, 0, vo.getMsgType(), dto.getContent(), dto.getExtra(), now, "persist");

        // 3. 发送 MQ：实时推送（通知接收方）
        sendMqMessage(sessionId, currentUserId, 0, vo.getMsgType(), dto.getContent(), dto.getExtra(), now, "push");

        // 4. 更新会话
        updateSession(sessionId, dto.getContent());

        return vo;
    }

    @Override
    public CsMessageVO sendAgentMessage(Long agentId, CsSendDTO dto) {
        Long sessionId = dto.getSessionId();
        if (sessionId == null) {
            throw new BizException("客服回复必须指定会话ID");
        }
        CsSession session = csSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException("会话不存在");
        }
        LocalDateTime now = LocalDateTime.now();

        CsMessageVO vo = new CsMessageVO();
        vo.setSessionId(sessionId);
        vo.setSenderId(agentId);
        vo.setSenderRole(1); // 客服
        vo.setMsgType(StrUtil.isBlank(dto.getMsgType()) ? "text" : dto.getMsgType());
        vo.setContent(dto.getContent());
        vo.setExtra(dto.getExtra());
        vo.setCreateTime(now);

        // 1. 写 Redis
        String redisKey = REDIS_PENDING_KEY + sessionId;
        String msgJson = JSONUtil.toJsonStr(toMap(vo));
        safeRedisOp(() -> {
            stringRedisTemplate.opsForList().leftPush(redisKey, msgJson);
            stringRedisTemplate.expire(redisKey, Duration.ofHours(PENDING_TTL_HOURS));
        });

        // 2. MQ 落库 + 推送
        sendMqMessage(sessionId, agentId, 1, vo.getMsgType(), dto.getContent(), dto.getExtra(), now, "persist");
        sendMqMessage(sessionId, agentId, 1, vo.getMsgType(), dto.getContent(), dto.getExtra(), now, "push");

        updateSession(sessionId, dto.getContent());
        return vo;
    }

    @Override
    public List<CsMessageVO> getMessages(Long sessionId) {
        // 优先 Redis
        List<CsMessageVO> fromRedis = getFromRedis(sessionId);
        if (!fromRedis.isEmpty()) {
            return fromRedis;
        }
        // 回退 MySQL
        CsMessageTableNameHandler.setMonth(LocalDateTime.now());
        try {
            List<CsMessage> messages = csMessageMapper.selectList(
                    new LambdaQueryWrapper<CsMessage>()
                            .eq(CsMessage::getSessionId, sessionId)
                            .orderByAsc(CsMessage::getCreateTime)
            );
            return messages.stream().map(m -> {
                CsMessageVO vo = new CsMessageVO();
                BeanUtil.copyProperties(m, vo);
                return vo;
            }).collect(Collectors.toList());
        } finally {
            CsMessageTableNameHandler.clearMonth();
        }
    }

    @Override
    public List<CsMessageVO> pollNewMessages(Long sessionId, Long lastMsgId) {
        // 从 Redis 读取比 lastMsgId 新的消息
        String redisKey = REDIS_PENDING_KEY + sessionId;
        List<String> jsonList = safeRedisGet(() ->
                stringRedisTemplate.opsForList().range(redisKey, 0, -1));
        if (jsonList == null || jsonList.isEmpty()) {
            return Collections.emptyList();
        }
        List<CsMessageVO> result = new ArrayList<>();
        for (String json : jsonList) {
            CsMessageVO vo = parseJson(json);
            if (vo != null && (lastMsgId == null || vo.getId() == null || vo.getId() > lastMsgId)) {
                result.add(vo);
            }
        }
        Collections.reverse(result);
        return result;
    }

    // ======================== 私有方法 ========================

    private Long getOrCreateSession(Long userId, Long sessionId) {
        if (sessionId != null) {
            CsSession session = csSessionMapper.selectById(sessionId);
            if (session == null) throw new BizException("会话不存在");
            if (!session.getUserId().equals(userId)) throw new BizException("无权访问");
            return sessionId;
        }
        CsSession session = new CsSession();
        session.setUserId(userId);
        session.setType(1); // 人工
        session.setStatus(0);
        session.setTitle("新会话");
        session.setLastMessageAt(LocalDateTime.now());
        csSessionMapper.insert(session);
        return session.getId();
    }

    private void updateSession(Long sessionId, String content) {
        CsSession session = csSessionMapper.selectById(sessionId);
        if (session == null) return;
        session.setLastMessageAt(LocalDateTime.now());
        if (StrUtil.isBlank(session.getTitle()) || "新会话".equals(session.getTitle())) {
            session.setTitle(content.length() > 20 ? content.substring(0, 20) + "..." : content);
        }
        csSessionMapper.updateById(session);
    }

    private void sendMqMessage(Long sessionId, Long senderId, int senderRole,
                               String msgType, String content, String extra,
                               LocalDateTime time, String purpose) {
        try {
            CsMQMessage msg = new CsMQMessage();
            msg.setMessageId(UUID.randomUUID().toString());
            msg.setSessionId(sessionId);
            msg.setSenderId(senderId);
            msg.setSenderRole(senderRole);
            msg.setMsgType(msgType);
            msg.setContent(content);
            msg.setExtra(extra);
            msg.setCreateTime(time);
            msg.setPurpose(purpose);

            String routingKey = "persist".equals(purpose)
                    ? RabbitMQConstants.CS_MESSAGE_PERSIST_KEY
                    : RabbitMQConstants.CS_PUSH_KEY;

            rabbitTemplate.convertAndSend(RabbitMQConstants.CS_EXCHANGE, routingKey, msg);
        } catch (Exception e) {
            log.error("发送MQ消息失败 sessionId={} purpose={}", sessionId, purpose, e);
        }
    }

    private Map<String, Object> toMap(CsMessageVO vo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", vo.getId());
        map.put("sessionId", vo.getSessionId());
        map.put("senderId", vo.getSenderId());
        map.put("senderRole", vo.getSenderRole());
        map.put("msgType", vo.getMsgType());
        map.put("content", vo.getContent());
        map.put("extra", vo.getExtra());
        map.put("createTime", vo.getCreateTime().toString());
        return map;
    }

    private List<CsMessageVO> getFromRedis(Long sessionId) {
        List<String> jsonList = safeRedisGet(() ->
                stringRedisTemplate.opsForList().range(REDIS_PENDING_KEY + sessionId, 0, -1));
        if (jsonList == null || jsonList.isEmpty()) return Collections.emptyList();
        Collections.reverse(jsonList);
        return jsonList.stream().map(this::parseJson).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private CsMessageVO parseJson(String json) {
        try {
            Map<String, Object> map = JSONUtil.parseObj(json);
            CsMessageVO vo = new CsMessageVO();
            vo.setId(map.get("id") != null ? Long.valueOf(map.get("id").toString()) : null);
            vo.setSessionId(Long.valueOf(map.get("sessionId").toString()));
            vo.setSenderId(Long.valueOf(map.get("senderId").toString()));
            vo.setSenderRole((Integer) map.get("senderRole"));
            vo.setMsgType((String) map.get("msgType"));
            vo.setContent((String) map.get("content"));
            vo.setExtra((String) map.get("extra"));
            vo.setCreateTime(LocalDateTime.parse((String) map.get("createTime")));
            return vo;
        } catch (Exception e) {
            return null;
        }
    }

    private void safeRedisOp(Runnable op) {
        try {
            op.run();
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis不可用，跳过缓存写入");
        } catch (Exception e) {
            log.warn("Redis操作异常: {}", e.getMessage());
        }
    }

    private <T> T safeRedisGet(java.util.function.Supplier<T> op) {
        try {
            return op.get();
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis不可用，回退MySQL");
            return null;
        } catch (Exception e) {
            log.warn("Redis操作异常: {}", e.getMessage());
            return null;
        }
    }
}

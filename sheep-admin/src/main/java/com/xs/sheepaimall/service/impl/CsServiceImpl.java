package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.entity.ChatMessage;
import com.xs.sheepaimall.entity.ChatSession;
import com.xs.sheepaimall.mapper.ChatMessageMapper;
import com.xs.sheepaimall.mapper.ChatSessionMapper;
import com.xs.sheepaimall.service.CsService;
import com.xs.sheepaimall.vo.ChatMessageVO;
import com.xs.sheepaimall.vo.CsSessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 人工坐席服务实现（设计 D4/D5）。
 * <p>未读数按"最后一条 role!=0（人工/AI/系统）消息之后"的 role=0 消息条数计算（D5），
 * 用两条查询实现：取最后一条非用户消息ID + 统计其后的用户消息数。</p>
 */
@Slf4j
@Service
public class CsServiceImpl implements CsService {

    /** 会话列表消息摘要最大长度（对齐 ChatServiceImpl 的 30 字截断） */
    private static final int LAST_MESSAGE_LIMIT = 30;
    /** 审计明细最大长度（对齐 AgentChatService 的 200 字摘要） */
    private static final int AUDIT_DETAIL_LIMIT = 200;
    /** 会话默认标题（新会话未命名标记，与 ChatServiceImpl 一致） */
    private static final String DEFAULT_TITLE = "新会话";
    /** 首条消息命名的标题最大长度（对齐 ChatServiceImpl.updateSession 的 20 字） */
    private static final int TITLE_LIMIT = 20;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionStateService chatSessionStateService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /** 消息 Redis 缓存键前缀与 TTL，必须与 ChatServiceImpl 保持一致（双写同源） */
    private static final String REDIS_MSG_KEY = "chat:msg:";
    private static final long MSG_TTL_HOURS = 168;

    @Override
    public List<CsSessionVO> listSessions(Long scope) {
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(scope != null, ChatSession::getMerchantId, scope)
                        .isNull(scope == null, ChatSession::getMerchantId)
                        .orderByDesc(ChatSession::getLastMessageAt)
        );
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }
        return sessions.stream().map(this::toCsSessionVO).collect(Collectors.toList());
    }

    @Override
    public List<ChatMessageVO> getMessages(Long scope, Long sessionId) {
        requireSessionInScope(scope, sessionId);
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
    public void reply(Long scope, Long sessionId, String content, Long operatorId) {
        ChatSession session = requireSessionInScope(scope, sessionId);

        // AI 态拒绝人工回复：避免坐席误操作把消息推给正在与智能客服对话的用户（设计 D1 状态机约束）
        if (session.getServiceMode() == null
                || session.getServiceMode() != ChatSessionStateService.MODE_HUMAN) {
            throw new BizException("当前会话为智能客服接待中，无法人工回复，请先转接人工");
        }

        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        // 商家坐席 role=2，平台坐席 role=3（设计 D1）
        msg.setRole(scope == null ? 3 : 2);
        msg.setContent(content);
        msg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(msg);

        // Redis 消息缓存双写（与 ChatServiceImpl.saveMessageToRedis 同格式：leftPush 最新在前）。
        // 用户侧 getMessages 优先读 Redis，缺失会导致坐席回复在缓存 TTL（7天）内对用户不可见
        try {
            Map<String, Object> cacheMsg = new HashMap<>();
            cacheMsg.put("role", msg.getRole());
            cacheMsg.put("content", content);
            cacheMsg.put("createTime", msg.getCreateTime().toString());
            stringRedisTemplate.opsForList().leftPush(REDIS_MSG_KEY + sessionId, cn.hutool.json.JSONUtil.toJsonStr(cacheMsg));
            stringRedisTemplate.expire(REDIS_MSG_KEY + sessionId, java.time.Duration.ofHours(MSG_TTL_HOURS));
        } catch (Exception e) {
            log.warn("坐席回复写 Redis 缓存失败（MySQL 已落库，不影响数据）sessionId={}", sessionId, e);
        }

        // 刷新会话时间；title 仍为默认名时用首条内容前20字命名（对齐 ChatServiceImpl.updateSession）
        LambdaUpdateWrapper<ChatSession> wrapper = new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .set(ChatSession::getLastMessageAt, LocalDateTime.now());
        if (StrUtil.isBlank(session.getTitle()) || DEFAULT_TITLE.equals(session.getTitle())) {
            String title = content.length() > TITLE_LIMIT
                    ? content.substring(0, TITLE_LIMIT) + "..."
                    : content;
            wrapper.set(ChatSession::getTitle, title);
        }
        chatSessionMapper.update(null, wrapper);

        String detail = content.length() > AUDIT_DETAIL_LIMIT
                ? content.substring(0, AUDIT_DETAIL_LIMIT) : content;
        auditService.record(scope == null ? AuditService.ACTOR_ADMIN : AuditService.ACTOR_MERCHANT,
                operatorId, sessionId, session.getMerchantId(),
                AuditService.ACTION_HUMAN_REPLY, "人工回复: " + detail);
    }

    @Override
    public void takeover(Long scope, Long sessionId, Long operatorId) {
        chatSessionStateService.takeover(scope, sessionId, operatorId);
    }

    @Override
    public void backToAi(Long scope, Long sessionId, Long operatorId) {
        chatSessionStateService.backToAi(scope, sessionId, operatorId);
    }

    @Override
    public void transferToHuman(Long scope, Long sessionId, Long operatorId) {
        chatSessionStateService.transferByAgent(scope, sessionId, operatorId);
    }

    // ======================== 辅助方法 ========================

    /** 会话实体转坐席列表 VO：最后一条消息摘要 + 未读数（两条查询实现，D5） */
    private CsSessionVO toCsSessionVO(ChatSession session) {
        CsSessionVO vo = new CsSessionVO();
        vo.setSessionId(session.getId());
        vo.setUserId(session.getUserId());
        vo.setTitle(session.getTitle());
        vo.setServiceMode(session.getServiceMode());
        vo.setTransferTime(session.getTransferTime());
        vo.setLastMessageAt(session.getLastMessageAt());

        // 最后一条消息（任意角色）摘要
        ChatMessage lastMsg = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT 1")
        );
        if (lastMsg != null && StrUtil.isNotBlank(lastMsg.getContent())) {
            vo.setLastMessage(lastMsg.getContent().length() > LAST_MESSAGE_LIMIT
                    ? lastMsg.getContent().substring(0, LAST_MESSAGE_LIMIT) + "..."
                    : lastMsg.getContent());
        } else {
            vo.setLastMessage("");
        }

        // 未读数（D5）：id 大于最后一条 role!=0 消息的 role=0 消息条数
        ChatMessage lastHumanMsg = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .ne(ChatMessage::getRole, 0)
                        .select(ChatMessage::getId)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT 1")
        );
        long lastHumanId = lastHumanMsg != null ? lastHumanMsg.getId() : 0L;
        Long unread = chatMessageMapper.selectCount(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .eq(ChatMessage::getRole, 0)
                        .gt(ChatMessage::getId, lastHumanId)
        );
        vo.setUnreadCount(unread == null ? 0 : unread.intValue());
        return vo;
    }

    /**
     * 范围校验（D4）：商家 scope 要求 session.merchantId 等于 scope；
     * 平台 scope（null）要求 session.merchantId 为 null。不匹配与不存在返回同一文案，避免存在性泄露。
     */
    private ChatSession requireSessionInScope(Long scope, Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException("会话不存在或无权访问");
        }
        boolean match = scope == null
                ? session.getMerchantId() == null
                : scope.equals(session.getMerchantId());
        if (!match) {
            throw new BizException("会话不存在或无权访问");
        }
        return session;
    }
}

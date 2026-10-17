package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.config.ChatProperties;
import com.xs.sheepaimall.entity.ChatSession;
import com.xs.sheepaimall.mapper.ChatSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 客服会话状态机（设计 D1/D7）：
 * <pre>
 *   AI服务:    service_mode=0
 *   待接入:    service_mode=1 且坐席尚未回复过
 *   人工服务中: service_mode=1 且坐席已回复过（或显式接入）
 * </pre>
 * 待接入与人工服务中不加列区分，用"坐席是否已回复过"推断（坐席回复动作在第二阶段 CsService 中落 role=2/3 消息）。
 * 待接入超时（transferTimeoutMinutes）不建定时任务：用户下次发消息时懒检查自动转回 AI（D7）。
 * 每个状态动作均写审计。
 */
@Slf4j
@Service
public class ChatSessionStateService {

    /** 服务模式：AI 服务 */
    public static final int MODE_AI = 0;
    /** 服务模式：人工服务（待接入/人工服务中） */
    public static final int MODE_HUMAN = 1;

    /** 懒检查自动转回时给用户看的系统提示 */
    public static final String TIP_TIMEOUT_BACK_TO_AI = "人工客服暂未接入，已为您转回智能客服";

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatProperties chatProperties;

    @Autowired
    private AuditService auditService;

    /**
     * 发起转人工
     *
     * @param sessionId  会话ID
     * @param operatorId 操作者ID
     * @param actorType  操作者类型（AuditService.ACTOR_*）：用户转接 USER，商家坐席 MERCHANT，平台 ADMIN
     */
    public void transfer(Long sessionId, Long operatorId, String actorType) {
        ChatSession session = requireSession(sessionId);
        if (AuditService.ACTOR_USER.equals(actorType) && !session.getUserId().equals(operatorId)) {
            throw new BizException("无权操作此会话");
        }
        // 幂等守卫：已是人工态则跳过状态变更（transfer_time 保留首次发起时间作为超时懒检查基准），仍记审计
        boolean alreadyHuman = session.getServiceMode() != null && session.getServiceMode() == MODE_HUMAN;
        if (!alreadyHuman) {
            chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                    .eq(ChatSession::getId, sessionId)
                    .set(ChatSession::getServiceMode, MODE_HUMAN)
                    .set(ChatSession::getTransferTime, LocalDateTime.now()));
        }
        auditService.record(actorType, operatorId, sessionId, session.getMerchantId(),
                AuditService.ACTION_TRANSFER_REQUEST,
                alreadyHuman ? "转接人工客服（重复发起，幂等跳过）" : "转接人工客服");
        log.info("会话转接人工 sessionId={}, actorType={}, operatorId={}", sessionId, actorType, operatorId);
    }

    /** 坐席接入（第二阶段 CsService 调用）：仅校验范围并写审计，接入效果由坐席首条人工消息自然体现 */
    public void takeover(Long scope, Long sessionId, Long operatorId) {
        ChatSession session = requireSessionInScope(scope, sessionId);
        auditService.record(scope == null ? AuditService.ACTOR_ADMIN : AuditService.ACTOR_MERCHANT,
                operatorId, sessionId, session.getMerchantId(),
                AuditService.ACTION_TAKEOVER, "坐席接入会话");
    }

    /** 坐席手动转人工：AI 态会话经坐席确认后切入人工态（幂等），写审计 */
    public void transferByAgent(Long scope, Long sessionId, Long operatorId) {
        ChatSession session = requireSessionInScope(scope, sessionId);
        boolean alreadyHuman = session.getServiceMode() != null && session.getServiceMode() == MODE_HUMAN;
        if (!alreadyHuman) {
            chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                    .eq(ChatSession::getId, sessionId)
                    .set(ChatSession::getServiceMode, MODE_HUMAN)
                    .set(ChatSession::getTransferTime, LocalDateTime.now()));
        }
        auditService.record(scope == null ? AuditService.ACTOR_ADMIN : AuditService.ACTOR_MERCHANT,
                operatorId, sessionId, session.getMerchantId(),
                AuditService.ACTION_TRANSFER_REQUEST,
                alreadyHuman ? "坐席转人工（重复发起，幂等跳过）" : "坐席转人工");
        log.info("坐席转人工 sessionId={}, scope={}, operatorId={}", sessionId, scope, operatorId);
    }

    /** 坐席转回 AI：清空人工态，写审计 */
    public void backToAi(Long scope, Long sessionId, Long operatorId) {
        ChatSession session = requireSessionInScope(scope, sessionId);
        chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .set(ChatSession::getServiceMode, MODE_AI)
                .set(ChatSession::getTransferTime, null));
        auditService.record(scope == null ? AuditService.ACTOR_ADMIN : AuditService.ACTOR_MERCHANT,
                operatorId, sessionId, session.getMerchantId(),
                AuditService.ACTION_BACK_TO_AI, "坐席转回AI客服");
    }

    /**
     * 用户发消息前的懒超时检查（D7）：人工态且转接发起时间超过配置时长 → 自动转回 AI + 审计。
     * 平台默认人工会话不参与超时转回（spec：平台人工态 SHALL NOT 自动转回 AI），仅商家会话的转接适用。
     *
     * @param session 当前会话（DB 实查对象）
     * @return 超时转回时返回用户可见的系统提示文案，未超时返回 null
     */
    public String resolveForUserMessage(ChatSession session) {
        if (session.getServiceMode() == null || session.getServiceMode() != MODE_HUMAN
                || session.getTransferTime() == null
                || session.getMerchantId() == null) {
            return null;
        }
        LocalDateTime deadline = session.getTransferTime()
                .plusMinutes(chatProperties.getTransferTimeoutMinutes());
        if (deadline.isAfter(LocalDateTime.now())) {
            return null;
        }
        chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, session.getId())
                .set(ChatSession::getServiceMode, MODE_AI)
                .set(ChatSession::getTransferTime, null));
        session.setServiceMode(MODE_AI);
        session.setTransferTime(null);
        auditService.record(AuditService.ACTOR_SYSTEM, 0L, session.getId(), session.getMerchantId(),
                AuditService.ACTION_BACK_TO_AI, "转接超时自动转回AI");
        log.info("会话转接超时自动转回AI sessionId={}", session.getId());
        return TIP_TIMEOUT_BACK_TO_AI;
    }

    /** 校验会话存在，不存在抛业务异常 */
    private ChatSession requireSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException("会话不存在");
        }
        return session;
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

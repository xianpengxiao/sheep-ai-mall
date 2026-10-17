package com.xs.sheepaimall.service.impl;

import com.xs.sheepaimall.entity.ChatAuditLog;
import com.xs.sheepaimall.mapper.ChatAuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 客服审计服务 —— 异步写 chat_audit_log，失败仅 log.error 不抛。
 * <p>注意：工具调用埋点发生在 Reactor 线程（UserContext 不可见），调用方必须显式传 actorId，
 * 本服务内部绝不依赖 UserContext。项目未开启 @EnableAsync，异步用 CompletableFuture（对齐既有写法）。</p>
 */
@Slf4j
@Service
public class AuditService {

    /** 动作：转接请求 */
    public static final String ACTION_TRANSFER_REQUEST = "TRANSFER_REQUEST";
    /** 动作：坐席接入 */
    public static final String ACTION_TAKEOVER = "TAKEOVER";
    /** 动作：转回 AI（含超时自动转回，detail 注明） */
    public static final String ACTION_BACK_TO_AI = "BACK_TO_AI";
    /** 动作：人工回复（第二阶段坐席接口使用） */
    public static final String ACTION_HUMAN_REPLY = "HUMAN_REPLY";
    /** 动作：AI 工具调用 */
    public static final String ACTION_TOOL_CALL = "TOOL_CALL";

    /** 操作者类型 */
    public static final String ACTOR_USER = "USER";
    public static final String ACTOR_MERCHANT = "MERCHANT";
    public static final String ACTOR_ADMIN = "ADMIN";
    public static final String ACTOR_SYSTEM = "SYSTEM";

    @Autowired
    private ChatAuditLogMapper chatAuditLogMapper;

    /** 异步记录一条审计日志，失败仅告警（审计非资金台账，丢失窗口可接受） */
    public void record(String actorType, Long actorId, Long sessionId, Long merchantId, String action, String detail) {
        CompletableFuture.runAsync(() -> doRecord(actorType, actorId, sessionId, merchantId, action, detail));
    }

    private void doRecord(String actorType, Long actorId, Long sessionId, Long merchantId, String action, String detail) {
        try {
            ChatAuditLog auditLog = new ChatAuditLog();
            auditLog.setActorType(actorType);
            auditLog.setActorId(actorId);
            auditLog.setSessionId(sessionId);
            auditLog.setMerchantId(merchantId);
            auditLog.setAction(action);
            auditLog.setDetail(detail);
            chatAuditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("审计日志写入失败 sessionId={}, action={}", sessionId, action, e);
        }
    }
}

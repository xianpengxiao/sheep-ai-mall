package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.CsReplyDTO;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.CsService;
import com.xs.sheepaimall.vo.ChatMessageVO;
import com.xs.sheepaimall.vo.CsSessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 平台人工坐席控制台（设计 D4）—— 仅 ROLE_ADMIN 可访问（角色判断惯例与
 * KnowledgeBaseServiceImpl.isAdmin 一致：roles.contains("ROLE_ADMIN")），
 * scope=null 只看平台会话（chat_session.merchant_id 为 null）；回复写 role=3 消息。
 */
@Tag(name = "平台管理-人工客服", description = "平台坐席控制台：会话列表、消息历史、人工回复、接入、转回AI")
@Validated
@RestController
@RequestMapping("/api/admin/cs")
public class AdminCsController {

    /** 管理员角色编码（与 KnowledgeBaseServiceImpl.ADMIN_ROLE_CODE 一致） */
    private static final String ADMIN_ROLE_CODE = "ROLE_ADMIN";

    @Autowired
    private CsService csService;

    @Autowired
    private com.xs.sheepaimall.service.impl.CsAgentConfigService csAgentConfigService;

    @Operation(summary = "坐席会话列表", description = "平台全部客服会话（merchantId 为 null），按最后消息时间倒序，含未读数")
    @GetMapping("/sessions")
    public R<List<CsSessionVO>> listSessions() {
        requireAdmin();
        return R.ok(csService.listSessions(null));
    }

    @Operation(summary = "会话消息历史", description = "指定会话的全部消息，按时间正序；非平台会话返回统一文案")
    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<ChatMessageVO>> getMessages(
            @Parameter(description = "会话ID") @PathVariable Long sessionId) {
        requireAdmin();
        return R.ok(csService.getMessages(null, sessionId));
    }

    @Operation(summary = "人工回复", description = "坐席回复用户：落 role=3 消息、刷新会话时间、写审计 HUMAN_REPLY")
    @PostMapping("/sessions/{sessionId}/reply")
    public R<Void> reply(
            @Parameter(description = "会话ID") @PathVariable Long sessionId,
            @Valid @RequestBody CsReplyDTO dto) {
        requireAdmin();
        csService.reply(null, sessionId, dto.getContent(), UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "坐席接入", description = "显式标记接入会话（写审计 TAKEOVER），接入效果由首条人工消息自然体现")
    @PutMapping("/sessions/{sessionId}/takeover")
    public R<Void> takeover(
            @Parameter(description = "会话ID") @PathVariable Long sessionId) {
        requireAdmin();
        csService.takeover(null, sessionId, UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "转回AI客服", description = "清空人工态回到 AI 服务（写审计 BACK_TO_AI）")
    @PutMapping("/sessions/{sessionId}/to-ai")
    public R<Void> backToAi(
            @Parameter(description = "会话ID") @PathVariable Long sessionId) {
        requireAdmin();
        csService.backToAi(null, sessionId, UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "AI自动接待总开关", description = "关闭时全部会话按人工处理（商家/平台各自开关被覆盖但状态保留）；开启时按各自开关执行")
    @PutMapping("/ai-enabled")
    public R<Void> aiEnabled(
            @Parameter(description = "1=开启 0=关闭") @RequestParam Integer enabled) {
        requireAdmin();
        csAgentConfigService.updateAiEnabled(enabled);
        return R.ok();
    }

    @Operation(summary = "平台会话AI接待开关", description = "控制官方客服新会话默认AI/人工；总开关关闭时本开关不生效；单会话仍可由坐席手动转接")
    @PutMapping("/platform-ai-enabled")
    public R<Void> platformAiEnabled(
            @Parameter(description = "1=官方客服新会话AI接待 0=默认人工") @RequestParam Integer enabled) {
        requireAdmin();
        csAgentConfigService.updatePlatformAiEnabled(enabled);
        return R.ok();
    }

    @Operation(summary = "客服全局配置", description = "返回总开关、平台AI开关与额度等全局配置（单行）")
    @GetMapping("/config")
    public R<com.xs.sheepaimall.entity.CsAgentConfig> config() {
        requireAdmin();
        return R.ok(csAgentConfigService.getConfig());
    }

    @Operation(summary = "转人工客服", description = "坐席将 AI 态会话切入人工态（写审计 TRANSFER_REQUEST），转接后可人工回复")
    @PutMapping("/sessions/{sessionId}/to-human")
    public R<Void> transferToHuman(
            @Parameter(description = "会话ID") @PathVariable Long sessionId) {
        requireAdmin();
        csService.transferToHuman(null, sessionId, UserContext.getUserId());
        return R.ok();
    }

    /** 入口角色校验：roles 含 ROLE_ADMIN，否则抛业务异常（惯例同 KnowledgeBaseServiceImpl.isAdmin） */
    private void requireAdmin() {
        List<String> roles = UserContext.getRoles();
        if (roles == null || !roles.contains(ADMIN_ROLE_CODE)) {
            throw new BizException("无权限");
        }
    }
}

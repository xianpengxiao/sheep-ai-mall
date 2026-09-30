package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.config.CsProperties;
import com.xs.sheepaimall.core.CsPollHolder;
import com.xs.sheepaimall.dto.CsSendDTO;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.CsMessageService;
import com.xs.sheepaimall.service.CsQueueService;
import com.xs.sheepaimall.service.CsSessionService;
import com.xs.sheepaimall.vo.CsMessageVO;
import com.xs.sheepaimall.vo.CsSessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

/** 客服端接口 */
@Tag(name = "客服工作台", description = "客服端：上线/下线、回复消息、查看会话、排队管理")
@Validated
@RestController
@RequestMapping("/api/cs/agent")
public class CsAgentController {

    @Autowired
    private CsMessageService csMessageService;

    @Autowired
    private CsSessionService csSessionService;

    @Autowired
    private CsQueueService csQueueService;

    @Autowired
    private CsPollHolder csPollHolder;

    @Autowired
    private CsProperties csProperties;

    @Operation(summary = "客服上线")
    @PostMapping("/login")
    public R<Void> login() {
        csQueueService.agentOnline(UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "客服下线")
    @PostMapping("/logout")
    public R<Void> logout() {
        csQueueService.agentOffline(UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "发送消息")
    @PostMapping("/send")
    public R<CsMessageVO> send(@Valid @RequestBody CsSendDTO dto) {
        return R.ok(csMessageService.sendAgentMessage(UserContext.getUserId(), dto));
    }

    @Operation(summary = "长轮询拉取新消息")
    @GetMapping("/poll")
    public DeferredResult<List<CsMessageVO>> poll(
            @Parameter(description = "会话ID") @RequestParam Long sessionId,
            @Parameter(description = "客户端最新消息ID") @RequestParam(required = false) Long lastMsgId) {
        List<CsMessageVO> newMsgs = csMessageService.pollNewMessages(sessionId, lastMsgId);
        if (!newMsgs.isEmpty()) {
            DeferredResult<List<CsMessageVO>> immediate = new DeferredResult<>();
            immediate.setResult(newMsgs);
            return immediate;
        }
        return csPollHolder.register(sessionId, csProperties.getPollTimeout());
    }

    @Operation(summary = "我的进行中会话")
    @GetMapping("/sessions")
    public R<List<CsSessionVO>> listSessions() {
        return R.ok(csSessionService.listAgentSessions(UserContext.getUserId()));
    }

    @Operation(summary = "会话消息历史")
    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<CsMessageVO>> getMessages(@PathVariable Long sessionId) {
        return R.ok(csMessageService.getMessages(sessionId));
    }

    @Operation(summary = "查看排队列表")
    @GetMapping("/queue")
    public R<String> queueList() {
        return R.ok("排队列表（需扩展Service返回完整列表）");
    }

    @Operation(summary = "手动接入排队用户")
    @PostMapping("/queue/assign/{sessionId}")
    public R<Void> assignQueue(@PathVariable Long sessionId) {
        csQueueService.assignAgent(sessionId);
        return R.ok();
    }

    @Operation(summary = "结束会话")
    @PostMapping("/sessions/{sessionId}/close")
    public R<Void> closeSession(@PathVariable Long sessionId) {
        csSessionService.closeSession(sessionId, UserContext.getUserId());
        return R.ok();
    }
}

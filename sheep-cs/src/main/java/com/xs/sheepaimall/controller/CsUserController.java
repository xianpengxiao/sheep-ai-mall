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
import com.xs.sheepaimall.vo.CsQueueStatusVO;
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

/** 用户端客服接口 */
@Tag(name = "用户客服", description = "用户端人工客服：发送消息、长轮询、排队")
@Validated
@RestController
@RequestMapping("/api/cs/user")
public class CsUserController {

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

    @Operation(summary = "发送消息")
    @PostMapping("/send")
    public R<CsMessageVO> send(@Valid @RequestBody CsSendDTO dto) {
        return R.ok(csMessageService.sendMessage(dto));
    }

    @Operation(summary = "长轮询拉取新消息", description = "挂起等待最多30秒，有新消息立即返回，超时返回空列表")
    @GetMapping("/poll")
    public DeferredResult<List<CsMessageVO>> poll(
            @Parameter(description = "会话ID") @RequestParam Long sessionId,
            @Parameter(description = "客户端最新消息ID") @RequestParam(required = false) Long lastMsgId) {
        // 先查 Redis 是否有新消息
        List<CsMessageVO> newMsgs = csMessageService.pollNewMessages(sessionId, lastMsgId);
        if (!newMsgs.isEmpty()) {
            DeferredResult<List<CsMessageVO>> immediate = new DeferredResult<>();
            immediate.setResult(newMsgs);
            return immediate;
        }
        // 挂起等待
        return csPollHolder.register(sessionId, csProperties.getPollTimeout());
    }

    @Operation(summary = "会话列表")
    @GetMapping("/sessions")
    public R<List<CsSessionVO>> listSessions() {
        return R.ok(csSessionService.listUserSessions(UserContext.getUserId()));
    }

    @Operation(summary = "消息历史")
    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<CsMessageVO>> getMessages(@PathVariable Long sessionId) {
        return R.ok(csMessageService.getMessages(sessionId));
    }

    @Operation(summary = "手动转人工")
    @PostMapping("/transfer")
    public R<CsQueueStatusVO> transfer(@RequestParam Long sessionId) {
        return R.ok(csQueueService.enqueue(sessionId, UserContext.getUserId()));
    }

    @Operation(summary = "查询排队状态")
    @GetMapping("/queue-status")
    public R<CsQueueStatusVO> queueStatus(@RequestParam Long sessionId) {
        return R.ok(csQueueService.getQueueStatus(sessionId));
    }

    @Operation(summary = "取消排队")
    @PostMapping("/queue-cancel")
    public R<Void> cancelQueue(@RequestParam Long sessionId) {
        csQueueService.cancelQueue(sessionId, UserContext.getUserId());
        return R.ok();
    }
}

package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.ChatAddCartOpDTO;
import com.xs.sheepaimall.dto.ChatSendDTO;
import com.xs.sheepaimall.dto.ChatTransferDTO;
import com.xs.sheepaimall.dto.ConfirmDTO;
import com.xs.sheepaimall.entity.ChatPendingOp;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.ChatService;
import com.xs.sheepaimall.service.PendingOperationService;
import com.xs.sheepaimall.vo.ChatMessageVO;
import com.xs.sheepaimall.vo.ChatSessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/** 智能客服接口 */
@Tag(name = "智能客服", description = "基于 DeepSeek + RAG 的智能客服对话")
@Validated
@RestController
@RequestMapping("/api/ai/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private PendingOperationService pendingOperationService;

    @Autowired
    private com.xs.sheepaimall.service.impl.ChatSessionStateService chatSessionStateService;

    @Operation(summary = "转人工", description = "用户主动发起转人工（按钮通路），仅可操作自己的会话")
    @PostMapping("/transfer")
    public R<Void> transfer(@Valid @RequestBody ChatTransferDTO dto) {
        chatSessionStateService.transfer(dto.getSessionId(), UserContext.getUserId(),
                com.xs.sheepaimall.service.impl.AuditService.ACTOR_USER);
        return R.ok();
    }

    @Operation(summary = "发送消息（流式）", description = "发送消息给AI客服，SSE流式返回回复。sessionId为空则自动创建新会话；新建会话可传 merchantId 进入商家专属客服（商家须已开通）。")
    @PostMapping(value = "/send", produces = "text/html;charset=utf-8")
    public Flux<String> send(@Valid @RequestBody ChatSendDTO dto) {
        return chatService.sendMessage(dto);
    }

    @Operation(summary = "确认待执行操作", description = "对AI客服生成的写操作（改地址/取消订单/加购物车）进行人工确认。仅携带 operationId + approve，参数以服务端快照为准。")
    @PostMapping("/confirm")
    public R<Void> confirm(@Valid @RequestBody ConfirmDTO dto) {
        pendingOperationService.confirm(dto.getOperationId(), dto.getApprove(), UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "商品卡片加购操作单", description = "商品卡片的\"加入购物车\"按钮调用：仅生成待确认操作单（走确认闸），用户确认后执行；skuId 必须为在售规格")
    @PostMapping("/add-cart-op")
    public R<Map<String, String>> addCartOp(@Valid @RequestBody ChatAddCartOpDTO dto) {
        ChatPendingOp op = chatService.createAddCartOp(dto.getSkuId(), dto.getQuantity(), dto.getSessionId());
        Map<String, String> marker = new java.util.LinkedHashMap<>();
        marker.put("operationId", op.getId());
        marker.put("displayText", op.getDisplayText());
        marker.put("opType", op.getOpType());
        return R.ok(marker);
    }

    @Operation(summary = "会话列表", description = "获取当前用户的客服会话列表（按聊天对象隔离：merchantId 传值→该商家会话，不传→平台会话），按最后消息时间倒序")
    @GetMapping("/sessions")
    public R<List<ChatSessionVO>> listSessions(
            @Parameter(description = "商家ID（不传=平台会话列表）") @RequestParam(required = false) Long merchantId) {
        return R.ok(chatService.listSessions(merchantId));
    }

    @Operation(summary = "消息历史", description = "获取指定会话的消息历史，按时间正序")
    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<ChatMessageVO>> getMessages(
            @Parameter(description = "会话ID") @PathVariable Long sessionId) {
        return R.ok(chatService.getMessages(sessionId));
    }

    @Operation(summary = "删除会话", description = "逻辑删除指定会话及其所有消息")
    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> deleteSession(
            @Parameter(description = "会话ID") @PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return R.ok();
    }
}

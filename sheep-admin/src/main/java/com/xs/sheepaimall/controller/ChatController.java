package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.ChatAddCartOpDTO;
import com.xs.sheepaimall.dto.ChatAfterSaleOpDTO;
import com.xs.sheepaimall.dto.ChatCreateOrderOpDTO;
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

    @Operation(summary = "操作单状态批量查询", description = "刷新历史确认卡时回查操作单最新状态：PENDING=待确认 / DONE=已执行 / CANCELLED=已取消 / EXPIRED=已过期。仅返回归属当前用户的操作单")
    @GetMapping("/pending-ops/states")
    public R<Map<String, String>> pendingOpStates(
            @Parameter(description = "操作单ID列表（英文逗号分隔）") @RequestParam String ids) {
        List<String> operationIds = java.util.Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return R.ok(pendingOperationService.queryStates(operationIds, UserContext.getUserId()));
    }

    @Operation(summary = "商品卡片规格列表", description = "商品卡片加购选规格用：返回该商品的在售 SKU（规格名/价格/库存），SKU ID 已字符串化，前端可直接使用")
    @GetMapping("/goods/{spuId}/skus")
    public R<List<Map<String, Object>>> goodsSkus(
            @Parameter(description = "商品ID") @PathVariable Long spuId) {
        return R.ok(chatService.listGoodsSkus(spuId));
    }

    @Operation(summary = "商品卡片购买操作单", description = "商品卡片的\"购买\"按钮调用：选规格/数量/地址后仅生成待确认操作单（CREATE_ORDER，走确认闸），用户确认后创建待支付订单（不代支付）")
    @PostMapping("/create-order-op")
    public R<Map<String, String>> createOrderOp(@Valid @RequestBody ChatCreateOrderOpDTO dto) {
        ChatPendingOp op = chatService.createOrderOp(dto.getSpuId(), dto.getSkuId(),
                dto.getQuantity(), dto.getAddressId(), dto.getSessionId());
        Map<String, String> marker = new java.util.LinkedHashMap<>();
        marker.put("operationId", op.getId());
        marker.put("displayText", op.getDisplayText());
        marker.put("opType", op.getOpType());
        return R.ok(marker);
    }

    @Operation(summary = "订单卡片售后操作单", description = "订单卡片的\"申请售后\"按钮调用：仅生成待确认操作单（AFTER_SALE，走确认闸），用户确认后创建待处理售后单，退款由商家处理")
    @PostMapping("/after-sale-op")
    public R<Map<String, String>> afterSaleOp(@Valid @RequestBody ChatAfterSaleOpDTO dto) {
        ChatPendingOp op = chatService.createAfterSaleOp(dto.getOrderId(), dto.getType(),
                dto.getReason(), dto.getSessionId());
        Map<String, String> marker = new java.util.LinkedHashMap<>();
        marker.put("operationId", op.getId());
        marker.put("displayText", op.getDisplayText());
        marker.put("opType", op.getOpType());
        return R.ok(marker);
    }

    @Operation(summary = "聊天对象聚合时间线", description = "单时间线展示用：按 (当前用户, merchantId) 合并全部会话消息，消息 ID 游标分页（beforeId=当前最早消息 id，不传=最新一页），正序返回；含 currentSessionId（发送消息复用）与 hasMore。仅本人数据")
    @GetMapping("/messages/timeline")
    public R<com.xs.sheepaimall.vo.ChatTimelineVO> timeline(
            @Parameter(description = "商家ID（不传=平台聊天对象）") @RequestParam(required = false) Long merchantId,
            @Parameter(description = "游标：早于该消息ID（不传=最新一页）") @RequestParam(required = false) Long beforeId,
            @Parameter(description = "每页条数（默认20，最大50）") @RequestParam(defaultValue = "20") int size) {
        return R.ok(chatService.getTimeline(merchantId, beforeId, size));
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

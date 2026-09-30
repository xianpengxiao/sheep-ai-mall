package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.ChatSendDTO;
import com.xs.sheepaimall.service.ChatService;
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

/** 智能客服接口 */
@Tag(name = "智能客服", description = "基于 DeepSeek + RAG 的智能客服对话")
@Validated
@RestController
@RequestMapping("/api/ai/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Operation(summary = "发送消息（流式）", description = "发送消息给AI客服，SSE流式返回回复。sessionId为空则自动创建新会话。")
    @PostMapping(value = "/send", produces = "text/html;charset=utf-8")
    public Flux<String> send(@Valid @RequestBody ChatSendDTO dto) {
        return chatService.sendMessage(dto);
    }

    @Operation(summary = "会话列表", description = "获取当前用户的客服会话列表，按最后消息时间倒序")
    @GetMapping("/sessions")
    public R<List<ChatSessionVO>> listSessions() {
        return R.ok(chatService.listSessions());
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

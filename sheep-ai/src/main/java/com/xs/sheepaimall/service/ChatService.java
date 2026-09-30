package com.xs.sheepaimall.service;

import com.xs.sheepaimall.dto.ChatSendDTO;
import com.xs.sheepaimall.vo.ChatMessageVO;
import com.xs.sheepaimall.vo.ChatSessionVO;
import reactor.core.publisher.Flux;

import java.util.List;

/** 智能客服 Service */
public interface ChatService {

    /**
     * 发送消息（流式返回）
     *
     * @param dto 会话ID（可为空）+ 消息内容
     * @return SSE 流式 AI 回复
     */
    Flux<String> sendMessage(ChatSendDTO dto);

    /** 当前用户的会话列表 */
    List<ChatSessionVO> listSessions();

    /** 会话消息历史 */
    List<ChatMessageVO> getMessages(Long sessionId);

    /** 删除会话 */
    void deleteSession(Long sessionId);
}

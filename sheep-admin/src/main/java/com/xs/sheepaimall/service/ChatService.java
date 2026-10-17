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

    /**
     * 当前用户的会话列表（按聊天对象隔离）
     *
     * @param merchantId 聊天对象：商家ID（null=平台会话列表）
     */
    List<ChatSessionVO> listSessions(Long merchantId);

    /** 会话消息历史 */
    List<ChatMessageVO> getMessages(Long sessionId);

    /** 删除会话 */
    void deleteSession(Long sessionId);

    /**
     * 商品卡片"加入购物车"专用：仅生成待确认操作单（走确认闸，不直接加购）。
     * 校验 SKU 在售，参数快照含 spuId/skuId/quantity。
     *
     * @param skuId     商品规格ID
     * @param quantity  数量（null/<=0 按 1）
     * @param sessionId 当前会话ID（可选，仅作操作单归属记录）
     * @return 创建成功的操作单（含 operationId）
     */
    com.xs.sheepaimall.entity.ChatPendingOp createAddCartOp(Long skuId, Integer quantity, Long sessionId);
}

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

    /**
     * 商品卡片"购买"专用：仅生成待确认操作单（CREATE_ORDER，走确认闸，不直接下单不代支付）。
     * 校验商品/规格在售与库存、收货地址归属当前用户。
     *
     * @param spuId     商品ID
     * @param skuId     规格ID
     * @param quantity  数量（null/<=0 按 1）
     * @param addressId 收货地址ID
     * @param sessionId 当前会话ID（可选，仅作操作单归属记录）
     * @return 创建成功的操作单（含 operationId）
     */
    com.xs.sheepaimall.entity.ChatPendingOp createOrderOp(Long spuId, Long skuId, Integer quantity,
                                                          Long addressId, Long sessionId);

    /**
     * 订单卡片"申请售后"专用：仅生成待确认操作单（AFTER_SALE，走确认闸，确认后才创建售后单）。
     * 校验与用户端申请同规则（归属/状态-类型映射/无重复待处理单）。
     *
     * @param orderId   订单ID
     * @param type      售后类型（REFUND_ONLY/RETURN_REFUND/EXCHANGE）
     * @param reason    申请原因
     * @param sessionId 当前会话ID（可选，仅作操作单归属记录）
     * @return 创建成功的操作单（含 operationId）
     */
    com.xs.sheepaimall.entity.ChatPendingOp createAfterSaleOp(Long orderId, String type, String reason,
                                                              Long sessionId);

    /**
     * 聊天对象聚合时间线（单时间线展示）：按 (userId, merchantId) 合并全部会话的消息，
     * 以消息 ID 游标分页（beforeId 为当前最早一条消息 id，null=最新一页），时间正序返回；
     * 返回体含 currentSessionId（该对象最近活跃会话，供发送消息复用）与 hasMore。
     *
     * @param merchantId 聊天对象：商家ID（null=平台）
     * @param beforeId   游标：早于该消息 id（null=最新一页）
     * @param size       每页条数
     */
    com.xs.sheepaimall.vo.ChatTimelineVO getTimeline(Long merchantId, Long beforeId, int size);

    /**
     * 商品卡片加购选规格用：返回指定 SPU 的在售 SKU 列表。
     * SKU ID 一律字符串化（雪花 ID 19 位，裸数字 JSON 前端解析精度丢失）。
     *
     * @param spuId 商品ID
     * @return [{skuId, skuName, price, stock}]
     */
    List<java.util.Map<String, Object>> listGoodsSkus(Long spuId);
}

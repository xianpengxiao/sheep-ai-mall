package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.entity.AfterSale;

import java.util.Collection;
import java.util.Map;

/**
 * 售后单服务 —— 用户申请（按订单状态收敛类型）、商家处理（拒绝/同意）。
 * <p>退款类（仅退款/退货退款）同意时联动：退款给用户 + 商家退款扣回流水 + 订单退款标记，
 * 全程事务保护，任一步失败整体回滚，商家可重试。</p>
 */
public interface AfterSaleService {

    /** 类型：仅退款 */
    String TYPE_REFUND_ONLY = "REFUND_ONLY";

    /** 类型：退货退款 */
    String TYPE_RETURN_REFUND = "RETURN_REFUND";

    /** 类型：换货 */
    String TYPE_EXCHANGE = "EXCHANGE";

    /** 状态：待处理 */
    String STATUS_PENDING = "PENDING";

    /** 状态：已完结（同意） */
    String STATUS_APPROVED = "APPROVED";

    /** 状态：已拒绝 */
    String STATUS_REJECTED = "REJECTED";

    /**
     * 用户发起售后申请（用户端与 Agent 确认闸共用同一套校验）。
     *
     * @param userId  当前用户
     * @param orderId 订单ID
     * @param type    类型（见 TYPE_*）
     * @param reason  申请原因
     * @return 创建的售后单
     */
    AfterSale apply(Long userId, Long orderId, String type, String reason);

    /**
     * 售后申请前置校验（Agent 工具生成待确认操作单前调用，不产生任何数据）；
     * 与 apply 完全同一套规则，确认阶段 apply 会再次校验（防 TOCTOU）。
     *
     * @return 校验通过的订单
     */
    com.xs.sheepaimall.entity.OrderInfo validate(Long userId, Long orderId, String type);

    /**
     * 商家同意售后：退款类联动退款/资金/订单标记并完结；换货仅完结。
     *
     * @param merchantId  当前商家ID（归属校验）
     * @param afterSaleId 售后单ID
     */
    void approve(Long merchantId, Long afterSaleId);

    /**
     * 商家拒绝售后。
     *
     * @param merchantId  当前商家ID（归属校验）
     * @param afterSaleId 售后单ID
     * @param rejectReason 拒绝原因（必填）
     */
    void reject(Long merchantId, Long afterSaleId, String rejectReason);

    /** 我的售后单分页（用户端，含订单商品明细） */
    Page<com.xs.sheepaimall.vo.AfterSaleVO> pageByUser(Long userId, int pageNum, int pageSize);

    /** 商家售后单分页（可按状态筛选，含订单商品明细） */
    Page<com.xs.sheepaimall.vo.AfterSaleVO> pageByMerchant(Long merchantId, int pageNum, int pageSize, String status);

    /** 批量查询订单的待处理售后单：orderId -> 售后单状态（订单列表"售后中"标识用） */
    Map<Long, String> pendingStatusByOrderIds(Collection<Long> orderIds);
}

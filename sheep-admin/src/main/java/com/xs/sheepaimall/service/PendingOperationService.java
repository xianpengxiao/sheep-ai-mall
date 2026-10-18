package com.xs.sheepaimall.service;

import com.xs.sheepaimall.entity.ChatPendingOp;

import java.util.Map;

/**
 * 待确认操作单服务 —— 智能客服写工具的安全落地。
 * <p>写工具绝不直接执行业务，只生成操作单；用户通过 confirm 接口确认后由服务端执行。
 * 操作单双写 Redis（chat:pendingop:{operationId}，TTL 5 分钟）+ MySQL（chat_pending_op，幂等记录），
 * Redis 不可用时仅用 MySQL。</p>
 */
public interface PendingOperationService {

    /** 操作类型：修改收货地址 */
    String OP_UPDATE_ADDRESS = "UPDATE_ADDRESS";

    /** 操作类型：取消订单 */
    String OP_CANCEL_ORDER = "CANCEL_ORDER";

    /** 操作类型：加入购物车 */
    String OP_ADD_CART = "ADD_CART";

    /** 操作类型：创建待支付订单（AI 客服代下单，确认后用户自行支付） */
    String OP_CREATE_ORDER = "CREATE_ORDER";

    /** 操作类型：申请售后（退货退款/仅退款/换货，确认后创建待处理售后单，退款由商家处理） */
    String OP_AFTER_SALE = "AFTER_SALE";

    /**
     * 创建待确认操作单（双写 Redis + MySQL，5 分钟过期）
     *
     * @param userId      归属用户
     * @param sessionId   会话ID
     * @param opType      操作类型（见 OP_* 常量）
     * @param args        参数快照（序列化为 argsJson 存储）
     * @param displayText 给用户看的操作描述
     * @return 创建成功的操作单（含 operationId）
     */
    ChatPendingOp create(Long userId, Long sessionId, String opType, Map<String, Object> args, String displayText);

    /**
     * 确认/取消操作单。
     * <p>校验操作单存在、归属当前用户、未过期；用原子抢占（UPDATE ... WHERE id=? AND status=0）
     * 保证幂等，影响行数为 0 视为已处理。approve=true 时由服务端从参数快照反序列化并调用真正的业务 Service 执行；
     * approve=false 时仅标记取消。确认请求只带 operationId + approve，不带业务参数。</p>
     *
     * @param operationId   操作单ID
     * @param approve       true=确认执行 false=取消
     * @param currentUserId 当前登录用户ID（用于归属校验）
     */
    void confirm(String operationId, boolean approve, Long currentUserId);

    /**
     * 批量查询操作单最新状态（供前端刷新历史确认卡时回显）。
     * 仅返回归属当前用户且仍在有效期内的操作单状态。
     *
     * @param operationIds 操作单ID列表
     * @param currentUserId 当前登录用户ID（归属校验）
     * @return operationId -> 状态描述（PENDING=待确认 / DONE=已执行 / CANCELLED=已取消 / EXPIRED=已过期）
     */
    java.util.Map<String, String> queryStates(java.util.List<String> operationIds, Long currentUserId);

    /**
     * 是否已存在同订单的待确认售后操作单（防同一张订单卡片重复生成多张确认卡）。
     *
     * @param userId  归属用户
     * @param orderId 订单ID（匹配 argsJson 中的 orderId）
     * @return true=存在待确认售后操作单
     */
    boolean existsPendingAfterSaleOp(Long userId, Long orderId);
}

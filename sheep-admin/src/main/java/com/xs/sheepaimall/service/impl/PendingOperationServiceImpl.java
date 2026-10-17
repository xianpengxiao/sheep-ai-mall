package com.xs.sheepaimall.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.dto.AddressSaveDTO;
import com.xs.sheepaimall.dto.CartAddDTO;
import com.xs.sheepaimall.entity.ChatPendingOp;
import com.xs.sheepaimall.mapper.ChatPendingOpMapper;
import com.xs.sheepaimall.service.CartService;
import com.xs.sheepaimall.service.OrderService;
import com.xs.sheepaimall.service.PendingOperationService;
import com.xs.sheepaimall.service.ShippingAddressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 待确认操作单服务实现 —— 双写 Redis + MySQL，原子抢占保证确认幂等。
 * <p>参考 ChatServiceImpl 的降级模式：Redis 挂了仅用 MySQL，不影响主流程。</p>
 */
@Slf4j
@Service
public class PendingOperationServiceImpl implements PendingOperationService {

    private static final String REDIS_KEY_PREFIX = "chat:pendingop:";
    private static final long OP_TTL_MINUTES = 5;

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_EXECUTED = 1;
    private static final int STATUS_CANCELLED = 2;

    @Autowired
    private ChatPendingOpMapper chatPendingOpMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ShippingAddressService shippingAddressService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Override
    public ChatPendingOp create(Long userId, Long sessionId, String opType, Map<String, Object> args, String displayText) {
        ChatPendingOp op = new ChatPendingOp();
        op.setId(UUID.randomUUID().toString());
        op.setUserId(userId);
        op.setSessionId(sessionId);
        op.setOpType(opType);
        op.setArgsJson(JSONUtil.toJsonStr(args));
        op.setDisplayText(displayText);
        op.setStatus(STATUS_PENDING);
        op.setExpireTime(LocalDateTime.now().plusMinutes(OP_TTL_MINUTES));
        // MySQL 为幂等记录，必须成功
        chatPendingOpMapper.insert(op);

        // Redis 暂存加速读取，失败仅告警（降级为纯 MySQL）
        try {
            stringRedisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + op.getId(), JSONUtil.toJsonStr(op), Duration.ofMinutes(OP_TTL_MINUTES));
        } catch (Exception e) {
            log.warn("操作单写入 Redis 失败，降级为纯 MySQL 模式 operationId={}", op.getId());
        }
        return op;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(String operationId, boolean approve, Long currentUserId) {
        ChatPendingOp op = chatPendingOpMapper.selectById(operationId);
        if (op == null) {
            throw new BizException("操作单不存在或已过期");
        }
        if (!op.getUserId().equals(currentUserId)) {
            throw new BizException("无权操作此操作单");
        }
        if (op.getExpireTime() != null && op.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException("操作已过期，请重新发起");
        }

        // 原子抢占：影响行数为 0 说明已被执行/取消（满足幂等要求）
        int rows = chatPendingOpMapper.update(null, new LambdaUpdateWrapper<ChatPendingOp>()
                .eq(ChatPendingOp::getId, operationId)
                .eq(ChatPendingOp::getStatus, STATUS_PENDING)
                .set(ChatPendingOp::getStatus, approve ? STATUS_EXECUTED : STATUS_CANCELLED));
        if (rows == 0) {
            throw new BizException("该操作已处理过，请勿重复提交");
        }

        // 清理 Redis 暂存，失败忽略
        try {
            stringRedisTemplate.delete(REDIS_KEY_PREFIX + operationId);
        } catch (Exception ignored) {
        }

        if (approve) {
            try {
                execute(op);
            } catch (BizException e) {
                // 业务校验失败（如订单状态不允许取消）：回滚状态，允许用户重试或重新发起
                resetToPending(operationId);
                throw e;
            } catch (Exception e) {
                resetToPending(operationId);
                log.error("操作单执行失败 operationId={}", operationId, e);
                throw new BizException("操作执行失败，请重试");
            }
        }
    }

    /** 将操作单状态回滚为待处理（执行失败时允许重试） */
    private void resetToPending(String operationId) {
        chatPendingOpMapper.update(null, new LambdaUpdateWrapper<ChatPendingOp>()
                .eq(ChatPendingOp::getId, operationId)
                .set(ChatPendingOp::getStatus, STATUS_PENDING));
    }

    /**
     * 按 opType 分发执行。
     * <p>执行参数以操作单快照（argsJson）为准；归属校验已在 confirm 阶段用 currentUserId 完成，
     * 这里仍以快照中的 userId 作为业务参数，不依赖 UserContext。</p>
     */
    private void execute(ChatPendingOp op) {
        switch (op.getOpType()) {
            case OP_UPDATE_ADDRESS -> {
                // ShippingAddressService.update 内部会再次校验地址归属 userId
                AddressSaveDTO dto = JSONUtil.toBean(op.getArgsJson(), AddressSaveDTO.class);
                dto.setUserId(op.getUserId());
                shippingAddressService.update(dto);
            }
            case OP_CANCEL_ORDER -> {
                // OrderService.cancel 内部校验订单归属（confirm 为 MVC 线程，UserContext 可用）且仅待支付可取消
                Long orderId = JSONUtil.<Map<String, Object>>parseObj(op.getArgsJson()).get("orderId", Long.class);
                orderService.cancel(orderId);
            }
            case OP_ADD_CART -> {
                cn.hutool.json.JSONObject args = JSONUtil.parseObj(op.getArgsJson());
                CartAddDTO dto = new CartAddDTO();
                dto.setUserId(op.getUserId());
                dto.setSpuId(args.get("spuId", Long.class));
                dto.setSkuId(args.get("skuId", Long.class));
                dto.setQuantity(args.get("quantity", Integer.class));
                cartService.add(dto);
            }
            default -> throw new BizException("不支持的操作类型：" + op.getOpType());
        }
        log.info("操作单执行成功 operationId={}, opType={}", op.getId(), op.getOpType());
    }
}

package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.dto.OrderCreateDTO;
import com.xs.sheepaimall.entity.OrderInfo;
import com.xs.sheepaimall.vo.OrderInfoVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单 Service —— 下单事务管控
 */
public interface OrderService extends IService<OrderInfo> {

    /** 下单：校验商品 → 扣库存 → 生成订单 → 清空购物车，异常自动回滚 */
    OrderInfoVO create(OrderCreateDTO dto);

    /** 查询订单详情（含明细） */
    OrderInfoVO getDetailById(Long id);

    /** 分页查询会员订单 */
    Page<OrderInfo> pageByMemberId(Long memberId, int pageNum, int pageSize);

    /** 更新支付状态（支付回调专用） */
    void updatePayStatus(Long orderId, BigDecimal payAmount, Integer status, LocalDateTime payTime);

    /** 取消订单：仅待支付状态可取消，回滚库存和销量 */
    OrderInfoVO cancel(Long orderId);
}

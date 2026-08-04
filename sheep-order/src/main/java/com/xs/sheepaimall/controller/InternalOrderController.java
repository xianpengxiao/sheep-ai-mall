package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 订单服务内部控制器（供 Feign 调用）
 */
@RestController
@RequestMapping("/internal/order")
public class InternalOrderController {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ProductReviewMapper productReviewMapper;

    @GetMapping("/{id}")
    public OrderInfo getOrderById(@PathVariable Long id) {
        return orderInfoMapper.selectById(id);
    }

    @GetMapping("/by-order-no")
    public OrderInfo getOrderByOrderNo(@RequestParam String orderNo) {
        return orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo));
    }

    @PutMapping("/{id}/remark")
    public void updateOrderRemark(@PathVariable Long id, @RequestParam String remark) {
        OrderInfo order = orderInfoMapper.selectById(id);
        if (order != null) {
            order.setRemark(remark);
            orderInfoMapper.updateById(order);
        }
    }

    @PutMapping("/{id}/pay-status")
    public void updatePayStatus(@PathVariable Long id, @RequestParam Long payAmount,
                                @RequestParam Integer status, @RequestParam String payTime) {
        OrderInfo order = orderInfoMapper.selectById(id);
        if (order != null) {
            order.setPayAmount(BigDecimal.valueOf(payAmount));
            order.setStatus(status);
            order.setPayTime(LocalDateTime.parse(payTime));
            orderInfoMapper.updateById(order);
        }
    }

    @GetMapping("/items/by-order/{orderId}")
    public List<OrderItem> getOrderItemsByOrderId(@PathVariable Long orderId) {
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
    }

    @GetMapping("/items/by-order-ids")
    public List<OrderItem> listOrderItemsByOrderIds(@RequestParam List<Long> orderIds) {
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
    }

    @GetMapping("/reviews/by-spu/{spuId}")
    public List<ProductReview> getReviewsBySpuId(@PathVariable Long spuId) {
        return productReviewMapper.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getSpuId, spuId)
                        .eq(ProductReview::getStatus, 1)
                        .eq(ProductReview::getReviewStatus, 1));
    }

    @GetMapping("/reviews/by-spu-ids")
    public List<ProductReview> listReviewsBySpuIds(@RequestParam List<Long> spuIds) {
        return productReviewMapper.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .in(ProductReview::getSpuId, spuIds)
                        .eq(ProductReview::getStatus, 1));
    }

    @GetMapping("/reviews/expired")
    public List<ProductReview> listExpiredReviews(@RequestParam int days) {
        return productReviewMapper.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getReviewStatus, 1)
                        .lt(ProductReview::getCreateTime, LocalDateTime.now().minusDays(days)));
    }

    @PutMapping("/reviews/{id}/hide")
    public void hideReview(@PathVariable Long id) {
        ProductReview review = productReviewMapper.selectById(id);
        if (review != null) {
            review.setStatus(0);
            productReviewMapper.updateById(review);
        }
    }

    @PostMapping("/reviews/expire")
    public int expireReviews() {
        return productReviewMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProductReview>()
                        .eq(ProductReview::getReviewStatus, 0)
                        .lt(ProductReview::getExpiredAt, LocalDateTime.now())
                        .set(ProductReview::getReviewStatus, 2));
    }

    // ========== 以下为 Merchant 模块 Feign 调用所需端点 ==========

    @GetMapping("/items/list-by-spu-ids")
    public List<OrderItem> listOrderItemsBySpuIds(@RequestParam List<Long> spuIds) {
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getSpuId, spuIds));
    }

    @PostMapping("/page-by-ids")
    public Page<OrderInfo> pageOrdersByIds(
            @RequestBody Set<Long> orderIds,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status) {
        return orderInfoMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OrderInfo>()
                        .in(OrderInfo::getId, orderIds)
                        .eq(status != null, OrderInfo::getStatus, status)
                        .orderByDesc(OrderInfo::getCreateTime));
    }

    @PutMapping("/{id}/deliver")
    public void deliverOrder(@PathVariable Long id,
                             @RequestParam String deliveryCompany,
                             @RequestParam String deliveryNo) {
        OrderInfo order = orderInfoMapper.selectById(id);
        if (order != null) {
            order.setStatus(2);
            order.setDeliveryTime(LocalDateTime.now());
            orderInfoMapper.updateById(order);
        }
    }

    @PutMapping("/{id}")
    public void updateOrder(@PathVariable Long id, @RequestBody OrderInfo order) {
        order.setId(id);
        orderInfoMapper.updateById(order);
    }
}

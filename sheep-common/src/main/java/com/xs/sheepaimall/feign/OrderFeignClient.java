package com.xs.sheepaimall.feign;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.entity.OrderInfo;
import com.xs.sheepaimall.entity.OrderItem;
import com.xs.sheepaimall.entity.ProductReview;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 订单服务内部 Feign 接口
 */
@FeignClient(name = "sheep-order", path = "/internal/order")
public interface OrderFeignClient {

    @GetMapping("/{id}")
    OrderInfo getOrderById(@PathVariable Long id);

    @GetMapping("/by-order-no")
    OrderInfo getOrderByOrderNo(@RequestParam String orderNo);

    @PutMapping("/{id}/pay-status")
    void updatePayStatus(@PathVariable Long id, @RequestParam Long payAmount,
                         @RequestParam Integer status, @RequestParam String payTime);

    @PutMapping("/{id}/remark")
    void updateOrderRemark(@PathVariable Long id, @RequestParam String remark);

    @GetMapping("/items/by-order/{orderId}")
    List<OrderItem> getOrderItemsByOrderId(@PathVariable Long orderId);

    @GetMapping("/items/by-order-ids")
    List<OrderItem> listOrderItemsByOrderIds(@RequestParam List<Long> orderIds);

    @GetMapping("/reviews/by-spu/{spuId}")
    List<ProductReview> getReviewsBySpuId(@PathVariable Long spuId);

    @GetMapping("/reviews/by-spu-ids")
    List<ProductReview> listReviewsBySpuIds(@RequestParam List<Long> spuIds);

    @GetMapping("/reviews/expired")
    List<ProductReview> listExpiredReviews(@RequestParam int days);

    @PutMapping("/reviews/{id}/hide")
    void hideReview(@PathVariable Long id);

    @PostMapping("/reviews/expire")
    int expireReviews();

    @GetMapping("/reviews/count-by-spu/{spuId}")
    Integer countReviewsBySpuId(@PathVariable Long spuId, @RequestParam Integer status);

    @GetMapping("/reviews/stats-by-spu/{spuId}")
    Object getReviewStatsBySpuId(@PathVariable Long spuId, @RequestParam Integer status);

    @GetMapping("/items/list-by-spu-ids")
    List<OrderItem> listOrderItemsBySpuIds(@RequestParam List<Long> spuIds);

    @PostMapping("/page-by-ids")
    Page<OrderInfo> pageOrdersByIds(@RequestBody Set<Long> orderIds,
                                    @RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize,
                                    @RequestParam(required = false) Integer status);

    @PutMapping("/{id}/deliver")
    void deliverOrder(@PathVariable Long id,
                      @RequestParam String deliveryCompany,
                      @RequestParam String deliveryNo);

    @PutMapping("/{id}")
    void updateOrder(@PathVariable Long id, @RequestBody OrderInfo order);
}

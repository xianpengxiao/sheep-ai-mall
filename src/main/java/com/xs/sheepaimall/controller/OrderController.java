package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.common.ResultCode;
import com.xs.sheepaimall.dto.OrderCreateDTO;
import com.xs.sheepaimall.entity.OrderInfo;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.OrderService;
import com.xs.sheepaimall.vo.OrderInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单接口 —— 下单事务管控
 */
@Tag(name = "订单", description = "下单、订单查询")
@Validated
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Resource
    private OrderService orderService;

    @Operation(summary = "下单",
            description = "校验商品有效性 → 原子扣减库存 → 生成订单+明细 → 清空购物车。异常自动回滚。")
    @PostMapping("/create")
    public R<OrderInfoVO> create(@Valid @RequestBody OrderCreateDTO dto) {
        return R.ok(orderService.create(dto));
    }

    @Operation(summary = "查询订单详情", description = "含订单明细列表")
    @GetMapping("/{id}")
    public R<OrderInfoVO> detail(@Parameter(description = "订单ID") @PathVariable Long id) {
        return R.ok(orderService.getDetailById(id));
    }

    @Operation(summary = "会员订单分页",
            description = "普通用户只能查看自己的订单，拥有 order:list 权限可查看任意用户订单。不传 userId 默认查当前用户。")
    @GetMapping("/page")
    public R<Page<OrderInfoVO>> page(
            @Parameter(description = "会员ID（可选，默认当前登录用户；如需查他人需 order:list 权限）")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        Long currentUserId = UserContext.getUserId();
        if (userId == null) {
            userId = currentUserId;
        } else if (!userId.equals(currentUserId)) {
            // 查询他人订单需要 order:list 权限
            List<String> permissions = UserContext.getPermissions();
            if (permissions == null || !permissions.contains("order:list")) {
                throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权查看其他用户的订单");
            }
        }
        return R.ok(orderService.pageByUserId(userId, pageNum, pageSize));
    }

    @Operation(summary = "查询订单列表",
            description = "普通用户只能查看自己的订单，拥有 order:list 权限可查看任意用户订单。不传 userId 默认查当前用户。不分页，如需分页请使用 /page 接口。")
    @GetMapping("/list")
    public R<List<OrderInfo>> list(
            @Parameter(description = "用户ID（可选，默认当前登录用户；查他人需 order:list 权限）")
            @RequestParam(required = false) Long userId) {
        Long currentUserId = UserContext.getUserId();
        if (userId == null) {
            userId = currentUserId;
        } else if (!userId.equals(currentUserId)) {
            List<String> permissions = UserContext.getPermissions();
            if (permissions == null || !permissions.contains("order:list")) {
                throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权查看其他用户的订单");
            }
        }
        return R.ok(orderService.listByUserId(userId));
    }

    @Operation(summary = "取消订单",
            description = "仅待支付(0)状态可取消。回滚库存、销量，设置 status=4(已取消) 和 cancelTime。" +
                    "全部操作在同一事务中，异常自动回滚。")
    @PutMapping("/{id}/cancel")
    public R<OrderInfoVO> cancel(@Parameter(description = "订单ID") @PathVariable Long id) {
        return R.ok(orderService.cancel(id));
    }
}

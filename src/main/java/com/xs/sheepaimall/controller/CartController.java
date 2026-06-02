package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.CartAddDTO;
import com.xs.sheepaimall.service.CartService;
import com.xs.sheepaimall.vo.CartVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车接口 —— Redis Hash 缓存 + MySQL 持久化双写
 */
@Tag(name = "购物车", description = "购物车增删改查、全选清空")
@Validated
@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Resource
    private CartService cartService;

    @Operation(summary = "加入购物车", description = "已存在同SKU则累加数量")
    @PostMapping("/add")
    public R<Object> add(@Valid @RequestBody CartAddDTO dto) {
        cartService.add(dto);
        return R.ok();
    }

    @Operation(summary = "修改购物车数量")
    @PutMapping("/{id}/quantity")
    public R<Object> updateQuantity(
            @Parameter(description = "购物车记录ID") @PathVariable Long id,
            @Parameter(description = "数量") @RequestParam Integer quantity) {
        cartService.updateQuantity(id, quantity);
        return R.ok();
    }

    @Operation(summary = "修改选中状态")
    @PutMapping("/{id}/selected")
    public R<Object> updateSelected(
            @Parameter(description = "购物车记录ID") @PathVariable Long id,
            @Parameter(description = "是否选中：1=是 0=否") @RequestParam Integer selected) {
        cartService.updateSelected(id, selected);
        return R.ok();
    }

    @Operation(summary = "批量删除购物车记录")
    @DeleteMapping("/batch")
    public R<Object> batchRemove(@RequestBody List<Long> ids) {
        cartService.batchRemove(ids);
        return R.ok();
    }

    @Operation(summary = "删除单条购物车记录")
    @DeleteMapping("/{id}")
    public R<Object> removeItem(@Parameter(description = "购物车记录ID") @PathVariable Long id) {
        cartService.removeItem(id);
        return R.ok();
    }

    @Operation(summary = "一键清空会员购物车")
    @DeleteMapping("/clear/{userId}")
    public R<Object> clear(@Parameter(description = "会员ID") @PathVariable Long userId) {
        cartService.clear(userId);
        return R.ok();
    }

    @Operation(summary = "查询购物车列表", description = "含商品名称、图片、规格、单价等冗余信息")
    @GetMapping("/{userId}")
    public R<List<CartVO>> list(@Parameter(description = "会员ID") @PathVariable Long userId) {
        return R.ok(cartService.listByUserId(userId));
    }

    @Operation(summary = "全选/取消全选")
    @PutMapping("/select-all/{userId}")
    public R<Object> selectAll(
            @Parameter(description = "会员ID") @PathVariable Long userId,
            @Parameter(description = "是否选中：1=是 0=否") @RequestParam Integer selected) {
        cartService.selectAll(userId, selected);
        return R.ok();
    }
}

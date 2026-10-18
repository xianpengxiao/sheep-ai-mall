package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.AfterSaleRejectDTO;
import com.xs.sheepaimall.entity.AfterSale;
import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.mapper.MerchantMapper;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.AfterSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 售后（商家端）—— 仅可操作归属本商家的售后单 */
@Tag(name = "商家-售后管理", description = "商家售后单列表与处理：同意（退款类联动退款/资金/订单）、拒绝")
@Validated
@RestController
@RequestMapping("/api/merchant/after-sale")
public class MerchantAfterSaleController {

    @Autowired
    private AfterSaleService afterSaleService;

    @Autowired
    private MerchantMapper merchantMapper;

    @Operation(summary = "售后单列表", description = "本商家的售后单分页，可按状态筛选（PENDING/APPROVED/REJECTED），按创建时间倒序")
    @GetMapping
    public R<Page<com.xs.sheepaimall.vo.AfterSaleVO>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status) {
        return R.ok(afterSaleService.pageByMerchant(getScope(), pageNum, pageSize, status));
    }

    @Operation(summary = "同意售后", description = "退款类（仅退款/退货退款）同意时联动：退款给用户 + 商家退款扣回流水 + 订单标记已退款；换货仅完结。失败整体回滚可重试")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@Parameter(description = "售后单ID") @PathVariable Long id) {
        afterSaleService.approve(getScope(), id);
        return R.ok();
    }

    @Operation(summary = "拒绝售后", description = "填写拒绝原因后拒绝，订单与资金不变")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@Parameter(description = "售后单ID") @PathVariable Long id,
                          @Valid @RequestBody AfterSaleRejectDTO dto) {
        afterSaleService.reject(getScope(), id, dto.getRejectReason());
        return R.ok();
    }

    /** 当前登录账号对应的商家ID（scope 推导，与商家客服控制台一致） */
    private Long getScope() {
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, UserContext.getUserId()));
        if (merchant == null) {
            throw new BizException("当前账号不是商家");
        }
        return merchant.getId();
    }
}

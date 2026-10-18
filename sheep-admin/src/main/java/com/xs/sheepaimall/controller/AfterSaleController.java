package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.AfterSaleApplyDTO;
import com.xs.sheepaimall.entity.AfterSale;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.AfterSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 售后（用户端） */
@Tag(name = "售后", description = "用户售后申请与查询")
@Validated
@RestController
@RequestMapping("/api/after-sale")
public class AfterSaleController {

    @Autowired
    private AfterSaleService afterSaleService;

    @Operation(summary = "发起售后申请", description = "整单申请；类型按订单状态收敛（待发货=仅退款；待收货/已完成=退货退款/换货）；同订单仅允许一条待处理售后单")
    @PostMapping("/apply")
    public R<AfterSale> apply(@Valid @RequestBody AfterSaleApplyDTO dto) {
        return R.ok(afterSaleService.apply(UserContext.getUserId(), dto.getOrderId(),
                dto.getType(), dto.getReason()));
    }

    @Operation(summary = "我的售后单", description = "当前用户的售后单分页，按创建时间倒序")
    @GetMapping("/my")
    public R<Page<com.xs.sheepaimall.vo.AfterSaleVO>> my(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(afterSaleService.pageByUser(UserContext.getUserId(), pageNum, pageSize));
    }
}

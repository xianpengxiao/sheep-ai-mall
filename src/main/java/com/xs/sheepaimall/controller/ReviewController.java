package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.ReviewDTO;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.ReviewService;
import com.xs.sheepaimall.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "商品评价", description = "买家评价 + 平台管理")
@Validated
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    @Resource
    private ReviewService reviewService;

    @Operation(summary = "提交评价", description = "需已支付的订单，每条订单明细仅可评价一次")
    @PostMapping
    public R<ReviewVO> create(@Valid @RequestBody ReviewDTO dto) {
        return R.ok(reviewService.create(dto));
    }

    @Operation(summary = "商品评价列表", description = "查询某个商品的所有显示状态的评价")
    @GetMapping("/spu/{spuId}")
    public R<Page<ReviewVO>> pageBySpu(
            @Parameter(description = "商品SPU ID") @PathVariable Long spuId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(reviewService.pageBySpu(spuId, pageNum, pageSize));
    }

    @Operation(summary = "删除自己的评价", description = "只能删除自己的评价")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "评价ID") @PathVariable Long id) {
        reviewService.deleteMyReview(id);
        return R.ok();
    }
}

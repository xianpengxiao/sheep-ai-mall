package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "平台管理-评价", description = "平台管理员评价管理")
@Validated
@RestController
@RequestMapping("/api/admin/review")
public class AdminReviewController {

    @Resource
    private ReviewService reviewService;

    @Operation(summary = "隐藏/显示评价")
    @PutMapping("/{id}/status")
    @RequirePermission("review:manage")
    public R<Void> toggleStatus(
            @Parameter(description = "评价ID") @PathVariable Long id,
            @Parameter(description = "状态 0隐藏 1显示") @RequestParam Integer status) {
        reviewService.toggleStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "删除评价")
    @DeleteMapping("/{id}")
    @RequirePermission("review:delete")
    public R<Void> delete(@Parameter(description = "评价ID") @PathVariable Long id) {
        reviewService.removeReview(id);
        return R.ok();
    }
}

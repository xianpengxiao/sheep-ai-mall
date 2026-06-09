package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.ReviewService;
import com.xs.sheepaimall.vo.ReviewVO;
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

    @Operation(summary = "评价列表分页", description = "支持关键词、评分、显示状态、时间范围筛选")
    @GetMapping("/page")
    @RequirePermission("review:list")
    public R<Page<ReviewVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "关键词(商品名/评价内容/用户名)") @RequestParam(required = false) String keyword,
            @Parameter(description = "评分 1-5") @RequestParam(required = false) Integer rating,
            @Parameter(description = "显示状态 0隐藏 1显示") @RequestParam(required = false) Integer status,
            @Parameter(description = "开始时间 yyyy-MM-dd") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间 yyyy-MM-dd") @RequestParam(required = false) String endTime) {
        return R.ok(reviewService.pageAllReview(pageNum, pageSize, keyword, rating, status, startTime, endTime));
    }

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

package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.SpuAuditDTO;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.vo.SpuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员-商品审核接口（sheep-product 服务）
 */
@Tag(name = "管理员-商品审核", description = "商品审核管理")
@RestController
@RequestMapping("/api/admin")
public class SpuAuditController {

    @Autowired
    private SpuService spuService;

    @Operation(summary = "待审核商品列表")
    @RequirePermission("spu:audit:list")
    @GetMapping("/spu/pending-audit")
    public R<Page<Spu>> pendingAuditSpu(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "审核状态") @RequestParam(required = false) Integer auditStatus,
            @Parameter(description = "商品名称关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "商家ID") @RequestParam(required = false) Long merchantId) {
        return R.ok(spuService.pagePendingAudit(pageNum, pageSize, auditStatus, keyword, categoryId, merchantId));
    }

    @Operation(summary = "审核商品（通过/驳回）")
    @RequirePermission("spu:audit")
    @PutMapping("/spu/audit")
    public R<Void> auditSpu(@Valid @RequestBody SpuAuditDTO dto) {
        spuService.auditSpu(dto.getSpuId(), dto.getAuditStatus(), dto.getAuditMsg());
        return R.ok();
    }

    @Operation(summary = "查看待审核商品详情")
    @RequirePermission("spu:audit:list")
    @GetMapping("/spu/{id}/detail")
    public R<SpuVO> spuDetail(@PathVariable Long id) {
        return R.ok(spuService.getAdminSpuDetail(id));
    }
}

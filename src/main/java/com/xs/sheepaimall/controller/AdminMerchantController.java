package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.MerchantAuditDTO;
import com.xs.sheepaimall.dto.MerchantInfoAuditDTO;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.MerchantService;
import com.xs.sheepaimall.vo.MerchantInfoChangeVO;
import com.xs.sheepaimall.vo.MerchantVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "平台管理-商家", description = "平台管理员商家管理")
@Validated
@RestController
@RequestMapping("/api/admin/merchant")
public class AdminMerchantController {

    @Resource
    private MerchantService merchantService;

    @Operation(summary = "全量商家列表", description = "支持按状态筛选和关键词搜索")
    @GetMapping("/page")
    @RequirePermission("merchant:list")
    public R<Page<MerchantVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "商家状态 0待审核 1已开通 2已关闭") @RequestParam(required = false) Integer status,
            @Parameter(description = "关键词(店铺名/联系人/手机号)") @RequestParam(required = false) String keyword) {
        return R.ok(merchantService.pageAllMerchant(pageNum, pageSize, status, keyword));
    }

    @Operation(summary = "审核入驻申请", description = "通过或驳回商家入驻申请，通过后自动创建商家记录并分配商家角色")
    @PutMapping("/apply/{id}/audit")
    @RequirePermission("merchant:audit")
    public R<Void> audit(
            @Parameter(description = "申请ID") @PathVariable Long id,
            @Valid @RequestBody MerchantAuditDTO dto) {
        merchantService.auditApply(id, dto);
        return R.ok();
    }

    @Operation(summary = "待审核商家信息变更列表")
    @GetMapping("/info-change/pending")
    @RequirePermission("merchant:audit:list")
    public R<Page<MerchantInfoChangeVO>> pendingInfoChange(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(merchantService.pagePendingInfoChange(pageNum, pageSize));
    }

    @Operation(summary = "审核商家信息变更", description = "审核商家A类资质字段变更，通过后更新商家数据")
    @PutMapping("/info-change/audit")
    @RequirePermission("merchant:audit:info")
    public R<Void> auditInfoChange(@Valid @RequestBody MerchantInfoAuditDTO dto) {
        merchantService.auditInfoChange(dto.getChangeId(), dto.getAuditStatus(), dto.getAuditMsg());
        return R.ok();
    }

    @Operation(summary = "禁用/启用商家")
    @PutMapping("/{id}/status")
    @RequirePermission("merchant:disable")
    public R<Void> toggleStatus(
            @Parameter(description = "商家ID") @PathVariable Long id,
            @Parameter(description = "状态 1已开通 2已关闭") @RequestParam Integer status) {
        merchantService.toggleMerchantStatus(id, status);
        return R.ok();
    }
}

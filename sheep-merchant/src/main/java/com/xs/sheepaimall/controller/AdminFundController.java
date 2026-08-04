package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.CommissionConfigSaveDTO;
import com.xs.sheepaimall.dto.SettlementAccountEditDTO;
import com.xs.sheepaimall.dto.WithdrawAuditDTO;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.FundService;
import com.xs.sheepaimall.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;

@Tag(name = "平台管理-资金", description = "平台资金管理：结算账户、佣金规则、分佣明细、提现审核、资金流水、对账报表")
@Validated
@RestController
@RequestMapping("/api/admin/fund")
public class AdminFundController {

    @Autowired
    private FundService fundService;

    // ==================== 1. 结算账户管理 ====================

    @Operation(summary = "结算账户分页", description = "查看全部商家结算账户（脱敏展示）")
    @GetMapping("/settlement-account/page")
    @RequirePermission("fund:account:list")
    public R<Page<SettlementAccountVO>> pageSettlementAccount(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "关键词(店铺名)") @RequestParam(required = false) String keyword) {
        return R.ok(fundService.pageSettlementAccount(pageNum, pageSize, keyword));
    }

    @Operation(summary = "结算账户详情", description = "查询单个商家的结算账户信息")
    @GetMapping("/settlement-account/{merchantId}")
    @RequirePermission("fund:account:list")
    public R<SettlementAccountVO> getSettlementAccount(
            @Parameter(description = "商家ID") @PathVariable Long merchantId) {
        return R.ok(fundService.getSettlementAccount(merchantId));
    }

    @Operation(summary = "编辑结算账户", description = "修改商家结算费率、结算周期、提现权限")
    @PutMapping("/settlement-account/{merchantId}")
    @RequirePermission("fund:account:edit")
    public R<Void> updateSettlementAccount(
            @Parameter(description = "商家ID") @PathVariable Long merchantId,
            @Valid @RequestBody SettlementAccountEditDTO dto) {
        fundService.updateSettlementAccount(merchantId, dto);
        return R.ok();
    }

    // ==================== 2. 佣金规则配置 ====================

    @Operation(summary = "佣金规则分页", description = "按商品分类查看佣金规则列表")
    @GetMapping("/commission/config/page")
    @RequirePermission("fund:commission:list")
    public R<Page<CommissionConfigVO>> pageCommissionConfig(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId) {
        return R.ok(fundService.pageCommissionConfig(pageNum, pageSize, categoryId));
    }

    @Operation(summary = "新增佣金规则", description = "为指定分类设置佣金比例")
    @PostMapping("/commission/config")
    @RequirePermission("fund:commission:edit")
    public R<Void> saveCommissionConfig(@Valid @RequestBody CommissionConfigSaveDTO dto) {
        fundService.saveCommissionConfig(dto);
        return R.ok();
    }

    @Operation(summary = "编辑佣金规则")
    @PutMapping("/commission/config/{id}")
    @RequirePermission("fund:commission:edit")
    public R<Void> updateCommissionConfig(
            @Parameter(description = "规则ID") @PathVariable Long id,
            @Valid @RequestBody CommissionConfigSaveDTO dto) {
        fundService.updateCommissionConfig(id, dto);
        return R.ok();
    }

    @Operation(summary = "启用/禁用佣金规则")
    @PutMapping("/commission/config/{id}/status")
    @RequirePermission("fund:commission:edit")
    public R<Void> toggleCommissionConfig(
            @Parameter(description = "规则ID") @PathVariable Long id,
            @Parameter(description = "状态 0禁用 1启用") @RequestParam Integer status) {
        fundService.toggleCommissionConfig(id, status);
        return R.ok();
    }

    // ==================== 3. 分佣明细 ====================

    @Operation(summary = "分佣记录分页", description = "查询所有订单的分佣明细")
    @GetMapping("/commission/log/page")
    @RequirePermission("fund:commissionLog:list")
    public R<Page<OrderCommissionLogVO>> pageCommissionLog(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "订单号") @RequestParam(required = false) String orderNo,
            @Parameter(description = "商家ID") @RequestParam(required = false) Long merchantId,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(fundService.pageCommissionLog(pageNum, pageSize, orderNo, merchantId, startDate, endDate));
    }

    // ==================== 4. 提现审核 ====================

    @Operation(summary = "提现申请分页", description = "查看全部商家提现申请列表")
    @GetMapping("/withdraw/page")
    @RequirePermission("fund:withdraw:list")
    public R<Page<WithdrawVO>> pageWithdraw(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "状态 0待审核 1待打款 2已打款 3已驳回") @RequestParam(required = false) Integer status,
            @Parameter(description = "商家ID") @RequestParam(required = false) Long merchantId) {
        return R.ok(fundService.pageWithdraw(pageNum, pageSize, status, merchantId));
    }

    @Operation(summary = "审核提现申请", description = "通过或驳回商家提现申请，驳回后资金回退")
    @PutMapping("/withdraw/{id}/audit")
    @RequirePermission("fund:withdraw:audit")
    public R<Void> auditWithdraw(
            @Parameter(description = "提现ID") @PathVariable Long id,
            @Valid @RequestBody WithdrawAuditDTO dto) {
        fundService.auditWithdraw(id, dto);
        return R.ok();
    }

    @Operation(summary = "确认打款", description = "财务线下转账后更新为已打款状态")
    @PutMapping("/withdraw/{id}/confirm")
    @RequirePermission("fund:withdraw:audit")
    public R<Void> confirmWithdraw(
            @Parameter(description = "提现ID") @PathVariable Long id) {
        fundService.confirmWithdraw(id);
        return R.ok();
    }

    // ==================== 5. 资金流水 ====================

    @Operation(summary = "资金流水分页", description = "查询平台或店铺维度的资金流水")
    @GetMapping("/flow/page")
    @RequirePermission("fund:flow:list")
    public R<Page<FundFlowVO>> pageFundFlow(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "商家ID（空=平台流水）") @RequestParam(required = false) Long merchantId,
            @Parameter(description = "流水类型") @RequestParam(required = false) String flowType,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(fundService.pageFundFlow(pageNum, pageSize, merchantId, flowType, startDate, endDate));
    }

    // ==================== 6. 对账报表 ====================

    @Operation(summary = "每日汇总", description = "平台每日营收、佣金、提现、退款汇总")
    @GetMapping("/report/daily")
    @RequirePermission("fund:report:export")
    public R<Page<DailyReportVO>> pageDailyReport(
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(fundService.pageDailyReport(startDate, endDate, pageNum, pageSize));
    }

    @Operation(summary = "单店铺对账", description = "查看指定商家的周期对账汇总")
    @GetMapping("/report/merchant")
    @RequirePermission("fund:report:export")
    public R<MerchantReportVO> merchantReport(
            @Parameter(description = "商家ID") @RequestParam Long merchantId,
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(fundService.getMerchantReport(merchantId, startDate, endDate));
    }
}

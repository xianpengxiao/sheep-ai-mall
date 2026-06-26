package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.MerchantSettlementAccountBindDTO;
import com.xs.sheepaimall.dto.MerchantWithdrawApplyDTO;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.FundService;
import com.xs.sheepaimall.service.MerchantService;
import com.xs.sheepaimall.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;

@Tag(name = "商家-资金", description = "商家后台资金管理：结算账户、提现、流水、分佣、对账")
@Validated
@RestController
@RequestMapping("/api/merchant/fund")
public class MerchantFundController {

    @Autowired
    private FundService fundService;

    @Autowired
    private MerchantService merchantService;

    /** 获取当前商家 ID */
    private Long getMerchantId() {
        return merchantService.getCurrentMerchantId();
    }

    // ==================== 1. 结算账户 ====================

    @Operation(summary = "结算账户查询", description = "查询自己的结算账户信息（脱敏展示）")
    @GetMapping("/account")
    @RequirePermission("merchant:fund:account")
    public R<SettlementAccountVO> getAccount() {
        return R.ok(fundService.getMySettlementAccount(getMerchantId()));
    }

    @Operation(summary = "绑定/修改结算账户", description = "绑定银行卡、支付宝或微信结算账户")
    @PutMapping("/account")
    @RequirePermission("merchant:fund:account")
    public R<Void> bindAccount(@Valid @RequestBody MerchantSettlementAccountBindDTO dto) {
        fundService.bindSettlementAccount(getMerchantId(), dto);
        return R.ok();
    }

    @Operation(summary = "查询余额", description = "查询当前可提现余额")
    @GetMapping("/balance")
    @RequirePermission("merchant:fund:account")
    public R<BigDecimal> balance() {
        return R.ok(fundService.getCurrentBalance(getMerchantId()));
    }

    // ==================== 2. 提现申请 ====================

    @Operation(summary = "提交提现申请", description = "提交提现申请，平台审核后打款")
    @PostMapping("/withdraw")
    @RequirePermission("merchant:fund:withdraw")
    public R<Void> applyWithdraw(@Valid @RequestBody MerchantWithdrawApplyDTO dto) {
        fundService.applyWithdraw(getMerchantId(), dto.getAmount());
        return R.ok();
    }

    @Operation(summary = "提现记录分页", description = "查看自己的提现申请记录")
    @GetMapping("/withdraw/page")
    @RequirePermission("merchant:fund:withdraw")
    public R<Page<WithdrawVO>> pageWithdraw(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(fundService.pageMyWithdraw(getMerchantId(), pageNum, pageSize));
    }

    // ==================== 3. 资金流水 ====================

    @Operation(summary = "资金流水分页", description = "查看自己的资金流水记录")
    @GetMapping("/flow/page")
    @RequirePermission("merchant:fund:flow")
    public R<Page<FundFlowVO>> pageFundFlow(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "流水类型") @RequestParam(required = false) String flowType,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(fundService.pageMyFundFlow(getMerchantId(), pageNum, pageSize, flowType, startDate, endDate));
    }

    // ==================== 4. 分佣明细 ====================

    @Operation(summary = "分佣记录分页", description = "查看自己的订单分佣明细")
    @GetMapping("/commission/page")
    @RequirePermission("merchant:fund:commission")
    public R<Page<OrderCommissionLogVO>> pageCommissionLog(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(fundService.pageMyCommissionLog(getMerchantId(), pageNum, pageSize, startDate, endDate));
    }

    // ==================== 5. 对账报表 ====================

    @Operation(summary = "对账汇总", description = "查看指定时间段内的经营对账汇总")
    @GetMapping("/report")
    @RequirePermission("merchant:fund:report")
    public R<MerchantReportVO> report(
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(fundService.getMyReport(getMerchantId(), startDate, endDate));
    }
}

package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.dto.CommissionConfigSaveDTO;
import com.xs.sheepaimall.dto.MerchantSettlementAccountBindDTO;
import com.xs.sheepaimall.dto.SettlementAccountEditDTO;
import com.xs.sheepaimall.dto.WithdrawAuditDTO;
import com.xs.sheepaimall.entity.FundFlow;
import com.xs.sheepaimall.vo.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 资金管理 Service */
public interface FundService extends IService<FundFlow> {

    // ===== 1. 结算账户管理 =====

    /** 分页查询商家结算账户 */
    Page<SettlementAccountVO> pageSettlementAccount(int pageNum, int pageSize, String keyword);

    /** 查询单个商家结算账户 */
    SettlementAccountVO getSettlementAccount(Long merchantId);

    /** 编辑商家结算账户（费率/周期/提现权限） */
    void updateSettlementAccount(Long merchantId, SettlementAccountEditDTO dto);

    // ===== 2. 佣金规则配置 =====

    /** 分页查询佣金规则 */
    Page<CommissionConfigVO> pageCommissionConfig(int pageNum, int pageSize, Long categoryId);

    /** 新增佣金规则 */
    void saveCommissionConfig(CommissionConfigSaveDTO dto);

    /** 编辑佣金规则 */
    void updateCommissionConfig(Long id, CommissionConfigSaveDTO dto);

    /** 启用/禁用佣金规则 */
    void toggleCommissionConfig(Long id, Integer status);

    // ===== 3. 分佣明细 =====

    /** 分页查询订单分佣记录 */
    Page<OrderCommissionLogVO> pageCommissionLog(int pageNum, int pageSize, String orderNo,
                                                  Long merchantId, LocalDate startDate, LocalDate endDate);

    // ===== 4. 提现审核 =====

    /** 分页查询提现申请 */
    Page<WithdrawVO> pageWithdraw(int pageNum, int pageSize, Integer status, Long merchantId);

    /** 审核提现申请（通过/驳回） */
    void auditWithdraw(Long withdrawId, WithdrawAuditDTO dto);

    /** 确认打款 */
    void confirmWithdraw(Long withdrawId);

    // ===== 5. 资金流水 =====

    /** 分页查询资金流水 */
    Page<FundFlowVO> pageFundFlow(int pageNum, int pageSize, Long merchantId,
                                   String flowType, LocalDate startDate, LocalDate endDate);

    // ===== 6. 对账报表 =====

    /** 每日汇总 */
    Page<DailyReportVO> pageDailyReport(LocalDate startDate, LocalDate endDate, int pageNum, int pageSize);

    /** 单店铺对账 */
    MerchantReportVO getMerchantReport(Long merchantId, LocalDate startDate, LocalDate endDate);

    // ===== 7. 分佣内部方法 =====

    /** 订单确认收货后计算佣金（由 OrderServiceImpl 调用） */
    void settleOrderCommission(Long orderId);

    // ===== 8. 商家端方法（merchantId 从 UserContext 获取） =====

    /** 查询商家自己的结算账户（脱敏） */
    SettlementAccountVO getMySettlementAccount(Long merchantId);

    /** 商家绑定/修改结算账户 */
    void bindSettlementAccount(Long merchantId, MerchantSettlementAccountBindDTO dto);

    /** 查询当前可提现余额 */
    BigDecimal getCurrentBalance(Long merchantId);

    /** 商家提交提现申请 */
    void applyWithdraw(Long merchantId, BigDecimal amount);

    /** 商家查看自己的提现记录 */
    Page<WithdrawVO> pageMyWithdraw(Long merchantId, int pageNum, int pageSize);

    /** 商家查看自己的资金流水 */
    Page<FundFlowVO> pageMyFundFlow(Long merchantId, int pageNum, int pageSize,
                                     String flowType, LocalDate startDate, LocalDate endDate);

    /** 商家查看自己的分佣记录 */
    Page<OrderCommissionLogVO> pageMyCommissionLog(Long merchantId, int pageNum, int pageSize,
                                                    LocalDate startDate, LocalDate endDate);

    /** 商家查看自己的对账汇总 */
    MerchantReportVO getMyReport(Long merchantId, LocalDate startDate, LocalDate endDate);
}

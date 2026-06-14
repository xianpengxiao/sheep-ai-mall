package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.ResultCode;
import com.xs.sheepaimall.dto.CommissionConfigSaveDTO;
import com.xs.sheepaimall.dto.MerchantSettlementAccountBindDTO;
import com.xs.sheepaimall.dto.SettlementAccountEditDTO;
import com.xs.sheepaimall.dto.WithdrawAuditDTO;
import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.mapper.*;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.CategoryService;
import com.xs.sheepaimall.service.FundService;
import com.xs.sheepaimall.service.MerchantService;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.vo.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FundServiceImpl extends ServiceImpl<FundFlowMapper, FundFlow> implements FundService {

    // 提现手续费率（暂定固定 0.5%）
    private static final BigDecimal WITHDRAW_FEE_RATE = new BigDecimal("0.005");

    @Resource
    private MerchantSettlementAccountMapper settlementAccountMapper;

    @Resource
    private CommissionConfigMapper commissionConfigMapper;

    @Resource
    private OrderCommissionLogMapper orderCommissionLogMapper;

    @Resource
    private MerchantWithdrawMapper merchantWithdrawMapper;

    @Resource
    private OperLogMapper operLogMapper;

    @Resource
    private MerchantService merchantService;

    @Resource
    private SpuService spuService;

    @Resource
    private CategoryService categoryService;

    @Resource
    private OrderItemMapper orderItemMapper;

    @Resource
    private OrderInfoMapper orderInfoMapper;

    // ==================== 工具方法 ====================

    /** 生成流水号 */
    private String genFlowNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    /** 记录操作日志 */
    private void logOperation(String operation, String targetType, Long targetId, String detail) {
        Long userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        OperLog log = new OperLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setOperation(operation);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        operLogMapper.insert(log);
    }

    /** 银行卡号脱敏：622202******1234 */
    private String maskBankCard(String card) {
        if (StrUtil.isBlank(card) || card.length() < 8) return card;
        return card.substring(0, 6) + "******" + card.substring(card.length() - 4);
    }

    /** 账号脱敏 */
    private String maskAccount(String type, String account) {
        if (StrUtil.isBlank(account)) return account;
        return switch (type != null ? type : "") {
            case "BANK" -> maskBankCard(account);
            case "ALIPAY", "WECHAT" ->
                    account.length() > 4 ? "***" + account.substring(account.length() - 4) : account;
            default -> account;
        };
    }

    // ==================== 1. 结算账户管理 ====================

    @Override
    public Page<SettlementAccountVO> pageSettlementAccount(int pageNum, int pageSize, String keyword) {
        Page<MerchantSettlementAccount> page = settlementAccountMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MerchantSettlementAccount>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .inSql(MerchantSettlementAccount::getMerchantId,
                                        "SELECT id FROM merchant WHERE shop_name LIKE '%" + keyword + "%'")
                        )
                        .orderByDesc(MerchantSettlementAccount::getCreateTime));

        Page<SettlementAccountVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(page.getTotal());

        List<SettlementAccountVO> voList = page.getRecords().stream().map(acc -> {
            SettlementAccountVO vo = new SettlementAccountVO();
            BeanUtil.copyProperties(acc, vo);
            // 脱敏
            vo.setCardNumber(maskBankCard(acc.getCardNumber()));
            vo.setAlipayAccount(maskAccount("ALIPAY", acc.getAlipayAccount()));
            vo.setWechatAccount(maskAccount("WECHAT", acc.getWechatAccount()));
            // 店铺名称
            Merchant merchant = merchantService.getById(acc.getMerchantId());
            if (merchant != null) vo.setShopName(merchant.getShopName());
            return vo;
        }).collect(Collectors.toList());
        result.setRecords(voList);
        return result;
    }

    @Override
    public SettlementAccountVO getSettlementAccount(Long merchantId) {
        MerchantSettlementAccount acc = settlementAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlementAccount>()
                        .eq(MerchantSettlementAccount::getMerchantId, merchantId));
        if (acc == null) throw new BizException("该商家暂无结算账户");

        SettlementAccountVO vo = new SettlementAccountVO();
        BeanUtil.copyProperties(acc, vo);
        vo.setCardNumber(maskBankCard(acc.getCardNumber()));
        vo.setAlipayAccount(maskAccount("ALIPAY", acc.getAlipayAccount()));
        vo.setWechatAccount(maskAccount("WECHAT", acc.getWechatAccount()));

        Merchant merchant = merchantService.getById(merchantId);
        if (merchant != null) vo.setShopName(merchant.getShopName());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSettlementAccount(Long merchantId, SettlementAccountEditDTO dto) {
        MerchantSettlementAccount acc = settlementAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlementAccount>()
                        .eq(MerchantSettlementAccount::getMerchantId, merchantId));
        if (acc == null) throw new BizException("该商家暂无结算账户");

        StringBuilder detail = new StringBuilder();
        if (dto.getSettlementRate() != null) {
            detail.append("费率:").append(acc.getSettlementRate()).append("%→").append(dto.getSettlementRate()).append("%;");
            acc.setSettlementRate(dto.getSettlementRate());
        }
        if (dto.getSettlementCycle() != null) {
            detail.append("周期:").append(acc.getSettlementCycle()).append("→").append(dto.getSettlementCycle()).append(";");
            acc.setSettlementCycle(dto.getSettlementCycle());
        }
        if (dto.getWithdrawEnabled() != null) {
            detail.append("提现权限:").append(acc.getWithdrawEnabled() == 1 ? "开启" : "关闭").append(";");
            acc.setWithdrawEnabled(dto.getWithdrawEnabled());
        }
        settlementAccountMapper.updateById(acc);

        logOperation("编辑结算账户", "MerchantSettlementAccount", merchantId, detail.toString());
    }

    // ==================== 2. 佣金规则配置 ====================

    @Override
    public Page<CommissionConfigVO> pageCommissionConfig(int pageNum, int pageSize, Long categoryId) {
        Page<CommissionConfig> page = commissionConfigMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CommissionConfig>()
                        .eq(categoryId != null, CommissionConfig::getCategoryId, categoryId)
                        .orderByDesc(CommissionConfig::getCreateTime));

        Page<CommissionConfigVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toCommissionConfigVO).collect(Collectors.toList()));
        return result;
    }

    private CommissionConfigVO toCommissionConfigVO(CommissionConfig config) {
        CommissionConfigVO vo = new CommissionConfigVO();
        BeanUtil.copyProperties(config, vo);
        Category cat = categoryService.getById(config.getCategoryId());
        if (cat != null) vo.setCategoryName(cat.getName());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCommissionConfig(CommissionConfigSaveDTO dto) {
        Long exists = commissionConfigMapper.selectCount(
                new LambdaQueryWrapper<CommissionConfig>()
                        .eq(CommissionConfig::getCategoryId, dto.getCategoryId()));
        if (exists > 0) throw new BizException("该分类已存在佣金规则");

        CommissionConfig config = new CommissionConfig();
        BeanUtil.copyProperties(dto, config);
        if (config.getStatus() == null) config.setStatus(1);
        commissionConfigMapper.insert(config);
        logOperation("新增佣金规则", "CommissionConfig", config.getId(),
                "分类ID:" + dto.getCategoryId() + ", 费率:" + dto.getCommissionRate() + "%");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCommissionConfig(Long id, CommissionConfigSaveDTO dto) {
        CommissionConfig config = commissionConfigMapper.selectById(id);
        if (config == null) throw new BizException(ResultCode.NOT_FOUND.getCode(), "佣金规则不存在");
        BeanUtil.copyProperties(dto, config);
        commissionConfigMapper.updateById(config);
        logOperation("编辑佣金规则", "CommissionConfig", id,
                "分类ID:" + dto.getCategoryId() + ", 费率:" + dto.getCommissionRate() + "%");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleCommissionConfig(Long id, Integer status) {
        CommissionConfig config = commissionConfigMapper.selectById(id);
        if (config == null) throw new BizException(ResultCode.NOT_FOUND.getCode(), "佣金规则不存在");
        config.setStatus(status);
        commissionConfigMapper.updateById(config);
        logOperation(status == 1 ? "启用佣金规则" : "禁用佣金规则", "CommissionConfig", id, "");
    }

    // ==================== 3. 分佣明细 ====================

    @Override
    public Page<OrderCommissionLogVO> pageCommissionLog(int pageNum, int pageSize, String orderNo,
                                                         Long merchantId, LocalDate startDate, LocalDate endDate) {
        Page<OrderCommissionLog> page = orderCommissionLogMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OrderCommissionLog>()
                        .eq(merchantId != null, OrderCommissionLog::getMerchantId, merchantId)
                        .eq(StrUtil.isNotBlank(orderNo), OrderCommissionLog::getOrderNo, orderNo)
                        .ge(startDate != null, OrderCommissionLog::getCreateTime, startDate != null ? startDate.atStartOfDay() : null)
                        .lt(endDate != null, OrderCommissionLog::getCreateTime, endDate != null ? endDate.plusDays(1).atStartOfDay() : null)
                        .orderByDesc(OrderCommissionLog::getCreateTime));

        Page<OrderCommissionLogVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toOrderCommissionLogVO).collect(Collectors.toList()));
        return result;
    }

    private OrderCommissionLogVO toOrderCommissionLogVO(OrderCommissionLog log) {
        OrderCommissionLogVO vo = new OrderCommissionLogVO();
        BeanUtil.copyProperties(log, vo);
        // SPU 名称
        Spu spu = spuService.getById(log.getSpuId());
        if (spu != null) vo.setSpuName(spu.getName());
        // 分类名称
        Category cat = categoryService.getById(log.getCategoryId());
        if (cat != null) vo.setCategoryName(cat.getName());
        // 店铺名称
        Merchant merchant = merchantService.getById(log.getMerchantId());
        if (merchant != null) vo.setShopName(merchant.getShopName());
        return vo;
    }

    // ==================== 4. 提现审核 ====================

    @Override
    public Page<WithdrawVO> pageWithdraw(int pageNum, int pageSize, Integer status, Long merchantId) {
        Page<MerchantWithdraw> page = merchantWithdrawMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MerchantWithdraw>()
                        .eq(status != null, MerchantWithdraw::getStatus, status)
                        .eq(merchantId != null, MerchantWithdraw::getMerchantId, merchantId)
                        .orderByDesc(MerchantWithdraw::getCreateTime));

        Map<String, String> statusMap = Map.of(
                "0", "待审核", "1", "待打款", "2", "已打款", "3", "已驳回");

        Page<WithdrawVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords().stream().map(w -> {
            WithdrawVO vo = new WithdrawVO();
            BeanUtil.copyProperties(w, vo);
            vo.setStatusText(statusMap.getOrDefault(String.valueOf(w.getStatus()), "未知"));
            Merchant merchant = merchantService.getById(w.getMerchantId());
            if (merchant != null) vo.setShopName(merchant.getShopName());
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditWithdraw(Long withdrawId, WithdrawAuditDTO dto) {
        MerchantWithdraw w = merchantWithdrawMapper.selectById(withdrawId);
        if (w == null) throw new BizException(ResultCode.NOT_FOUND.getCode(), "提现申请不存在");
        if (w.getStatus() != 0) throw new BizException("该提现申请已处理，请勿重复操作");

        Integer newStatus = dto.getStatus();
        if (newStatus == 1) {
            // 审核通过前校验余额充足
            BigDecimal balance = getCurrentBalance(w.getMerchantId());
            if (balance.compareTo(w.getAmount()) < 0) {
                throw new BizException("商家余额不足，无法通过审核");
            }
            // 审核通过 → 待打款
            w.setStatus(1);
            w.setAuditUserId(UserContext.getUserId());
            w.setAuditTime(LocalDateTime.now());
            logOperation("提现审核通过", "MerchantWithdraw", withdrawId,
                    "金额:" + w.getAmount() + ", 商家ID:" + w.getMerchantId());
        } else if (newStatus == 3) {
            // 审核驳回 → 退回余额
            if (StrUtil.isBlank(dto.getRejectReason())) {
                throw new BizException("驳回时必须填写原因");
            }
            w.setStatus(3);
            w.setRejectReason(dto.getRejectReason());
            w.setAuditUserId(UserContext.getUserId());
            w.setAuditTime(LocalDateTime.now());
            // 驳回回冲：记录资金流水
            createFundFlow(w.getMerchantId(), "withdraw_refund", "INCOME",
                    w.getActualAmount(), w.getId(), "withdraw",
                    "提现驳回，金额退回。提现单号:" + w.getWithdrawNo());
            logOperation("提现驳回", "MerchantWithdraw", withdrawId,
                    "原因:" + dto.getRejectReason());
        } else {
            throw new BizException("审核状态不正确");
        }
        merchantWithdrawMapper.updateById(w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmWithdraw(Long withdrawId) {
        MerchantWithdraw w = merchantWithdrawMapper.selectById(withdrawId);
        if (w == null) throw new BizException(ResultCode.NOT_FOUND.getCode(), "提现申请不存在");
        if (w.getStatus() != 1) throw new BizException("该提现申请状态不是待打款");

        w.setStatus(2);
        w.setFinishTime(LocalDateTime.now());
        merchantWithdrawMapper.updateById(w);

        // 记录资金流水：提现支出
        createFundFlow(w.getMerchantId(), "withdraw_expense", "EXPENSE",
                w.getActualAmount(), w.getId(), "withdraw",
                "提现打款完成。提现单号:" + w.getWithdrawNo());

        logOperation("提现确认打款", "MerchantWithdraw", withdrawId, "金额:" + w.getAmount());
    }

    // ==================== 5. 资金流水 ====================

    @Override
    public Page<FundFlowVO> pageFundFlow(int pageNum, int pageSize, Long merchantId,
                                          String flowType, LocalDate startDate, LocalDate endDate) {
        Page<FundFlow> page = this.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FundFlow>()
                        .eq(merchantId != null, FundFlow::getMerchantId, merchantId)
                        .eq(StrUtil.isNotBlank(flowType), FundFlow::getFlowType, flowType)
                        .ge(startDate != null, FundFlow::getCreateTime, startDate != null ? startDate.atStartOfDay() : null)
                        .lt(endDate != null, FundFlow::getCreateTime, endDate != null ? endDate.plusDays(1).atStartOfDay() : null)
                        .orderByDesc(FundFlow::getCreateTime));

        Map<String, String> typeMap = Map.ofEntries(
                Map.entry("commission_income", "佣金收入"),
                Map.entry("subsidy", "补贴支出"),
                Map.entry("refund_deduct", "退款扣回"),
                Map.entry("withdraw_expense", "提现支出"),
                Map.entry("order_income", "订单入账"),
                Map.entry("withdraw_refund", "提现退回")
        );

        Page<FundFlowVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords().stream().map(f -> {
            FundFlowVO vo = new FundFlowVO();
            BeanUtil.copyProperties(f, vo);
            vo.setFlowTypeText(typeMap.getOrDefault(f.getFlowType(), f.getFlowType()));
            if (f.getMerchantId() != null) {
                Merchant merchant = merchantService.getById(f.getMerchantId());
                if (merchant != null) vo.setShopName(merchant.getShopName());
            }
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    // ==================== 6. 对账报表 ====================

    @Override
    public Page<DailyReportVO> pageDailyReport(LocalDate startDate, LocalDate endDate, int pageNum, int pageSize) {
        List<DailyReportVO> list = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            LocalDate day = cursor;
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();

            // 当日佣金收入
            BigDecimal commission = sumFlow("commission_income", "INCOME", dayStart, dayEnd);
            // 当日提现支出
            BigDecimal withdraw = sumFlow("withdraw_expense", "EXPENSE", dayStart, dayEnd);
            // 当日退款
            BigDecimal refund = sumFlow("refund_deduct", "EXPENSE", dayStart, dayEnd);

            DailyReportVO vo = new DailyReportVO();
            vo.setStatDate(day);
            vo.setTotalCommission(commission);
            vo.setTotalWithdraw(withdraw);
            vo.setTotalRefund(refund);
            vo.setNetIncome(commission.subtract(withdraw).subtract(refund));
            list.add(vo);
            cursor = cursor.plusDays(1);
        }

        // 简单分页
        int total = list.size();
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<DailyReportVO> subList = from < total ? list.subList(from, to) : new ArrayList<>();

        Page<DailyReportVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(total);
        result.setRecords(subList);
        return result;
    }

    @Override
    public MerchantReportVO getMerchantReport(Long merchantId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;

        // 订单入账 = 该商家已完成订单的金额（通过分佣记录汇总）
        BigDecimal orderIncome = orderCommissionLogMapper.selectList(
                        new LambdaQueryWrapper<OrderCommissionLog>()
                                .eq(OrderCommissionLog::getMerchantId, merchantId)
                                .ge(OrderCommissionLog::getCreateTime, start)
                                .lt(OrderCommissionLog::getCreateTime, end))
                .stream().map(OrderCommissionLog::getMerchantIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 佣金扣除
        BigDecimal commissionDeduct = orderCommissionLogMapper.selectList(
                        new LambdaQueryWrapper<OrderCommissionLog>()
                                .eq(OrderCommissionLog::getMerchantId, merchantId)
                                .ge(OrderCommissionLog::getCreateTime, start)
                                .lt(OrderCommissionLog::getCreateTime, end))
                .stream().map(OrderCommissionLog::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 提现支出
        BigDecimal withdrawAmount = sumMerchantFlow(merchantId, "withdraw_expense", "EXPENSE", start, end);
        // 退款回冲
        BigDecimal refundAmount = sumMerchantFlow(merchantId, "withdraw_refund", "INCOME", start, end);

        MerchantReportVO vo = new MerchantReportVO();
        vo.setMerchantId(merchantId);
        Merchant merchant = merchantService.getById(merchantId);
        vo.setShopName(merchant != null ? merchant.getShopName() : "");
        vo.setStartDate(startDate);
        vo.setEndDate(endDate);
        vo.setOrderIncome(orderIncome);
        vo.setCommissionDeduct(commissionDeduct);
        vo.setWithdrawAmount(withdrawAmount);
        vo.setRefundAmount(refundAmount);
        vo.setAvailableBalance(orderIncome.subtract(commissionDeduct).subtract(withdrawAmount).add(refundAmount));
        return vo;
    }

    /** 汇总平台流水 */
    private BigDecimal sumFlow(String flowType, String direction, LocalDateTime start, LocalDateTime end) {
        List<FundFlow> list = this.list(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getFlowType, flowType)
                .eq(FundFlow::getDirection, direction)
                .ge(FundFlow::getCreateTime, start)
                .lt(FundFlow::getCreateTime, end));
        return list.stream().map(FundFlow::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 汇总商家流水 */
    private BigDecimal sumMerchantFlow(Long merchantId, String flowType, String direction,
                                        LocalDateTime start, LocalDateTime end) {
        List<FundFlow> list = this.list(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getMerchantId, merchantId)
                .eq(FundFlow::getFlowType, flowType)
                .eq(FundFlow::getDirection, direction)
                .ge(FundFlow::getCreateTime, start)
                .lt(FundFlow::getCreateTime, end));
        return list.stream().map(FundFlow::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 7. 内部方法 ====================

    /** 创建资金流水（商家维度记录余额快照，平台维度跳过） */
    private void createFundFlow(Long merchantId, String flowType, String direction,
                                 BigDecimal amount, Long bizId, String bizType, String remark) {
        FundFlow flow = new FundFlow();
        flow.setFlowNo(genFlowNo("F"));
        flow.setMerchantId(merchantId);
        flow.setFlowType(flowType);
        flow.setDirection(direction);
        flow.setAmount(amount);
        flow.setBizType(bizType);
        flow.setBizId(bizId);
        flow.setRemark(remark);
        // 商家维度流水记录操作前后余额快照
        if (merchantId != null) {
            BigDecimal current = getCurrentBalance(merchantId);
            flow.setBalanceBefore(current);
            flow.setBalanceAfter("INCOME".equals(direction) ? current.add(amount) : current.subtract(amount));
        }
        this.save(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleOrderCommission(Long orderId) {
        // 由 OrderServiceImpl.confirmReceipt() 调用
        // 幂等：查询该订单是否已结算
        List<OrderCommissionLog> existing = orderCommissionLogMapper.selectList(
                new LambdaQueryWrapper<OrderCommissionLog>()
                        .eq(OrderCommissionLog::getOrderId, orderId));
        if (existing.stream().anyMatch(e -> e.getStatus() == 1)) {
            log.warn("订单 {} 已结算分佣，跳过", orderId);
            return;
        }

        // 查询订单信息
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单 {} 不存在，跳过分佣", orderId);
            return;
        }

        // 查询订单明细
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        if (items.isEmpty()) {
            log.warn("订单 {} 无明细，跳过分佣", orderId);
            return;
        }

        for (OrderItem item : items) {
            // 通过 item.spuId → spu 获取分类ID和商家ID
            Spu spu = spuService.getById(item.getSpuId());
            if (spu == null) continue;

            Long categoryId = spu.getCategoryId();
            Long merchantId = spu.getMerchantId();

            // 查询该分类的佣金规则（取启用的、最新的）
            CommissionConfig config = commissionConfigMapper.selectOne(
                    new LambdaQueryWrapper<CommissionConfig>()
                            .eq(CommissionConfig::getCategoryId, categoryId)
                            .eq(CommissionConfig::getStatus, 1)
                            .orderByDesc(CommissionConfig::getCreateTime)
                            .last("LIMIT 1"));

            BigDecimal rate = config != null ? config.getCommissionRate() : BigDecimal.ZERO;
            // 佣金 = 商品实付 × 佣金比例 / 100
            BigDecimal commission = item.getTotalPrice().multiply(rate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal merchantIncome = item.getTotalPrice().subtract(commission);

            // 插入分佣记录
            OrderCommissionLog log = new OrderCommissionLog();
            log.setOrderId(orderId);
            log.setOrderItemId(item.getId());
            log.setOrderNo(order.getOrderNo());
            log.setSpuId(item.getSpuId());
            log.setCategoryId(categoryId);
            log.setMerchantId(merchantId);
            log.setTotalPrice(item.getTotalPrice());
            log.setCommissionRate(rate);
            log.setCommissionAmount(commission);
            log.setMerchantIncome(merchantIncome);
            log.setStatus(1); // 已结算
            log.setSettleTime(LocalDateTime.now());
            orderCommissionLogMapper.insert(log);

            // 记录资金流水（平台维度：佣金收入）
            createFundFlow(null, "commission_income", "INCOME",
                    commission, log.getId(), "order",
                    "订单分佣。订单号:" + order.getOrderNo() + ", 商品:" + spu.getName());

            // 记录资金流水（商家维度：订单入账）
            createFundFlow(merchantId, "order_income", "INCOME",
                    merchantIncome, log.getId(), "order",
                    "订单入账。订单号:" + order.getOrderNo() + ", 扣除佣金:" + commission);
        }

        log.info("订单 {} 分佣结算完成，共 {} 条明细", orderId, items.size());
    }

    // ==================== 8. 商家端方法 ====================

    @Override
    public SettlementAccountVO getMySettlementAccount(Long merchantId) {
        MerchantSettlementAccount acc = settlementAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlementAccount>()
                        .eq(MerchantSettlementAccount::getMerchantId, merchantId));
        if (acc == null) throw new BizException("您还未绑定结算账户");

        SettlementAccountVO vo = new SettlementAccountVO();
        BeanUtil.copyProperties(acc, vo);
        vo.setCardNumber(maskBankCard(acc.getCardNumber()));
        vo.setAlipayAccount(maskAccount("ALIPAY", acc.getAlipayAccount()));
        vo.setWechatAccount(maskAccount("WECHAT", acc.getWechatAccount()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindSettlementAccount(Long merchantId, MerchantSettlementAccountBindDTO dto) {
        MerchantSettlementAccount acc = settlementAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlementAccount>()
                        .eq(MerchantSettlementAccount::getMerchantId, merchantId));

        boolean isNew = acc == null;
        if (isNew) {
            acc = new MerchantSettlementAccount();
            acc.setMerchantId(merchantId);
            acc.setSettlementRate(BigDecimal.ZERO);
            acc.setSettlementCycle("T+1");
            acc.setWithdrawEnabled(1);
            acc.setBalance(BigDecimal.ZERO);
        }

        acc.setAccountType(dto.getAccountType());
        acc.setAccountHolder(dto.getAccountHolder());
        acc.setCardNumber(dto.getCardNumber());
        acc.setAlipayAccount(dto.getAlipayAccount());
        acc.setWechatAccount(dto.getWechatAccount());
        acc.setBankName(dto.getBankName());
        acc.setBranchBankName(dto.getBranchBankName());
        acc.setBindingStatus(1);

        if (isNew) {
            settlementAccountMapper.insert(acc);
        } else {
            settlementAccountMapper.updateById(acc);
        }
        logOperation("绑定结算账户", "MerchantSettlementAccount", merchantId,
                "类型:" + dto.getAccountType() + ", 开户人:" + dto.getAccountHolder());
    }

    @Override
    public BigDecimal getCurrentBalance(Long merchantId) {
        // 从 fund_flow 实时计算：所有 INCOME 之和 - 所有 EXPENSE 之和
        List<FundFlow> flows = this.list(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getMerchantId, merchantId)
                .select(FundFlow::getDirection, FundFlow::getAmount));
        return flows.stream()
                .map(f -> "INCOME".equals(f.getDirection()) ? f.getAmount() : f.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyWithdraw(Long merchantId, BigDecimal amount) {
        // 1. 检查结算账户
        MerchantSettlementAccount acc = settlementAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantSettlementAccount>()
                        .eq(MerchantSettlementAccount::getMerchantId, merchantId));
        if (acc == null || acc.getBindingStatus() != 1) {
            throw new BizException("请先绑定结算账户");
        }
        if (acc.getWithdrawEnabled() != 1) {
            throw new BizException("提现功能已关闭，请联系平台客服");
        }

        // 2. 检查余额
        BigDecimal balance = getCurrentBalance(merchantId);
        if (balance.compareTo(amount) < 0) {
            throw new BizException("可提现余额不足，当前余额:" + balance + "元");
        }

        // 3. 防止重复提现（有待审核/待打款的记录时不可再提交）
        Long pending = merchantWithdrawMapper.selectCount(
                new LambdaQueryWrapper<MerchantWithdraw>()
                        .eq(MerchantWithdraw::getMerchantId, merchantId)
                        .in(MerchantWithdraw::getStatus, 0, 1));
        if (pending > 0) {
            throw new BizException("您有待处理的提现申请，请等待处理完成后再提交");
        }

        // 4. 计算手续费和实际到账
        BigDecimal fee = amount.multiply(WITHDRAW_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal actualAmount = amount.subtract(fee);

        // 5. 脱敏账户信息
        String accountInfo = switch (acc.getAccountType()) {
            case "BANK" -> maskBankCard(acc.getCardNumber());
            case "ALIPAY" -> maskAccount("ALIPAY", acc.getAlipayAccount());
            case "WECHAT" -> maskAccount("WECHAT", acc.getWechatAccount());
            default -> "";
        };

        // 6. 创建提现申请
        MerchantWithdraw w = new MerchantWithdraw();
        w.setMerchantId(merchantId);
        w.setWithdrawNo(genFlowNo("W"));
        w.setAmount(amount);
        w.setFee(fee);
        w.setActualAmount(actualAmount);
        w.setAccountType(acc.getAccountType());
        w.setAccountInfo(accountInfo);
        w.setStatus(0);
        merchantWithdrawMapper.insert(w);
    }

    @Override
    public Page<WithdrawVO> pageMyWithdraw(Long merchantId, int pageNum, int pageSize) {
        // 复用现有 pageWithdraw 逻辑，按商家过滤
        return pageWithdraw(pageNum, pageSize, null, merchantId);
    }

    @Override
    public Page<FundFlowVO> pageMyFundFlow(Long merchantId, int pageNum, int pageSize,
                                            String flowType, LocalDate startDate, LocalDate endDate) {
        return pageFundFlow(pageNum, pageSize, merchantId, flowType, startDate, endDate);
    }

    @Override
    public Page<OrderCommissionLogVO> pageMyCommissionLog(Long merchantId, int pageNum, int pageSize,
                                                           LocalDate startDate, LocalDate endDate) {
        return pageCommissionLog(pageNum, pageSize, null, merchantId, startDate, endDate);
    }

    @Override
    public MerchantReportVO getMyReport(Long merchantId, LocalDate startDate, LocalDate endDate) {
        return getMerchantReport(merchantId, startDate, endDate);
    }
}

package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.ResultCode;
import com.xs.sheepaimall.config.WechatPayProperties;
import com.xs.sheepaimall.entity.OrderInfo;
import com.xs.sheepaimall.entity.PaymentRecord;
import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.feign.AuthFeignClient;
import com.xs.sheepaimall.mapper.PaymentRecordMapper;
import com.xs.sheepaimall.service.OrderService;
import com.xs.sheepaimall.service.PaymentService;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付 Service —— 微信支付 API v3 JSAPI 实现。
 *
 * 流程：
 * 1. createJsapiPayment → 微信统一下单 → 保存 PaymentRecord(PENDING) → 返回前端拉起参数
 * 2. handleNotify → 验签+解密 → 更新 OrderInfo(payAmount+status) + PaymentRecord(SUCCESS)
 */
@Service
public class PaymentServiceImpl extends ServiceImpl<PaymentRecordMapper, PaymentRecord> implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Autowired(required = false)
    private RSAAutoCertificateConfig wechatPayConfig;

    @Autowired(required = false)
    private NotificationConfig notificationConfig;

    @Autowired(required = false)
    private JsapiService jsapiService;

    @Autowired
    private WechatPayProperties wechatPayProperties;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AuthFeignClient authFeignClient;

    // ==================== 创建 JSAPI 支付 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createJsapiPayment(Long orderId) {
        // 1. 查询订单
        OrderInfo order = orderService.getById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        // 仅待支付状态的订单可发起支付
        if (order.getStatus() != null && order.getStatus() != 0) {
            throw new BizException("订单状态不允许支付，当前状态：" + order.getStatus());
        }

        // 2. 构建微信预支付请求
        PrepayRequest request = new PrepayRequest();
        request.setAppid(wechatPayProperties.getMerchantId()); // TODO: 替换为实际 appId
        request.setMchid(wechatPayProperties.getMerchantId());
        request.setDescription("商品订单-" + order.getOrderNo());
        request.setOutTradeNo(order.getOrderNo());
        request.setNotifyUrl(wechatPayProperties.getNotifyUrl());

        Amount amount = new Amount();
        amount.setTotal(order.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue()); // 分
        amount.setCurrency("CNY");
        request.setAmount(amount);

        Payer payer = new Payer();
        payer.setOpenid(getUserOpenid(order.getUserId()));
        request.setPayer(payer);

        // 3. 调用微信 JSAPI 下单
        PrepayResponse response = jsapiService.prepay(request);
        String prepayId = response.getPrepayId();

        // 4. 保存支付记录
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(orderId);
        record.setOrderNo(order.getOrderNo());
        record.setUserId(order.getUserId());
        record.setPrepayId(prepayId);
        record.setPayAmount(order.getTotalAmount());
        record.setPayMethod("WECHAT_JSAPI");
        record.setStatus("PENDING");
        this.save(record);

        // 5. 组装前端拉起微信支付所需参数
        Map<String, Object> payParams = new HashMap<>();
        payParams.put("prepayId", prepayId);
        payParams.put("orderNo", order.getOrderNo());
        payParams.put("totalAmount", order.getTotalAmount());
        return payParams;
    }

    // ==================== 支付回调处理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleNotify(String notifyBody,
                             String wechatpaySignature,
                             String wechatpayTimestamp,
                             String wechatpayNonce,
                             String wechatpaySerial,
                             String wechatpaySignatureType) {

        // 1. 构建验签参数
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(wechatpaySerial)
                .nonce(wechatpayNonce)
                .signature(wechatpaySignature)
                .timestamp(wechatpayTimestamp)
                .signType(wechatpaySignatureType)
                .body(notifyBody)
                .build();

        // 2. 验签 + 解密通知内容
        NotificationParser parser = new NotificationParser(notificationConfig);
        Transaction transaction = parser.parse(requestParam, Transaction.class);

        // 3. 校验交易状态
        if (!"SUCCESS".equals(transaction.getTradeState())) {
            log.warn("支付回调状态非SUCCESS: orderNo={} tradeState={}",
                    transaction.getOutTradeNo(), transaction.getTradeState());
            // 未支付成功的通知暂不处理，微信会持续回调直到成功
            return;
        }

        String orderNo = transaction.getOutTradeNo();
        String transactionId = transaction.getTransactionId();
        // 金额从"分"转"元"
        BigDecimal payAmount = BigDecimal.valueOf(transaction.getAmount().getTotal())
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        LocalDateTime payTime = parsePayTime(transaction.getSuccessTime());

        // 4. 更新订单支付状态
        OrderInfo order = orderService.getOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getOrderNo, orderNo));
        if (order == null) {
            log.error("支付回调订单不存在 orderNo={}", orderNo);
            throw new BizException("订单不存在: " + orderNo);
        }
        // 幂等：已支付则跳过
        if (order.getStatus() != null && order.getStatus() == 1) {
            log.info("订单已支付，跳过重复回调 orderNo={}", orderNo);
            return;
        }
        orderService.updatePayStatus(order.getId(), payAmount, 1, payTime);

        // 5. 更新支付记录
        PaymentRecord record = this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderNo, orderNo)
                .eq(PaymentRecord::getStatus, "PENDING"));
        if (record != null) {
            record.setTransactionId(transactionId);
            record.setPayAmount(payAmount);
            record.setStatus("SUCCESS");
            record.setPayTime(payTime);
            this.updateById(record);
        }

        log.info("支付回调处理成功 orderNo={} transactionId={} payAmount={}", orderNo, transactionId, payAmount);
    }

    // ==================== 查询支付状态 ====================

    @Override
    public String queryStatus(Long orderId) {
        PaymentRecord record = this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .orderByDesc(PaymentRecord::getCreateTime)
                .last("LIMIT 1"));
        return record != null ? record.getStatus() : null;
    }

    // ==================== 模拟支付（不走微信SDK） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRecord mockPay(Long orderId) {
        // 1. 查询订单
        OrderInfo order = orderService.getById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        // 仅待支付订单可模拟支付
        if (order.getStatus() != null && order.getStatus() != 0) {
            throw new BizException("订单状态不允许支付，当前状态：" + order.getStatus());
        }

        // 2. 生成模拟微信交易号
        String mockTransactionId = "MOCK_" + System.currentTimeMillis()
                + "_" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal payAmount = order.getTotalAmount();
        LocalDateTime payTime = LocalDateTime.now();

        // 3. 更新订单 payAmount + status（复用真实支付回调同款方法）
        orderService.updatePayStatus(orderId, payAmount, 1, payTime);

        // 4. 创建/更新支付记录为 SUCCESS
        PaymentRecord record = this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .orderByDesc(PaymentRecord::getCreateTime)
                .last("LIMIT 1"));

        if (record == null) {
            record = new PaymentRecord();
            record.setOrderId(orderId);
            record.setOrderNo(order.getOrderNo());
            record.setUserId(order.getUserId());
            record.setPrepayId("MOCK_PREPAY_" + System.currentTimeMillis());
            record.setPayMethod("MOCK");
        }
        record.setTransactionId(mockTransactionId);
        record.setPayAmount(payAmount);
        record.setStatus("SUCCESS");
        record.setPayTime(payTime);
        this.saveOrUpdate(record);

        log.info("模拟支付成功 orderId={} orderNo={} mockTransactionId={} payAmount={}",
                orderId, order.getOrderNo(), mockTransactionId, payAmount);
        return record;
    }

    // ==================== 内部方法 ====================

    /** 从 sys_user 表获取用户 openid，用于微信 JSAPI 支付 */
    private String getUserOpenid(Long userId) {
        SysUser user = authFeignClient.getUserById(userId);
        if (user == null || StringUtils.isEmpty(user.getOpenid())) {
            log.warn("用户 openid 为空 userId={}", userId);
            return "";
        }
        return user.getOpenid();
    }

    /** 微信支付时间格式 → LocalDateTime */
    private LocalDateTime parsePayTime(String timeStr) {
        if (timeStr == null) return null;
        try {
            // 微信回调时间格式: 2018-06-08T10:34:56+08:00
            return LocalDateTime.ofInstant(
                    Instant.parse(timeStr),
                    ZoneId.of("Asia/Shanghai"));
        } catch (Exception e) {
            log.warn("支付时间解析失败: {}", timeStr, e);
            return LocalDateTime.now();
        }
    }
}

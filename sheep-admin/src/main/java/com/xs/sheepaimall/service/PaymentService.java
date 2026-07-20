package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.entity.PaymentRecord;

import java.util.Map;

/**
 * 支付 Service —— 微信支付 API v3
 */
public interface PaymentService extends IService<PaymentRecord> {

    /**
     * 创建支付（JSAPI 公众号/小程序支付）
     * @param orderId 订单ID
     * @return 前端拉起微信支付所需的参数 Map
     */
    Map<String, Object> createJsapiPayment(Long orderId);

    /**
     * 处理微信支付回调通知
     * @param notifyBody   回调请求体（JSON）
     * @param wechatpaySignature  HTTP 头 Wechatpay-Signature
     * @param wechatpayTimestamp   HTTP 头 Wechatpay-Timestamp
     * @param wechatpayNonce       HTTP 头 Wechatpay-Nonce
     * @param wechatpaySerial      HTTP 头 Wechatpay-Serial
     * @param wechatpaySignatureType HTTP 头 Wechatpay-Signature-Type
     */
    void handleNotify(String notifyBody,
                      String wechatpaySignature,
                      String wechatpayTimestamp,
                      String wechatpayNonce,
                      String wechatpaySerial,
                      String wechatpaySignatureType);

    /** 查询支付状态 */
    String queryStatus(Long orderId);

    /**
     * 【模拟支付】不走微信SDK，纯代码模拟支付成功。
     * 自动赋值 payAmount=totalAmount，修改 status=1(已支付)，
     * 生成模拟 PaymentRecord。
     * @param orderId 订单ID
     * @return 支付记录
     */
    PaymentRecord mockPay(Long orderId);
}

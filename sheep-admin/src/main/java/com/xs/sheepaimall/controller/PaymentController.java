package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.entity.PaymentRecord;
import com.xs.sheepaimall.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 支付接口 —— 微信支付 API v3
 */
@Tag(name = "支付", description = "微信支付下单、回调、查询")
@Validated
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;

    @Operation(summary = "创建JSAPI支付", description = "返回前端拉起微信支付所需参数")
    @PostMapping("/create")
    public R<Map<String, Object>> create(
            @Parameter(description = "订单ID") @RequestParam Long orderId) {
        return R.ok(paymentService.createJsapiPayment(orderId));
    }

    @Operation(summary = "微信支付回调通知", description = "由微信服务器调用，无需鉴权")
    @PostMapping("/notify")
    public Map<String, String> notify(HttpServletRequest request) {
        try {
            String body = new String(request.getInputStream().readAllBytes());
            paymentService.handleNotify(
                    body,
                    request.getHeader("Wechatpay-Signature"),
                    request.getHeader("Wechatpay-Timestamp"),
                    request.getHeader("Wechatpay-Nonce"),
                    request.getHeader("Wechatpay-Serial"),
                    request.getHeader("Wechatpay-Signature-Type"));
            // 返回成功应答，微信收到后停止重复回调
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception e) {
            log.error("支付回调处理异常", e);
            return Map.of("code", "FAIL", "message", e.getMessage());
        }
    }

    @Operation(summary = "查询支付状态")
    @GetMapping("/status/{orderId}")
    public R<String> status(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        String status = paymentService.queryStatus(orderId);
        return R.ok(status);
    }

    @Operation(summary = "【模拟支付】不走微信SDK，直接模拟支付成功",
            description = "赋值 payAmount=totalAmount，修改 status=1(已支付)，生成模拟交易记录。" +
                    "用于开发调试阶段跑通完整下单→支付链路。")
    @PostMapping("/mock-pay")
    public R<PaymentRecord> mockPay(@Parameter(description = "订单ID") @RequestParam Long orderId) {
        return R.ok(paymentService.mockPay(orderId));
    }
}

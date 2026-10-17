package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.MerchantPaymentNotifyDTO;
import com.xs.sheepaimall.service.MerchantAiService;
import com.xs.sheepaimall.vo.MerchantAiServiceStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 商家智能客服服务接口 — 开通/续费、状态查询、支付回调（商家端登录态，无需加白名单）
 */
@Tag(name = "商家-智能客服", description = "智能客服服务开通、续费、状态查询、支付回调")
@Validated
@RestController
@RequestMapping("/api/merchant/ai")
public class MerchantAiController {

    @Autowired
    private MerchantAiService merchantAiService;

    @Operation(summary = "开通/续费智能客服", description = "假支付直接成功：生成支付流水、upsert 开通记录并初始化商家向量表；已开通则续费顺延一年")
    @PostMapping("/subscribe")
    public R<MerchantAiServiceStatusVO> subscribe() {
        return R.ok(merchantAiService.subscribe());
    }

    @Operation(summary = "查询开通状态", description = "返回当前商家的开通状态、AI接待开关与有效期至")
    @GetMapping("/status")
    public R<MerchantAiServiceStatusVO> status() {
        return R.ok(merchantAiService.getStatus());
    }

    @Operation(summary = "AI自动接待开关", description = "默认人工策略：开启需已开通且有效期内，关闭随时允许；关闭后商家会话按人工接待")
    @PutMapping("/ai-enabled")
    public R<MerchantAiServiceStatusVO> aiEnabled(
            @Parameter(description = "1=开启AI接待 0=关闭") @RequestParam Integer enabled) {
        return R.ok(merchantAiService.setAiEnabled(enabled));
    }

    @Operation(summary = "支付回调（预留）", description = "真实支付渠道回调入口，当前为 TODO 桩：接入后在此验签并更新流水状态")
    @PostMapping("/payment/notify")
    public Map<String, String> paymentNotify(@RequestBody MerchantPaymentNotifyDTO dto) {
        merchantAiService.handlePaymentNotify(dto);
        // 返回成功应答，避免真实渠道重复回调（当前桩恒成功）
        return Map.of("code", "SUCCESS", "message", "成功");
    }
}

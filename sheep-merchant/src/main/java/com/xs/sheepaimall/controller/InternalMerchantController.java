package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.mapper.*;
import com.xs.sheepaimall.service.FundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家服务内部控制器（供 Feign 调用）
 */
@RestController
@RequestMapping("/internal/merchant")
public class InternalMerchantController {

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantDsrMapper merchantDsrMapper;

    @Autowired
    private FundService fundService;

    @GetMapping("/{id}")
    public Merchant getMerchantById(@PathVariable Long id) {
        return merchantMapper.selectById(id);
    }

    @GetMapping("/by-user/{userId}")
    public Merchant getMerchantByUserId(@PathVariable Long userId) {
        return merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId));
    }

    @GetMapping("/by-ids")
    public List<Merchant> listMerchantsByIds(@RequestParam List<Long> ids) {
        return merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>()
                        .in(Merchant::getId, ids)
                        .select(Merchant::getId, Merchant::getShopStatus));
    }

    @GetMapping("/{id}/shop-status")
    public Integer getMerchantShopStatus(@PathVariable Long id) {
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getId, id)
                        .select(Merchant::getId, Merchant::getShopStatus));
        return merchant != null ? merchant.getShopStatus() : null;
    }

    @GetMapping("/dsr/{merchantId}")
    public MerchantDsr getMerchantDsr(@PathVariable Long merchantId) {
        return merchantDsrMapper.selectOne(
                new LambdaQueryWrapper<MerchantDsr>()
                        .eq(MerchantDsr::getMerchantId, merchantId)
                        .orderByDesc(MerchantDsr::getStatDate)
                        .last("LIMIT 1"));
    }

    @GetMapping("/list-active")
    public List<Merchant> listAllActiveMerchants() {
        return merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getStatus, 1));
    }

    @PostMapping("/settle-order-commission")
    public void settleOrderCommission(@RequestParam Long orderId) {
        fundService.settleOrderCommission(orderId);
    }
}

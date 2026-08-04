package com.xs.sheepaimall.feign;

import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.entity.MerchantDsr;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家服务内部 Feign 接口
 */
@FeignClient(name = "sheep-merchant", path = "/internal/merchant")
public interface MerchantFeignClient {

    @GetMapping("/{id}")
    Merchant getMerchantById(@PathVariable Long id);

    @GetMapping("/by-user/{userId}")
    Merchant getMerchantByUserId(@PathVariable Long userId);

    @GetMapping("/by-ids")
    List<Merchant> listMerchantsByIds(@RequestParam List<Long> ids);

    @GetMapping("/{id}/shop-status")
    Integer getMerchantShopStatus(@PathVariable Long id);

    @GetMapping("/dsr/{merchantId}")
    MerchantDsr getMerchantDsr(@PathVariable Long merchantId);

    @GetMapping("/list-active")
    List<Merchant> listAllActiveMerchants();

    @PostMapping("/settle-order-commission")
    void settleOrderCommission(@RequestParam Long orderId);
}

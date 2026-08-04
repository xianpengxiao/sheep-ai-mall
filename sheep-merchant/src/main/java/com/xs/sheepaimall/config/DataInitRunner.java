package com.xs.sheepaimall.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.entity.MerchantApply;
import com.xs.sheepaimall.mapper.MerchantApplyMapper;
import com.xs.sheepaimall.mapper.MerchantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 应用启动数据初始化：确保测试商家记录和入驻申请存在
 * 依赖 sheep-auth 已创建好对应的 SysUser
 */
@Slf4j
@Component
public class DataInitRunner implements ApplicationRunner {

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantApplyMapper merchantApplyMapper;

    @Override
    public void run(ApplicationArguments args) {
        createMerchantIfNotExist(1001L, 2L, "张三数码店",
                "手机数码、电脑办公、家用电器", "张三", "13912345678");
        createMerchantIfNotExist(1002L, 3L, "李四优选",
                "服装鞋帽、食品饮料", "李四", "13611112222");
    }

    private void createMerchantIfNotExist(Long merchantId, Long userId, String shopName,
                                          String businessScope, String contactName, String contactPhone) {
        // 1. 创建商家记录
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId));
        if (merchant == null) {
            merchant = new Merchant();
            merchant.setId(merchantId);
            merchant.setUserId(userId);
            merchant.setShopName(shopName);
            merchant.setBusinessScope(businessScope);
            merchant.setContactName(contactName);
            merchant.setContactPhone(contactPhone);
            merchant.setStatus(1);
            merchant.setAuditTime(LocalDateTime.now());
            merchantMapper.insert(merchant);
            log.info("商家记录已创建: shop={}, merchantId={}", shopName, merchantId);
        }

        // 2. 创建入驻申请记录（已审核通过）
        MerchantApply apply = merchantApplyMapper.selectOne(
                new LambdaQueryWrapper<MerchantApply>()
                        .eq(MerchantApply::getUserId, userId));
        if (apply == null) {
            apply = new MerchantApply();
            apply.setUserId(userId);
            apply.setShopName(shopName);
            apply.setBusinessScope(businessScope);
            apply.setContactName(contactName);
            apply.setContactPhone(contactPhone);
            apply.setStatus(1);
            apply.setAuditUserId(1L);
            apply.setAuditTime(LocalDateTime.now());
            merchantApplyMapper.insert(apply);
        }
    }
}

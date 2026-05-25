package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.mapper.SkuMapper;
import com.xs.sheepaimall.service.SkuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SkuServiceImpl extends ServiceImpl<SkuMapper, Sku> implements SkuService {

    @Override
    public List<Sku> listBySpuId(Long spuId) {
        return this.list(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getSpuId, spuId)
                .eq(Sku::getStatus, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long skuId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BizException("扣减数量必须大于0");
        }

        // 原子扣减：WHERE stock >= quantity，防止超卖
        boolean success = this.update(new LambdaUpdateWrapper<Sku>()
                .eq(Sku::getId, skuId)
                .ge(Sku::getStock, quantity)
                .setSql("stock = stock - " + quantity));

        if (!success) {
            throw new BizException("库存不足");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSaveOrUpdate(Long spuId, List<Sku> skuList) {
        // 先删除旧的SKU（逻辑删除），再批量插入新的
        this.remove(new LambdaUpdateWrapper<Sku>().eq(Sku::getSpuId, spuId));
        return this.saveBatch(skuList);
    }
}

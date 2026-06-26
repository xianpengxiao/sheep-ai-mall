package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.CacheHelper;
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.mapper.SkuMapper;
import com.xs.sheepaimall.service.SkuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class SkuServiceImpl extends ServiceImpl<SkuMapper, Sku> implements SkuService {

    @Autowired
    private CacheHelper cacheHelper;

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
        // 库存变更后清除父 SPU 缓存，防止详情页展示旧库存
        Sku sku = this.getById(skuId);
        if (sku != null) {
            cacheHelper.evictSpuDetail(sku.getSpuId());
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSaveOrUpdate(Long spuId, List<Sku> skuList) {
        // 先删除旧的SKU（逻辑删除），再批量插入新的
        this.remove(new LambdaUpdateWrapper<Sku>().eq(Sku::getSpuId, spuId));
        boolean result = this.saveBatch(skuList);
        cacheHelper.evictSpuDetail(spuId);
        return result;
    }

    /** 新增 SKU 后清除父 SPU 缓存 */
    @Override
    public boolean save(Sku entity) {
        boolean result = super.save(entity);
        if (entity.getSpuId() != null) {
            cacheHelper.evictSpuDetail(entity.getSpuId());
        }
        return result;
    }

    /** 修改 SKU 后清除父 SPU 缓存 */
    @Override
    public boolean updateById(Sku entity) {
        boolean result = super.updateById(entity);
        // updateById 只更新非 null 字段，需查库获取 spuId
        Sku existing = this.getById(entity.getId());
        if (existing != null) {
            cacheHelper.evictSpuDetail(existing.getSpuId());
        }
        return result;
    }

    /** 删除 SKU 后清除父 SPU 缓存 */
    @Override
    public boolean removeById(Long id) {
        Sku existing = this.getById(id);
        boolean result = super.removeById(id);
        if (existing != null) {
            cacheHelper.evictSpuDetail(existing.getSpuId());
        }
        return result;
    }
}

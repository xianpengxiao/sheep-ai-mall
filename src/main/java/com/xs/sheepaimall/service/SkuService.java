package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.entity.Sku;

import java.util.List;

/**
 * 商品SKU Service
 */
public interface SkuService extends IService<Sku> {

    /** 根据SPU ID查询SKU列表 */
    List<Sku> listBySpuId(Long spuId);

    /** 扣减库存（下单时调用），返回扣减是否成功 */
    boolean deductStock(Long skuId, Integer quantity);

    /** 批量保存或更新SKU */
    boolean batchSaveOrUpdate(Long spuId, List<Sku> skuList);

    boolean removeById(Long id);
}

package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.dto.SpuQueryDTO;
import com.xs.sheepaimall.dto.SpuSaveDTO;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.vo.SpuVO;

/**
 * 商品SPU Service
 */
public interface SpuService extends IService<Spu> {

    /** 分页条件查询 */
    Page<Spu> pageQuery(SpuQueryDTO dto);

    /** 获取商品详情（含SKU列表、分类名称） */
    SpuVO getDetailById(Long id);

    /** 新增商品（含SKU） */
    SpuVO saveWithSku(SpuSaveDTO dto);

    /** 更新商品（含SKU） */
    SpuVO updateWithSku(SpuSaveDTO dto);

    /** 上架/下架 */
    boolean updateStatus(Long id, Integer status);
}

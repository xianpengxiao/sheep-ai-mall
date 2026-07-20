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

    /** 热门商品分页查询（按销量降序，仅上架商品） */
    Page<Spu> pageHotProducts(int pageNum, int pageSize);

    boolean removeById(Long id);

    /** 审核商品（通过/驳回） */
    void auditSpu(Long spuId, Integer auditStatus, String auditMsg);

    /** 待审核商品分页（管理员），支持按审核状态/名称/分类/商家筛选 */
    Page<Spu> pagePendingAudit(int pageNum, int pageSize, Integer auditStatus, String keyword, Long categoryId, Long merchantId);

    /** 管理员查看商品详情（不限审核状态，不走缓存） */
    SpuVO getAdminSpuDetail(Long id);
}

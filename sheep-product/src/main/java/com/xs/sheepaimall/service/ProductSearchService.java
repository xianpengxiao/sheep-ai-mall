package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.dto.ProductSearchDTO;
import com.xs.sheepaimall.vo.MerchantSearchVO;
import com.xs.sheepaimall.vo.ProductSearchVO;

/** 商品搜索 Service（Elasticsearch） */
public interface ProductSearchService {

    /**
     * 搜索商品 — 支持关键词、分类、价格区间、排序、高亮
     *
     * @param dto 搜索条件
     * @return 分页结果（含高亮片段）
     */
    Page<ProductSearchVO> search(ProductSearchDTO dto);

    /** MySQL 全量商品同步到 Elasticsearch */
    void syncAllToEs();

    /** 单个 SPU 同步到 ES（商品变更后调用） */
    void syncSpuToEs(Long spuId);

    // ========== 商家搜索 ==========

    /** 搜索商家 — 支持关键词、综合评分排序、高亮 */
    Page<MerchantSearchVO> searchMerchant(String keyword, int pageNum, int pageSize, String sortBy);

    /** MySQL 全量商家同步到 Elasticsearch */
    void syncAllMerchantsToEs();

    /** 单个商家同步到 ES（商家信息变更后调用） */
    void syncMerchantToEs(Long merchantId);
}

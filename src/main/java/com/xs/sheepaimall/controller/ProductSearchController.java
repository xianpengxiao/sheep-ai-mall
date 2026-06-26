package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.ProductSearchDTO;
import com.xs.sheepaimall.service.ProductSearchService;
import com.xs.sheepaimall.vo.MerchantSearchVO;
import com.xs.sheepaimall.vo.ProductSearchVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
/** 商品搜索接口（Elasticsearch） — 仅在配置 spring.elasticsearch.uris 后生效 */
@Tag(name = "商品搜索", description = "Elasticsearch 全文检索，支持关键词/分类/价格/排序/高亮")
@RestController
@RequestMapping("/api/search")
public class ProductSearchController {

    @Autowired
    private ProductSearchService productSearchService;

    @Operation(summary = "商品搜索", description = "支持关键词多字段匹配、分类筛选、价格区间、销量/价格/最新排序、关键词高亮")
    @GetMapping("/product")
    public R<Page<ProductSearchVO>> search(ProductSearchDTO dto) {
        return R.ok(productSearchService.search(dto));
    }

    @Operation(summary = "全量同步商品和商家到 ES", description = "将 MySQL 中的商品和商家数据全量同步到 Elasticsearch（先清空后写入）")
    @PostMapping("/sync-all")
    public R<String> syncAll() {
        productSearchService.syncAllToEs();
        return R.ok("全量同步完成");
    }

    @Operation(summary = "商家搜索", description = "支持关键词搜索店铺名称/简介/经营范围，支持按综合评分排序")
    @GetMapping("/merchant")
    public R<Page<MerchantSearchVO>> searchMerchant(
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "排序：relevance=相关度 compositeScoreDesc=综合评分降序 newest=最新") @RequestParam(defaultValue = "compositeScoreDesc") String sortBy) {
        return R.ok(productSearchService.searchMerchant(keyword, pageNum, pageSize, sortBy));
    }
}

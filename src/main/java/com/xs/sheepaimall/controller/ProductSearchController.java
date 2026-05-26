package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.ProductSearchDTO;
import com.xs.sheepaimall.service.ProductSearchService;
import com.xs.sheepaimall.vo.ProductSearchVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** 商品搜索接口（Elasticsearch） — 仅在配置 spring.elasticsearch.uris 后生效 */
@Tag(name = "商品搜索", description = "Elasticsearch 全文检索，支持关键词/分类/价格/排序/高亮")
@RestController
@RequestMapping("/api/search")
@ConditionalOnProperty(name = "spring.elasticsearch.uris")
public class ProductSearchController {

    @Resource
    private ProductSearchService productSearchService;

    @Operation(summary = "商品搜索", description = "支持关键词多字段匹配、分类筛选、价格区间、销量/价格/最新排序、关键词高亮")
    @GetMapping("/product")
    public R<Page<ProductSearchVO>> search(ProductSearchDTO dto) {
        return R.ok(productSearchService.search(dto));
    }

    @Operation(summary = "全量同步商品到 ES", description = "将 MySQL 中所有未删除的 SPU 同步到 Elasticsearch 索引")
    @PostMapping("/sync-all")
    public R<String> syncAll() {
        productSearchService.syncAllToEs();
        return R.ok("全量同步完成");
    }
}

package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.dto.ProductSearchDTO;
import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.repository.SpuDocumentRepository;
import com.xs.sheepaimall.service.CategoryService;
import com.xs.sheepaimall.service.ProductSearchService;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.vo.ProductSearchVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 商品搜索 Service 实现 — Elasticsearch */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.elasticsearch.uris")
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final int SYNC_BATCH_SIZE = 500;

    @Resource
    private ElasticsearchClient esClient;

    @Resource
    private SpuDocumentRepository spuDocRepo;

    @Resource
    private SpuService spuService;

    @Resource
    private SkuService skuService;

    @Resource
    private CategoryService categoryService;

    // ==================== 搜索 ====================

    @Override
    public Page<ProductSearchVO> search(ProductSearchDTO dto) {

        int pageNum = Math.max(dto.getPageNum() != null ? dto.getPageNum() : 1, 1);
        int pageSize = dto.getPageSize() != null ? dto.getPageSize() : 20;
        try {
            SearchResponse<SpuDocument> response = esClient.search(sr -> sr
                            .index("spu_index")
                            .query(buildEsQuery(dto))
                            .highlight(buildHighlight())
                            .sort(buildSortOptions(dto.getSortBy()))
                            .from((pageNum - 1) * pageSize)
                            .size(pageSize),
                    SpuDocument.class);

            List<ProductSearchVO> records = new ArrayList<>();
            for (Hit<SpuDocument> hit : response.hits().hits()) {
                SpuDocument doc = hit.source();
                if (doc == null) continue;
                ProductSearchVO vo = new ProductSearchVO();
                BeanUtil.copyProperties(doc, vo);
                // 提取高亮
                if (hit.highlight() != null) {
                    List<String> nameHL = hit.highlight().get("name");
                    if (nameHL != null && !nameHL.isEmpty()) vo.setNameHighlight(nameHL.get(0));
                    List<String> subTitleHL = hit.highlight().get("subTitle");
                    if (subTitleHL != null && !subTitleHL.isEmpty())
                        vo.setSubTitleHighlight(subTitleHL.get(0));
                }
                records.add(vo);
            }

            Page<ProductSearchVO> resultPage = new Page<>(pageNum, pageSize);
            resultPage.setRecords(records);
            resultPage.setTotal(response.hits().total() != null ? response.hits().total().value() : 0);

            return resultPage;

        } catch (Exception e) {
            throw new RuntimeException("ES 搜索异常: " + e.getMessage(), e);
        }
    }

    /** 构建 ES bool 查询 */
    private Query buildEsQuery(ProductSearchDTO dto) {
        return Query.of(q -> q.bool(b -> {
            if (StrUtil.isNotBlank(dto.getKeyword())) {
                b.must(m -> m.multiMatch(mm -> mm
                        .fields("name^3", "subTitle^2", "description", "brand")
                        .query(dto.getKeyword())));
            } else {
                b.must(m -> m.matchAll(ma -> ma));
            }
            if (dto.getCategoryId() != null) {
                b.filter(f -> f.term(t -> t.field("categoryId").value(dto.getCategoryId())));
            }
            // 价格区间筛选：minPrice >= 用户下限，maxPrice <= 用户上限
            if (dto.getMinPrice() != null) {
                b.filter(f -> f.range(r -> r.field("minPrice")
                        .gte(JsonData.of(dto.getMinPrice().doubleValue()))));
            }
            if (dto.getMaxPrice() != null) {
                b.filter(f -> f.range(r -> r.field("maxPrice")
                        .lte(JsonData.of(dto.getMaxPrice().doubleValue()))));
            }
            b.filter(f -> f.term(t -> t.field("status").value(1)));
            return b;
        }));
    }

    /** 构建高亮 — 用 Map 显式传多个字段，避免链式 .fields() 被底层覆盖 */
    private Highlight buildHighlight() {
        return Highlight.of(h -> h.fields(Map.of(
                "name", HighlightField.of(
                        hf -> hf.preTags("<em>").postTags("</em>").fragmentSize(50).numberOfFragments(1)),
                "subTitle", HighlightField.of(
                        hf -> hf.preTags("<em>").postTags("</em>").fragmentSize(50).numberOfFragments(1))
        )));
    }

    /** 构建排序 */
    private List<SortOptions> buildSortOptions(String sortBy) {
        if (StrUtil.isBlank(sortBy) || "relevance".equals(sortBy)) {
            return List.of(SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc))));
        }
        return switch (sortBy) {
            case "salesDesc" -> List.of(SortOptions.of(so -> so.field(f ->
                    f.field("salesCount").order(SortOrder.Desc))));
            case "priceAsc" -> List.of(SortOptions.of(so -> so.field(f ->
                    f.field("minPrice").order(SortOrder.Asc))));
            case "priceDesc" -> List.of(SortOptions.of(so -> so.field(f ->
                    f.field("maxPrice").order(SortOrder.Desc))));
            case "newest" -> List.of(SortOptions.of(so -> so.field(f ->
                    f.field("createTime").order(SortOrder.Desc))));
            default -> List.of(SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc))));
        };
    }

    // ==================== MySQL → ES 同步 ====================

    @Override
    public void syncAllToEs() {
        long total = spuService.count();
        long pages = (total + SYNC_BATCH_SIZE - 1) / SYNC_BATCH_SIZE;

        for (int i = 1; i <= pages; i++) {
            Page<Spu> spuPage = spuService.page(
                    new Page<>(i, SYNC_BATCH_SIZE),
                    new LambdaQueryWrapper<>());
            List<SpuDocument> docs = spuPage.getRecords().stream()
                    .map(this::toDocument)
                    .collect(Collectors.toList());
            if (!docs.isEmpty()) {
                spuDocRepo.saveAll(docs);
            }
        }
    }

    @Override
    public void syncSpuToEs(Long spuId) {
        Spu spu = spuService.getById(spuId);
        if (spu == null) {
            spuDocRepo.deleteById(spuId);
            return;
        }
        spuDocRepo.save(toDocument(spu));
    }

    /** Spu → ES Document（含 SKU 价格聚合 + 分类名称） */
    private SpuDocument toDocument(Spu spu) {
        SpuDocument doc = new SpuDocument();
        BeanUtil.copyProperties(spu, doc);

        Category category = categoryService.getById(spu.getCategoryId());
        if (category != null) {
            doc.setCategoryName(category.getName());
        }

        List<Sku> skuList = skuService.listBySpuId(spu.getId());
        if (skuList != null && !skuList.isEmpty()) {
            doc.setMinPrice(skuList.stream().map(Sku::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            doc.setMaxPrice(skuList.stream().map(Sku::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
        } else {
            doc.setMinPrice(BigDecimal.ZERO);
            doc.setMaxPrice(BigDecimal.ZERO);
        }
        return doc;
    }
}

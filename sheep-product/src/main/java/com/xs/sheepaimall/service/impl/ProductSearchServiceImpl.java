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
import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.repository.MerchantDocumentRepository;
import com.xs.sheepaimall.repository.SpuDocumentRepository;
import com.xs.sheepaimall.service.CategoryService;
import com.xs.sheepaimall.feign.MerchantFeignClient;
import com.xs.sheepaimall.service.ProductSearchService;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.entity.MerchantDsr;
import com.xs.sheepaimall.vo.MerchantSearchVO;
import com.xs.sheepaimall.vo.ProductSearchVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** 商品搜索 Service 实现 — Elasticsearch */
@Slf4j
@Service
@ConditionalOnBean(ElasticsearchClient.class)
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final int SYNC_BATCH_SIZE = 500;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private SpuDocumentRepository spuDocRepo;

    @Autowired
    private SpuService spuService;

    @Autowired
    private SkuService skuService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private MerchantDocumentRepository merchantDocRepo;

    @Autowired
    private MerchantFeignClient merchantFeignClient;

    // ==================== 商品搜索 ====================

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
                // ES 分词器未返回高亮时（如单字搜索），Java 手动标记高亮兜底
                String keyword = dto.getKeyword();
                if (StrUtil.isNotBlank(keyword)) {
                    if (StrUtil.isBlank(vo.getNameHighlight()) && StrUtil.isNotBlank(doc.getName())) {
                        vo.setNameHighlight(wrapHighlight(doc.getName(), keyword));
                    }
                    if (StrUtil.isBlank(vo.getSubTitleHighlight()) && StrUtil.isNotBlank(doc.getSubTitle())) {
                        vo.setSubTitleHighlight(wrapHighlight(doc.getSubTitle(), keyword));
                    }
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

    /** 构建 ES bool 查询 — IK 分词 + wildcard 兜底，支持单字搜索 */
    private Query buildEsQuery(ProductSearchDTO dto) {
        return Query.of(q -> q.bool(b -> {
            if (StrUtil.isNotBlank(dto.getKeyword())) {
                String kw = dto.getKeyword().trim();
                // bool should: IK 多字段匹配(高权重) + keyword 通配(单字兜底)，满足任一即命中
                b.must(m -> m.bool(b2 -> {
                    b2.should(s1 -> s1.multiMatch(mm -> mm
                            .fields("name^3", "subTitle^2", "description", "brand")
                            .query(kw)));
                    b2.should(s2 -> s2.wildcard(w -> w
                            .field("name.keyword")
                            .caseInsensitive(true)
                            .value("*" + kw + "*")));
                    b2.should(s3 -> s3.wildcard(w -> w
                            .field("subTitle.keyword")
                            .caseInsensitive(true)
                            .value("*" + kw + "*")));
                    b2.minimumShouldMatch("1");
                    return b2;
                }));
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

    /** 构建高亮 */
    private Highlight buildHighlight() {
        return Highlight.of(h -> h.fields(Map.of(
                "name", HighlightField.of(
                        hf -> hf.preTags("<em>").postTags("</em>").fragmentSize(50).numberOfFragments(1)),
                "subTitle", HighlightField.of(
                        hf -> hf.preTags("<em>").postTags("</em>").fragmentSize(50).numberOfFragments(1))
        )));
    }

    /** Java 手动对关键词加 <em> 高亮标签 — 兜底 ES 分词器无法产出高亮的场景（如单字搜索） */
    private String wrapHighlight(String text, String keyword) {
        if (StrUtil.isBlank(text) || StrUtil.isBlank(keyword)) return text;
        String lowerText = text.toLowerCase();
        String lowerKw = keyword.toLowerCase();
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        int kwLen = keyword.length();
        while (idx <= text.length() - kwLen) {
            if (lowerText.startsWith(lowerKw, idx)) {
                sb.append("<em>").append(text, idx, idx + kwLen).append("</em>");
                idx += kwLen;
            } else {
                sb.append(text.charAt(idx));
                idx++;
            }
        }
        sb.append(text.substring(idx));
        return sb.toString();
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
    @Transactional
    public void syncAllToEs() {
        // 1. 清空索引，确保已删除/下架的商品不再出现在搜索结果中
        spuDocRepo.deleteAll();

        // 2. 只同步上架且审核通过的商品（status=1, audit_status=1）
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<Spu>()
                .eq(Spu::getStatus, 1)
                .eq(Spu::getAuditStatus, 1);

        long total = spuService.count(wrapper);
        long pages = (total + SYNC_BATCH_SIZE - 1) / SYNC_BATCH_SIZE;

        for (int i = 1; i <= pages; i++) {
            Page<Spu> spuPage = spuService.page(
                    new Page<>(i, SYNC_BATCH_SIZE),
                    wrapper);
            List<SpuDocument> docs = spuPage.getRecords().stream()
                    .map(this::toDocument)
                    .collect(Collectors.toList());
            if (!docs.isEmpty()) {
                spuDocRepo.saveAll(docs);
            }
        }

        // 3. 同步商家
        syncAllMerchantsToEs();
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

    // ==================== 商家搜索 ====================

    @Override
    public Page<MerchantSearchVO> searchMerchant(String keyword, int pageNum, int pageSize, String sortBy) {
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.max(pageSize, 1);
        try {
            int finalPageNum = pageNum;
            int finalPageSize = pageSize;
            SearchResponse<MerchantDocument> response = esClient.search(sr -> sr
                            .index("merchant_index")
                            .query(buildMerchantQuery(keyword))
                            .highlight(buildMerchantHighlight())
                            .sort(buildMerchantSortOptions(sortBy))
                            .from((finalPageNum - 1) * finalPageSize)
                            .size(finalPageSize),
                    MerchantDocument.class);

            List<MerchantSearchVO> records = new ArrayList<>();
            for (Hit<MerchantDocument> hit : response.hits().hits()) {
                MerchantDocument doc = hit.source();
                if (doc == null) continue;
                MerchantSearchVO vo = new MerchantSearchVO();
                BeanUtil.copyProperties(doc, vo);
                // 经营范围ID → 名称
                if (StrUtil.isNotBlank(doc.getBusinessScope())) {
                    List<Long> ids = Arrays.stream(doc.getBusinessScope().split(","))
                            .map(String::trim).filter(StrUtil::isNotBlank)
                            .map(s -> { try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }})
                            .filter(Objects::nonNull).collect(Collectors.toList());
                    if (!ids.isEmpty()) {
                        String names = categoryService.listByIds(ids).stream()
                                .map(Category::getName).filter(Objects::nonNull)
                                .collect(Collectors.joining(","));
                        if (StrUtil.isNotBlank(names)) vo.setBusinessScope(names);
                    }
                }
                // 提取高亮
                if (hit.highlight() != null) {
                    List<String> nameHL = hit.highlight().get("shopName");
                    if (nameHL != null && !nameHL.isEmpty()) vo.setShopNameHighlight(nameHL.get(0));
                    List<String> descHL = hit.highlight().get("shopDesc");
                    if (descHL != null && !descHL.isEmpty()) vo.setShopDescHighlight(descHL.get(0));
                }
                // 单字搜索 Java 高亮兜底
                if (StrUtil.isNotBlank(keyword)) {
                    if (StrUtil.isBlank(vo.getShopNameHighlight()) && StrUtil.isNotBlank(doc.getShopName())) {
                        vo.setShopNameHighlight(wrapHighlight(doc.getShopName(), keyword));
                    }
                    if (StrUtil.isBlank(vo.getShopDescHighlight()) && StrUtil.isNotBlank(doc.getShopDesc())) {
                        vo.setShopDescHighlight(wrapHighlight(doc.getShopDesc(), keyword));
                    }
                }
                records.add(vo);
            }

            Page<MerchantSearchVO> resultPage = new Page<>(pageNum, pageSize);
            resultPage.setRecords(records);
            resultPage.setTotal(response.hits().total() != null ? response.hits().total().value() : 0);
            return resultPage;

        } catch (Exception e) {
            throw new RuntimeException("ES 商家搜索异常: " + e.getMessage(), e);
        }
    }

    /** 构建商家 ES 查询 */
    private Query buildMerchantQuery(String keyword) {
        return Query.of(q -> q.bool(b -> {
            if (StrUtil.isNotBlank(keyword)) {
                String kw = keyword.trim();
                b.must(m -> m.bool(b2 -> {
                    b2.should(s1 -> s1.multiMatch(mm -> mm
                            .fields("shopName^3", "shopDesc", "businessScope")
                            .query(kw)));
                    b2.should(s2 -> s2.wildcard(w -> w
                            .field("shopName.keyword")
                            .caseInsensitive(true)
                            .value("*" + kw + "*")));
                    b2.minimumShouldMatch("1");
                    return b2;
                }));
            } else {
                b.must(m -> m.matchAll(ma -> ma));
            }
            // 只显示已开通且营业中的商家
            b.filter(f -> f.term(t -> t.field("status").value(1)));
            b.filter(f -> f.term(t -> t.field("shopStatus").value(1)));
            return b;
        }));
    }

    /** 构建商家搜索高亮 */
    private Highlight buildMerchantHighlight() {
        return Highlight.of(h -> h.fields(Map.of(
                "shopName", HighlightField.of(
                        hf -> hf.preTags("<em>").postTags("</em>").fragmentSize(50).numberOfFragments(1)),
                "shopDesc", HighlightField.of(
                        hf -> hf.preTags("<em>").postTags("</em>").fragmentSize(50).numberOfFragments(1))
        )));
    }

    /** 构建商家排序 */
    private List<SortOptions> buildMerchantSortOptions(String sortBy) {
        if (StrUtil.isBlank(sortBy) || "relevance".equals(sortBy)) {
            return List.of(SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc))));
        }
        return switch (sortBy) {
            case "compositeScoreDesc" -> List.of(SortOptions.of(so -> so.field(f ->
                    f.field("compositeScore").order(SortOrder.Desc))));
            case "newest" -> List.of(SortOptions.of(so -> so.field(f ->
                    f.field("createTime").order(SortOrder.Desc))));
            default -> List.of(SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc))));
        };
    }

    // ==================== 商家同步 ====================

    @Override
    @Transactional
    public void syncAllMerchantsToEs() {
        merchantDocRepo.deleteAll();

        List<Merchant> all = merchantFeignClient.listAllActiveMerchants();

        List<MerchantDocument> docs = all.stream()
                .map(this::toMerchantDocument)
                .collect(Collectors.toList());

        if (!docs.isEmpty()) {
            merchantDocRepo.saveAll(docs);
        }
        log.info("商家全量同步完成，共 {} 条", docs.size());
    }

    @Override
    public void syncMerchantToEs(Long merchantId) {
        Merchant merchant = merchantFeignClient.getMerchantById(merchantId);
        if (merchant == null) {
            merchantDocRepo.deleteById(merchantId);
            return;
        }
        merchantDocRepo.save(toMerchantDocument(merchant));
    }

    /** Merchant → ES Document（含 DSR 评分聚合） */
    private MerchantDocument toMerchantDocument(Merchant merchant) {
        MerchantDocument doc = new MerchantDocument();
        BeanUtil.copyProperties(merchant, doc);

        // 注入 DSR 评分
        MerchantDsr dsr = merchantFeignClient.getMerchantDsr(merchant.getId());
        if (dsr != null) {
            BigDecimal ds = dsr.getDescribeScore();
            BigDecimal ss = dsr.getServiceScore();
            BigDecimal ls = dsr.getLogisticsScore();
            doc.setDescribeScore(ds);
            doc.setServiceScore(ss);
            doc.setLogisticsScore(ls);
            // 综合评分 = 三维平均
            if (ds != null && ss != null && ls != null) {
                doc.setCompositeScore(ds.add(ss).add(ls).divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP));
            }
            doc.setDsrCount(dsr.getTotalCount());
        }
        return doc;
    }
}

package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.CacheConstants;
import com.xs.sheepaimall.common.CacheHelper;
import com.xs.sheepaimall.common.ResultCode;
import com.xs.sheepaimall.dto.SkuSaveDTO;
import com.xs.sheepaimall.dto.SpuQueryDTO;
import com.xs.sheepaimall.dto.SpuSaveDTO;
import com.xs.sheepaimall.entity.Category;
import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.entity.MerchantDsr;
import com.xs.sheepaimall.entity.ProductReview;
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.mapper.MerchantDsrMapper;
import com.xs.sheepaimall.mapper.MerchantMapper;
import com.xs.sheepaimall.mapper.ProductReviewMapper;
import com.xs.sheepaimall.mapper.SpuMapper;
import com.xs.sheepaimall.service.CategoryService;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.vo.SkuStockVO;
import com.xs.sheepaimall.vo.SkuVO;
import com.xs.sheepaimall.vo.SpuVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SpuServiceImpl extends ServiceImpl<SpuMapper, Spu> implements SpuService {

    /** SPU 详情缓存版本号，VO 结构变化时递增以强制刷新旧缓存 */
    private static final String SPU_CACHE_VERSION = "v2";

    @Resource
    private SkuService skuService;

    @Resource
    private CategoryService categoryService;

    @Resource
    private CacheHelper cacheHelper;

    @Resource
    private ProductReviewMapper productReviewMapper;

    @Resource
    private MerchantMapper merchantMapper;

    @Resource
    private MerchantDsrMapper merchantDsrMapper;

    @Override
    public Page<Spu> pageQuery(SpuQueryDTO dto) {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<Spu>()
                .eq(dto.getCategoryId() != null, Spu::getCategoryId, dto.getCategoryId())
                .eq(dto.getMerchantId() != null, Spu::getMerchantId, dto.getMerchantId())
                .like(StrUtil.isNotBlank(dto.getKeyword()), Spu::getName, dto.getKeyword())
                .eq(dto.getStatus() != null, Spu::getStatus, dto.getStatus())
                .eq(Spu::getAuditStatus, 1); // 公开查询只显示审核通过的商品

        if ("sales_count".equals(dto.getOrderBy())) {
            wrapper.orderByDesc(Spu::getSalesCount);
        } else {
            wrapper.orderByDesc(Spu::getCreateTime);
        }

        Page<Spu> page = this.page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        // 批量填充商家营业状态 + 最低SKU价格
        if (!page.getRecords().isEmpty()) {
            List<Long> spuIds = page.getRecords().stream().map(Spu::getId).collect(Collectors.toList());
            List<Long> merchantIds = page.getRecords().stream()
                    .map(Spu::getMerchantId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // 商家营业状态
            if (!merchantIds.isEmpty()) {
                Map<Long, Integer> shopStatusMap = merchantMapper.selectList(
                                new LambdaQueryWrapper<Merchant>()
                                        .in(Merchant::getId, merchantIds)
                                        .select(Merchant::getId, Merchant::getShopStatus))
                        .stream()
                        .collect(Collectors.toMap(Merchant::getId, Merchant::getShopStatus, (a, b) -> a));
                page.getRecords().forEach(spu ->
                        spu.setShopStatus(shopStatusMap.get(spu.getMerchantId())));
            }

            // 最低SKU价格
            Map<Long, BigDecimal> minPriceMap = new HashMap<>();
            skuService.lambdaQuery()
                    .in(Sku::getSpuId, spuIds)
                    .select(Sku::getSpuId, Sku::getPrice)
                    .list()
                    .forEach(sku -> {
                        BigDecimal current = minPriceMap.get(sku.getSpuId());
                        if (current == null || sku.getPrice().compareTo(current) < 0) {
                            minPriceMap.put(sku.getSpuId(), sku.getPrice());
                        }
                    });
            page.getRecords().forEach(spu ->
                    spu.setMinPrice(minPriceMap.get(spu.getId())));

            // 批量填充库存信息
            if (!spuIds.isEmpty()) {
                List<Sku> allSkus = skuService.lambdaQuery()
                        .in(Sku::getSpuId, spuIds)
                        .eq(Sku::getStatus, 1)
                        .select(Sku::getSpuId, Sku::getSkuName, Sku::getPrice, Sku::getStock)
                        .list();

                Map<Long, List<Sku>> skuGroup = allSkus.stream()
                        .collect(Collectors.groupingBy(Sku::getSpuId));

                for (Spu spu : page.getRecords()) {
                    List<Sku> skus = skuGroup.getOrDefault(spu.getId(), Collections.emptyList());
                    int totalStock = skus.stream().mapToInt(Sku::getStock).sum();
                    boolean multiSpec = skus.size() > 1;
                    long outOfStockCount = skus.stream().filter(s -> s.getStock() == 0).count();
                    boolean partOutOfStock = outOfStockCount > 0 && outOfStockCount < skus.size();

                    spu.setTotalStock(totalStock);
                    spu.setStockStatus(totalStock == 0 ? 0 : totalStock <= 10 ? 2 : 1);
                    spu.setMultiSpec(multiSpec);
                    spu.setPartOutOfStock(partOutOfStock);
                    spu.setSkuStockList(skus.stream().map(sku -> {
                        SkuStockVO vo = new SkuStockVO();
                        vo.setSkuId(sku.getId());
                        vo.setSkuName(sku.getSkuName());
                        vo.setPrice(sku.getPrice());
                        vo.setStock(sku.getStock());
                        return vo;
                    }).collect(Collectors.toList()));
                }
            }
        }

        return page;
    }

    @Override
    public SpuVO getDetailById(Long id) {
        String cacheKey = CacheConstants.SPU_DETAIL + "::" + id + "::" + SPU_CACHE_VERSION;

        String cachedJson = cacheHelper.getOrFetch(cacheKey,
                () -> {
                    SpuVO vo = loadDetailFromDb(id);
                    if (vo == null) return null;
                    return JSONUtil.toJsonStr(vo);
                },
                CacheConstants.SPU_DETAIL_TTL);

        if (cachedJson == null) return null;
        return JSONUtil.toBean(cachedJson, SpuVO.class);
    }

    /** 从数据库加载商品详情（不含缓存逻辑） */
    private SpuVO loadDetailFromDb(Long id) {
        Spu spu = this.getById(id);
        if (spu == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在");
        }
        SpuVO vo = toSpuVO(spu);

        Category category = categoryService.getById(spu.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getName());
        }

        List<Sku> skuList = skuService.listBySpuId(spu.getId());
        vo.setSkuList(skuList.stream().map(this::toSkuVO).collect(Collectors.toList()));

        // 查询商铺信息
        if (spu.getMerchantId() != null) {
            Merchant merchant = merchantMapper.selectById(spu.getMerchantId());
            if (merchant != null) {
                vo.setShopName(merchant.getShopName());
                vo.setShopLogo(merchant.getShopLogo());
                // 店铺DSR评分
                MerchantDsr dsr = merchantDsrMapper.selectOne(
                        new LambdaQueryWrapper<MerchantDsr>()
                                .eq(MerchantDsr::getMerchantId, spu.getMerchantId())
                                .orderByDesc(MerchantDsr::getStatDate)
                                .last("LIMIT 1"));
                if (dsr != null) {
                    vo.setShopDescribeScore(dsr.getDescribeScore() != null ? dsr.getDescribeScore().doubleValue() : null);
                    vo.setShopServiceScore(dsr.getServiceScore() != null ? dsr.getServiceScore().doubleValue() : null);
                    vo.setShopLogisticsScore(dsr.getLogisticsScore() != null ? dsr.getLogisticsScore().doubleValue() : null);
                }
            }
        }

        // 查询平均评分和评价数
        List<ProductReview> reviews = productReviewMapper.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getSpuId, id)
                        .eq(ProductReview::getStatus, 1)
                        .eq(ProductReview::getReviewStatus, 1));
        if (!reviews.isEmpty()) {
            double avg = reviews.stream()
                    .mapToInt(r -> r.getRating() != null ? r.getRating() : 0)
                    .average()
                    .orElse(0);
            vo.setRating(Math.round(avg * 10) / 10.0);
            vo.setReviewCount(reviews.size());
        } else {
            vo.setRating(0.0);
            vo.setReviewCount(0);
        }

        return vo;
    }

    @Override
    public Page<Spu> pageHotProducts(int pageNum, int pageSize) {
        String cacheKey = CacheConstants.SPU_HOT_PAGE + "::" + pageNum + "::" + pageSize;

        String cachedJson = cacheHelper.getOrFetch(cacheKey,
                () -> {
                    Page<Spu> page = this.page(
                            new Page<>(pageNum, pageSize),
                            new LambdaQueryWrapper<Spu>()
                                    .eq(Spu::getStatus, 1)
                                    .eq(Spu::getAuditStatus, 1)
                                    .orderByDesc(Spu::getSalesCount));
                    return page.getRecords().isEmpty() ? null : JSONUtil.toJsonStr(page);
                },
                CacheConstants.SPU_HOT_PAGE_TTL);

        if (cachedJson == null) return new Page<>(pageNum, pageSize);
        return JSONUtil.toBean(cachedJson, Page.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuVO saveWithSku(SpuSaveDTO dto) {
        Spu spu = new Spu();
        BeanUtil.copyProperties(dto, spu);
        if (dto.getImageList() != null) {
            spu.setImageList(JSONUtil.toJsonStr(dto.getImageList()));
        }
        this.save(spu);

        if (dto.getSkuList() != null && !dto.getSkuList().isEmpty()) {
            List<Sku> skuList = dto.getSkuList().stream()
                    .map(this::toSkuEntity)
                    .collect(Collectors.toList());
            skuList.forEach(s -> s.setSpuId(spu.getId()));
            skuService.batchSaveOrUpdate(spu.getId(), skuList);
        }

        // 新增商品后清除缓存
        cacheHelper.evictSpuHotPage();
        return getDetailById(spu.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuVO updateWithSku(SpuSaveDTO dto) {
        Spu existSpu = this.getById(dto.getId());
        if (existSpu == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在");
        }

        Spu spu = new Spu();
        BeanUtil.copyProperties(dto, spu);
        if (dto.getImageList() != null) {
            spu.setImageList(JSONUtil.toJsonStr(dto.getImageList()));
        }
        this.updateById(spu);

        if (dto.getSkuList() != null) {
            List<Sku> skuList = dto.getSkuList().stream()
                    .map(this::toSkuEntity)
                    .collect(Collectors.toList());
            skuList.forEach(s -> s.setSpuId(spu.getId()));
            skuService.batchSaveOrUpdate(spu.getId(), skuList);
        }

        // 更新后清除缓存
        cacheHelper.evictSpuDetail(dto.getId());
        cacheHelper.evictSpuHotPage();
        return getDetailById(spu.getId());
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        Spu spu = new Spu();
        spu.setId(id);
        spu.setStatus(status);
        boolean ok = this.updateById(spu);
        // 状态变更后清除缓存
        cacheHelper.evictSpuDetail(id);
        cacheHelper.evictSpuHotPage();
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditSpu(Long spuId, Integer auditStatus, String auditMsg) {
        Spu spu = this.getById(spuId);
        if (spu == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在");
        }
        if (spu.getAuditStatus() != 0) {
            throw new BizException("该商品已被审核，请勿重复操作");
        }
        Spu update = new Spu();
        update.setId(spuId);
        update.setAuditStatus(auditStatus);
        if (auditStatus == 1) {
            // 审核通过 → 自动上架
            update.setStatus(1);
            update.setAuditMsg("");
        } else if (auditStatus == 2) {
            // 审核驳回
            if (StrUtil.isBlank(auditMsg)) {
                throw new BizException("审核驳回时必须填写驳回原因");
            }
            update.setStatus(0);
            update.setAuditMsg(auditMsg);
        } else {
            throw new BizException("审核状态值错误");
        }
        this.updateById(update);

        cacheHelper.evictSpuDetail(spuId);
        cacheHelper.evictSpuHotPage();
    }

    @Override
    public Page<Spu> pagePendingAudit(int pageNum, int pageSize) {
        return this.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getAuditStatus, 0)
                        .orderByDesc(Spu::getCreateTime));
    }

    /** 重写逻辑删除，同时清除缓存 */
    @Override
    public boolean removeById(Long id) {
        boolean ok = super.removeById(id);
        if (ok) {
            cacheHelper.evictSpuDetail(id);
            cacheHelper.evictSpuHotPage();
        }
        return ok;
    }

    // ============== 内部转换方法 ==============

    private Sku toSkuEntity(SkuSaveDTO dto) {
        Sku sku = new Sku();
        BeanUtil.copyProperties(dto, sku);
        if (dto.getSpecInfo() != null) {
            sku.setSpecInfo(JSONUtil.toJsonStr(dto.getSpecInfo()));
        }
        // sku_code 为空时自动生成（数据库 NOT NULL + UNIQUE）
        if (StrUtil.isBlank(sku.getSkuCode())) {
            sku.setSkuCode("SKU" + System.currentTimeMillis() + (int) (Math.random() * 1000));
        }
        return sku;
    }

    private SpuVO toSpuVO(Spu spu) {
        SpuVO vo = new SpuVO();
        BeanUtil.copyProperties(spu, vo);
        if (StrUtil.isNotBlank(spu.getImageList())) {
            vo.setImageList(JSONUtil.toList(spu.getImageList(), String.class));
        }
        return vo;
    }

    private SkuVO toSkuVO(Sku sku) {
        SkuVO vo = new SkuVO();
        BeanUtil.copyProperties(sku, vo, "specInfo"); // specInfo 类型不同（String→Map），跳过后手动转换
        if (StrUtil.isNotBlank(sku.getSpecInfo())) {
            vo.setSpecInfo(JSONUtil.toBean(sku.getSpecInfo(), java.util.Map.class));
        }
        return vo;
    }
}

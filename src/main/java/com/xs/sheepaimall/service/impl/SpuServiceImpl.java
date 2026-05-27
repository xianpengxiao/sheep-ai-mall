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
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.mapper.SpuMapper;
import com.xs.sheepaimall.service.CategoryService;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.vo.SkuVO;
import com.xs.sheepaimall.vo.SpuVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SpuServiceImpl extends ServiceImpl<SpuMapper, Spu> implements SpuService {

    @Resource
    private SkuService skuService;

    @Resource
    private CategoryService categoryService;

    @Resource
    private CacheHelper cacheHelper;

    @Override
    public Page<Spu> pageQuery(SpuQueryDTO dto) {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<Spu>()
                .eq(dto.getCategoryId() != null, Spu::getCategoryId, dto.getCategoryId())
                .like(StrUtil.isNotBlank(dto.getKeyword()), Spu::getName, dto.getKeyword())
                .eq(dto.getStatus() != null, Spu::getStatus, dto.getStatus());

        if ("sales_count".equals(dto.getOrderBy())) {
            wrapper.orderByDesc(Spu::getSalesCount);
        } else {
            wrapper.orderByDesc(Spu::getCreateTime);
        }

        return this.page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
    }

    @Override
    public SpuVO getDetailById(Long id) {
        String cacheKey = CacheConstants.SPU_DETAIL + "::" + id;

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

        // 新增商品后清除热门分页缓存
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
        BeanUtil.copyProperties(sku, vo);
        if (StrUtil.isNotBlank(sku.getSpecInfo())) {
            vo.setSpecInfo(JSONUtil.toBean(sku.getSpecInfo(), java.util.Map.class));
        }
        return vo;
    }
}

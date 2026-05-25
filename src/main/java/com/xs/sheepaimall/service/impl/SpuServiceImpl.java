package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.BizException;
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

    @Override
    public Page<Spu> pageQuery(SpuQueryDTO dto) {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<Spu>()
                .eq(dto.getCategoryId() != null, Spu::getCategoryId, dto.getCategoryId())
                .like(StrUtil.isNotBlank(dto.getKeyword()), Spu::getName, dto.getKeyword())
                .eq(dto.getStatus() != null, Spu::getStatus, dto.getStatus());

        // 排序
        if ("sales_count".equals(dto.getOrderBy())) {
            wrapper.orderByDesc(Spu::getSalesCount);
        } else {
            wrapper.orderByDesc(Spu::getCreateTime);
        }

        return this.page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
    }

    @Override
    public SpuVO getDetailById(Long id) {
        Spu spu = this.getById(id);
        if (spu == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在");
        }

        SpuVO vo = toSpuVO(spu);

        // 关联分类名称
        Category category = categoryService.getById(spu.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getName());
        }

        // 关联SKU列表
        List<Sku> skuList = skuService.listBySpuId(spu.getId());
        vo.setSkuList(skuList.stream().map(this::toSkuVO).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuVO saveWithSku(SpuSaveDTO dto) {
        Spu spu = new Spu();
        BeanUtil.copyProperties(dto, spu);
        // imageList → JSON 字符串存储
        if (dto.getImageList() != null) {
            spu.setImageList(JSONUtil.toJsonStr(dto.getImageList()));
        }
        this.save(spu);

        // 保存SKU列表
        if (dto.getSkuList() != null && !dto.getSkuList().isEmpty()) {
            List<Sku> skuList = dto.getSkuList().stream()
                    .map(this::toSkuEntity)
                    .collect(Collectors.toList());
            skuList.forEach(s -> s.setSpuId(spu.getId()));
            skuService.batchSaveOrUpdate(spu.getId(), skuList);
        }

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

        // 全量替换SKU
        if (dto.getSkuList() != null) {
            List<Sku> skuList = dto.getSkuList().stream()
                    .map(this::toSkuEntity)
                    .collect(Collectors.toList());
            skuList.forEach(s -> s.setSpuId(spu.getId()));
            skuService.batchSaveOrUpdate(spu.getId(), skuList);
        }

        return getDetailById(spu.getId());
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        Spu spu = new Spu();
        spu.setId(id);
        spu.setStatus(status);
        return this.updateById(spu);
    }

    /** DTO → Sku Entity */
    private Sku toSkuEntity(SkuSaveDTO dto) {
        Sku sku = new Sku();
        BeanUtil.copyProperties(dto, sku);
        if (dto.getSpecInfo() != null) {
            sku.setSpecInfo(JSONUtil.toJsonStr(dto.getSpecInfo()));
        }
        return sku;
    }

    /** Spu Entity → VO */
    private SpuVO toSpuVO(Spu spu) {
        SpuVO vo = new SpuVO();
        BeanUtil.copyProperties(spu, vo);
        if (StrUtil.isNotBlank(spu.getImageList())) {
            vo.setImageList(JSONUtil.toList(spu.getImageList(), String.class));
        }
        return vo;
    }

    /** Sku Entity → VO */
    private SkuVO toSkuVO(Sku sku) {
        SkuVO vo = new SkuVO();
        BeanUtil.copyProperties(sku, vo);
        if (StrUtil.isNotBlank(sku.getSpecInfo())) {
            vo.setSpecInfo(JSONUtil.toBean(sku.getSpecInfo(), java.util.Map.class));
        }
        return vo;
    }
}

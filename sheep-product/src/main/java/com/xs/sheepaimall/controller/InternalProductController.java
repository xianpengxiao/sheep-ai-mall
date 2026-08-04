package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.dto.SpuSaveDTO;
import com.xs.sheepaimall.dto.StockReleaseDTO;
import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.mapper.*;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.vo.SpuVO;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品服务内部控制器（供 Feign 调用）
 */
@RestController
@RequestMapping("/internal/product")
public class InternalProductController {

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private SpuService spuService;

    @Autowired
    private SkuService skuService;

    @GetMapping("/sku/{id}")
    public Sku getSkuById(@PathVariable Long id) {
        return skuMapper.selectById(id);
    }

    @GetMapping("/sku/list-by-ids")
    public List<Sku> listSkuByIds(@RequestParam List<Long> ids) {
        return skuMapper.selectBatchIds(ids);
    }

    @GetMapping("/spu/{id}")
    public Spu getSpuById(@PathVariable Long id) {
        return spuMapper.selectById(id);
    }

    @GetMapping("/spu/list-by-ids")
    public List<Spu> listSpuByIds(@RequestParam List<Long> ids) {
        return spuMapper.selectBatchIds(ids);
    }

    @GetMapping("/spu/list-ids-by-merchant/{merchantId}")
    public List<Long> listSpuIdsByMerchant(@PathVariable Long merchantId) {
        return spuMapper.selectList(
                new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getMerchantId, merchantId)
                        .select(Spu::getId)
        ).stream().map(Spu::getId).collect(Collectors.toList());
    }

    @GetMapping("/spu/list-ids-by-keyword")
    public List<Long> listSpuIdsByKeyword(@RequestParam String keyword) {
        return spuMapper.selectList(
                new LambdaQueryWrapper<Spu>()
                        .like(Spu::getName, keyword)
                        .select(Spu::getId)
        ).stream().map(Spu::getId).collect(Collectors.toList());
    }

    @GetMapping("/category/{id}")
    public Category getCategoryById(@PathVariable Long id) {
        return categoryMapper.selectById(id);
    }

    @PostMapping("/stock/release")
    public void releaseStock(@RequestBody StockReleaseDTO dto) {
        for (StockReleaseDTO.Item item : dto.getItems()) {
            skuMapper.update(null, new LambdaUpdateWrapper<Sku>()
                    .eq(Sku::getId, item.getSkuId())
                    .setSql("stock = stock + " + item.getQuantity()));
            spuMapper.update(null, new LambdaUpdateWrapper<Spu>()
                    .eq(Spu::getId, item.getSpuId())
                    .setSql("sales_count = sales_count - " + item.getQuantity()));
        }
    }

    // ========== 以下为 Merchant 模块 Feign 调用所需端点 ==========

    @PostMapping("/spu")
    public SpuVO saveSpu(@RequestBody SpuSaveDTO dto) {
        return spuService.saveWithSku(dto);
    }

    @PutMapping("/spu")
    public SpuVO updateSpu(@RequestBody SpuSaveDTO dto) {
        return spuService.updateWithSku(dto);
    }

    @GetMapping("/spu/detail/{id}")
    public SpuVO getSpuDetail(@PathVariable Long id) {
        return spuService.getDetailById(id);
    }

    @PutMapping("/spu/{id}/status")
    public void updateSpuStatus(@PathVariable Long id, @RequestParam Integer status) {
        spuService.updateStatus(id, status);
    }

    /**
     * 按商家分页查询 SPU（商家后台用）
     */
    @GetMapping("/spu/page-by-merchant")
    public Page<Spu> pageSpuByMerchant(
            @RequestParam Long merchantId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<Spu>()
                .eq(Spu::getMerchantId, merchantId)
                .eq(categoryId != null, Spu::getCategoryId, categoryId)
                .eq(status != null, Spu::getStatus, status)
                .like(StrUtil.isNotBlank(keyword), Spu::getName, keyword)
                .orderByDesc(Spu::getCreateTime);
        return spuMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @GetMapping("/sku/list-by-spu-ids")
    public List<Sku> listSkuBySpuIds(@RequestParam List<Long> spuIds) {
        return skuMapper.selectList(
                new LambdaQueryWrapper<Sku>().in(Sku::getSpuId, spuIds));
    }

    @PostMapping("/sku/batch-save/{spuId}")
    public void batchSaveSku(@PathVariable Long spuId, @RequestBody List<Sku> skuList) {
        skuService.batchSaveOrUpdate(spuId, skuList);
    }

    @GetMapping("/category/list-by-ids")
    public List<Category> listCategoryByIds(@RequestParam List<Long> ids) {
        return categoryMapper.selectBatchIds(ids);
    }
}

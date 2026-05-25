package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.SkuSaveDTO;
import com.xs.sheepaimall.dto.StockAdjustDTO;
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.service.SkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import cn.hutool.json.JSONUtil;

import java.util.List;

@Tag(name = "商品SKU", description = "商品库存量单位管理")
@Validated
@RestController
@RequestMapping("/api/sku")
public class SkuController {

    @Resource
    private SkuService skuService;

    @Operation(summary = "根据SPU ID查询SKU列表")
    @GetMapping("/spu/{spuId}")
    public R<List<Sku>> listBySpuId(@Parameter(description = "SPU ID") @PathVariable Long spuId) {
        return R.ok(skuService.listBySpuId(spuId));
    }

    @Operation(summary = "查询SKU详情")
    @GetMapping("/{id}")
    public R<Sku> getById(@Parameter(description = "SKU ID") @PathVariable Long id) {
        return R.ok(skuService.getById(id));
    }

    @Operation(summary = "新增SKU")
    @PostMapping
    public R<Sku> save(@Valid @RequestBody SkuSaveDTO dto) {
        Sku sku = new Sku();
        sku.setSpuId(dto.getSpuId());
        sku.setSkuCode(dto.getSkuCode());
        sku.setSkuName(dto.getSkuName());
        if (dto.getSpecInfo() != null) {
            sku.setSpecInfo(JSONUtil.toJsonStr(dto.getSpecInfo()));
        }
        sku.setPrice(dto.getPrice());
        sku.setStock(dto.getStock());
        sku.setImage(dto.getImage());
        sku.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        skuService.save(sku);
        return R.ok(sku);
    }

    @Operation(summary = "修改SKU规格信息")
    @PutMapping("/{id}")
    public R<Object> update(@Parameter(description = "SKU ID") @PathVariable Long id, @Valid @RequestBody SkuSaveDTO dto) {
        dto.setId(id);
        Sku sku = new Sku();
        sku.setId(id);
        sku.setSkuCode(dto.getSkuCode());
        sku.setSkuName(dto.getSkuName());
        if (dto.getSpecInfo() != null) {
            sku.setSpecInfo(JSONUtil.toJsonStr(dto.getSpecInfo()));
        }
        sku.setPrice(dto.getPrice());
        sku.setStock(dto.getStock());
        sku.setImage(dto.getImage());
        sku.setStatus(dto.getStatus());
        skuService.updateById(sku);
        return R.ok();
    }

    @Operation(summary = "库存调整", description = "正数为入库，负数为出库")
    @PutMapping("/stock/adjust")
    public R<Object> adjustStock(@Valid @RequestBody StockAdjustDTO dto) {
        if (dto.getDelta() > 0) {
            Sku sku = skuService.getById(dto.getSkuId());
            if (sku == null) {
                return R.fail("SKU不存在");
            }
            sku.setStock(sku.getStock() + dto.getDelta());
            skuService.updateById(sku);
        } else if (dto.getDelta() < 0) {
            skuService.deductStock(dto.getSkuId(), Math.abs(dto.getDelta()));
        }
        return R.ok();
    }

    @Operation(summary = "删除SKU", description = "逻辑删除")
    @DeleteMapping("/{id}")
    public R<Object> delete(@Parameter(description = "SKU ID") @PathVariable Long id) {
        skuService.removeById(id);
        return R.ok();
    }
}

package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.SpuQueryDTO;
import com.xs.sheepaimall.dto.SpuSaveDTO;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.vo.SpuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "商品SPU", description = "商品标准产品单元管理")
@Validated
@RestController
@RequestMapping("/api/spu")
public class SpuController {

    @Resource
    private SpuService spuService;

    @Operation(summary = "分页查询SPU")
    @GetMapping("/page")
    public R<Page<Spu>> page(SpuQueryDTO dto) {
        return R.ok(spuService.pageQuery(dto));
    }

    @Operation(summary = "查询SPU详情", description = "含SKU列表")
    @GetMapping("/{id}")
    public R<SpuVO> detail(@Parameter(description = "SPU ID") @PathVariable Long id) {
        return R.ok(spuService.getDetailById(id));
    }

    @Operation(summary = "新增SPU", description = "含SKU列表")
    @PostMapping
    public R<SpuVO> save(@Valid @RequestBody SpuSaveDTO dto) {
        return R.ok(spuService.saveWithSku(dto));
    }

    @Operation(summary = "编辑SPU", description = "含SKU列表")
    @PutMapping("/{id}")
    public R<SpuVO> update(@Parameter(description = "SPU ID") @PathVariable Long id, @Valid @RequestBody SpuSaveDTO dto) {
        dto.setId(id);
        return R.ok(spuService.updateWithSku(dto));
    }

    @Operation(summary = "上架/下架SPU")
    @PutMapping("/{id}/status/{status}")
    public R<Object> updateStatus(
            @Parameter(description = "SPU ID") @PathVariable Long id,
            @Parameter(description = "状态: 1=上架 0=下架") @PathVariable Integer status) {
        spuService.updateStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "删除SPU", description = "逻辑删除")
    @DeleteMapping("/{id}")
    public R<Object> delete(@Parameter(description = "SPU ID") @PathVariable Long id) {
        spuService.removeById(id);
        return R.ok();
    }
}

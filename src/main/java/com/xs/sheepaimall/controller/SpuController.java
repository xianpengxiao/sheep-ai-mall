package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.SpuQueryDTO;
import com.xs.sheepaimall.dto.SpuSaveDTO;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.vo.SpuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
@Tag(name = "商品spu")
@Validated
@RestController
@RequestMapping("/api/spu")
public class SpuController {

    @Autowired
    private SpuService spuService;

    @Operation(summary = "分页查询SPU")
    @GetMapping("/page")
    public R<Page<Spu>> page(SpuQueryDTO dto) {
        return R.ok(spuService.pageQuery(dto));
    }

    @Operation(summary = "查询SPU详情", description = "含SKU列表，仅返回审核通过的商品")
    @GetMapping("/{id}")
    public R<SpuVO> detail(@Parameter(description = "SPU ID") @PathVariable Long id) {
        Spu spu = spuService.getById(id);
        if (spu == null || spu.getAuditStatus() != 1) {
            throw new BizException("商品不存在或未上架");
        }
        return R.ok(spuService.getDetailById(id));
    }

    @Operation(summary = "新增SPU", description = "含SKU列表")
    @PostMapping
    @RequirePermission("spu:create")
    public R<SpuVO> save(@Valid @RequestBody SpuSaveDTO dto) {
        return R.ok(spuService.saveWithSku(dto));
    }

    @Operation(summary = "编辑SPU", description = "含SKU列表")
    @PutMapping("/{id}")
    @RequirePermission("spu:update")
    public R<SpuVO> update(@Parameter(description = "SPU ID") @PathVariable Long id, @Valid @RequestBody SpuSaveDTO dto) {
        dto.setId(id);
        return R.ok(spuService.updateWithSku(dto));
    }

    @Operation(summary = "上架/下架SPU")
    @PutMapping("/{id}/status/{status}")
    @RequirePermission("spu:update")
    public R<Object> updateStatus(
            @Parameter(description = "SPU ID") @PathVariable Long id,
            @Parameter(description = "状态: 1=上架 0=下架") @PathVariable Integer status) {
        spuService.updateStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "热门商品分页", description = "按销量降序，仅返回上架商品")
    @GetMapping("/hot")
    public R<Page<Spu>> hot(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(spuService.pageHotProducts(pageNum, pageSize));
    }

    @Operation(summary = "删除SPU", description = "逻辑删除")
    @DeleteMapping("/{id}")
    @RequirePermission("spu:delete")
    public R<Object> delete(@Parameter(description = "SPU ID") @PathVariable Long id) {
        spuService.removeById(id);
        return R.ok();
    }
}

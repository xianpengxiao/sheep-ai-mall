package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.entity.Category;
import com.xs.sheepaimall.service.CategoryService;
import com.xs.sheepaimall.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Tag(name = "商品分类", description = "商品分类树查询、增删改查")
@Validated
@RestController
@RequestMapping("/api/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @Operation(summary = "查询分类树", description = "返回完整分类树结构")
    @GetMapping("/tree")
    public R<List<CategoryVO>> tree() {
        return R.ok(categoryService.getTree());
    }

    @Operation(summary = "根据父ID查询子分类", description = "查询指定父分类下的直接子分类列表")
    @GetMapping("/children/{parentId}")
    public R<List<CategoryVO>> children(@Parameter(description = "父分类ID") @PathVariable Long parentId) {
        return R.ok(categoryService.listByParentId(parentId));
    }

    @Operation(summary = "查询分类详情")
    @GetMapping("/{id}")
    public R<Category> getById(@Parameter(description = "分类ID") @PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public R<Object> save(@Valid @RequestBody CategorySaveRequest req) {
        Category category = new Category();
        category.setParentId(req.getParentId() != null ? req.getParentId() : 0);
        category.setName(req.getName());
        category.setIcon(req.getIcon());
        category.setStatus(req.getStatus());
        categoryService.saveOrUpdateCategory(category);
        return R.ok();
    }

    @Operation(summary = "编辑分类")
    @PutMapping("/{id}")
    public R<Object> update(@Parameter(description = "分类ID") @PathVariable Long id, @Valid @RequestBody CategorySaveRequest req) {
        Category category = new Category();
        category.setId(id);
        category.setParentId(req.getParentId());
        category.setName(req.getName());
        category.setIcon(req.getIcon());
        category.setSortOrder(req.getSortOrder());
        category.setStatus(req.getStatus());
        categoryService.updateById(category);
        return R.ok();
    }

    @Operation(summary = "删除分类", description = "逻辑删除，标记 deleted=1")
    @DeleteMapping("/{id}")
    public R<Object> delete(@Parameter(description = "分类ID") @PathVariable Long id) {
        categoryService.removeById(id);
        return R.ok();
    }

    // ============== 内嵌请求体 ==============

    @lombok.Data
    public static class CategorySaveRequest {

        @Schema(description = "父分类ID")
        private Long parentId;

        @Schema(description = "分类名称")
        @NotBlank(message = "分类名称不能为空")
        private String name;

        @Schema(description = "图标")
        private String icon;

        @Schema(description = "排序序号")
        private Integer sortOrder;

        @Schema(description = "状态：1=启用 0=禁用")
        private Integer status;
    }
}

package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 分类新增/编辑请求 */
@Data
@Schema(description = "分类新增/编辑请求")
public class CategorySaveDTO {

    @Schema(description = "分类ID，编辑时传入")
    private Long id;

    @Schema(description = "父分类ID")
    private Long parentId;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "状态：1=启用 0=禁用")
    private Integer status;
}

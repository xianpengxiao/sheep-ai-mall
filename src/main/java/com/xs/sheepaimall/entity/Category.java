package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品分类 */
@Data
@TableName("category")
public class Category {

    @Schema(description = "分类ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "父分类ID，0=根节点")
    private Long parentId;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "状态：1=启用 0=禁用")
    private Integer status;

    @Schema(description = "逻辑删除：0=否 1=是")
    @TableLogic
    private Integer deleted;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

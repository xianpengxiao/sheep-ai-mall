package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统权限（菜单/按钮/接口）
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    @Schema(description = "权限ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "父权限ID 0=顶级权限/菜单")
    private Long parentId;

    @Schema(description = "权限标识 如 spu:create spu:delete")
    private String permCode;

    @Schema(description = "权限名称 如 新增商品")
    private String permName;

    @Schema(description = "类型：1=菜单 2=按钮 3=接口")
    private Integer permType;

    @Schema(description = "前端路由/接口路径")
    private String path;

    @Schema(description = "菜单图标")
    private String icon;

    @Schema(description = "排序值 越小越前")
    private Integer sortOrder;

    @Schema(description = "状态 0禁用 1正常")
    private Integer status;

    @Schema(description = "逻辑删除 0未删除 1已删除")
    @TableLogic
    private Integer deleted;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

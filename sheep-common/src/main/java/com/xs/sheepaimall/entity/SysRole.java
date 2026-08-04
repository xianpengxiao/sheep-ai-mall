package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色
 */
@Data
@TableName("sys_role")
public class SysRole {

    @Schema(description = "角色ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "角色编码(权限标识) 如 ROLE_ADMIN")
    private String roleCode;

    @Schema(description = "角色名称 如 管理员")
    private String roleName;

    @Schema(description = "角色描述")
    private String description;

    @Schema(description = "状态 0禁用 1正常")
    private Integer status;

    @Schema(description = "排序值 越小越前")
    private Integer sortOrder;

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

package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色-权限关联
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @Schema(description = "关联ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "权限ID")
    private Long permissionId;
}

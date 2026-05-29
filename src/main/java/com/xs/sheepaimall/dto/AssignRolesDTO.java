package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 分配角色入参
 */
@Data
@Schema(description = "分配角色请求参数")
public class AssignRolesDTO {

    @NotNull(message = "角色ID列表不能为null（可为空数组表示清空所有角色）")
    @Schema(description = "角色ID列表，传空数组表示清空该用户所有角色", example = "[1, 2]")
    private List<Long> roleIds;
}

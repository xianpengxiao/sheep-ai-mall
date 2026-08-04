package com.xs.sheepaimall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xs.sheepaimall.entity.SysUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联 Mapper
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 根据用户ID查询角色编码列表
     */
    @Select("SELECT r.role_code FROM sys_user_role ur " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(Long userId);

    /**
     * 根据用户ID查询角色ID列表
     */
    @Select("SELECT ur.role_id FROM sys_user_role ur " +
            "INNER JOIN sys_role r ON ur.role_id = r.id AND r.status = 1 " +
            "WHERE ur.user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 删除用户的所有角色关联
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 查询用户角色的最小排序值（值越小权限越高）
     */
    @Select("SELECT MIN(r.sort_order) FROM sys_user_role ur " +
            "INNER JOIN sys_role r ON ur.role_id = r.id AND r.status = 1 " +
            "WHERE ur.user_id = #{userId}")
    Integer selectMinSortOrderByUserId(@Param("userId") Long userId);
}

package com.xs.sheepaimall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xs.sheepaimall.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统角色 Mapper
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 批量查询角色的 sortOrder（值越小权限越高）
     */
    @Select({
            "<script>",
            "SELECT r.sort_order FROM sys_role r WHERE r.status = 1 AND r.id IN",
            "<foreach item='id' collection='roleIds' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    List<Integer> selectSortOrdersByRoleIds(List<Long> roleIds);
}

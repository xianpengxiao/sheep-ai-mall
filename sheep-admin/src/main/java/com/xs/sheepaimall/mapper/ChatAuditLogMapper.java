package com.xs.sheepaimall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xs.sheepaimall.entity.ChatAuditLog;
import org.apache.ibatis.annotations.Mapper;

/** 客服审计日志 Mapper */
@Mapper
public interface ChatAuditLogMapper extends BaseMapper<ChatAuditLog> {
}

package com.xs.sheepaimall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xs.sheepaimall.entity.ChatPendingOp;
import org.apache.ibatis.annotations.Mapper;

/** 客服待确认操作单 Mapper */
@Mapper
public interface ChatPendingOpMapper extends BaseMapper<ChatPendingOp> {
}

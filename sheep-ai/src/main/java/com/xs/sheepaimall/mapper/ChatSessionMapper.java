package com.xs.sheepaimall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xs.sheepaimall.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/** 客服会话 Mapper */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}

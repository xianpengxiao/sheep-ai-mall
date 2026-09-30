package com.xs.sheepaimall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xs.sheepaimall.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** 客服消息 Mapper */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}

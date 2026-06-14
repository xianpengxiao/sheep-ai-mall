package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作审计日志 */
@Data
@TableName("oper_log")
public class OperLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String username;

    private String operation;

    private String targetType;

    private Long targetId;

    private String detail;

    /** 仅插入时填充，无更新时间字段 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服账号 */
@Data
@TableName("cs_agent")
@Schema(description = "客服账号")
public class CsAgent {

    @Schema(description = "客服ID(关联sys_user.id)")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "sys_user.id")
    private Long userId;

    @Schema(description = "客服名称")
    private String name;

    @Schema(description = "状态：0=离线 1=在线 2=忙碌")
    private Integer status;

    @Schema(description = "最大同时接待数")
    private Integer maxConcurrent;

    @Schema(description = "当前接待数")
    private Integer currentCount;

    @Schema(description = "累计会话数")
    private Integer totalSessions;

    @Schema(description = "平均评分")
    private java.math.BigDecimal avgRating;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

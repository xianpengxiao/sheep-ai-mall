package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服全局配置（单行表）：用户日限、上下文/输出预算、额度总开关 */
@Data
@TableName("cs_agent_config")
@Schema(description = "客服全局配置")
public class CsAgentConfig {

    @Schema(description = "配置ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "用户日请求上限")
    private Integer userDailyRequestLimit;

    @Schema(description = "用户日Token上限")
    private Integer userDailyTokenLimit;

    @Schema(description = "上下文Token预算（字符/4估算，超出从旧消息丢弃）")
    private Integer maxContextTokens;

    @Schema(description = "单次输出Token上限")
    private Integer maxOutputTokens;

    @Schema(description = "额度开关：0=关闭(全部放行) 1=启用")
    private Integer quotaEnabled;

    @Schema(description = "AI自动接待总开关：0=全部人工 1=按商家/平台各自开关")
    private Integer aiEnabled;

    @Schema(description = "平台会话AI接待开关：0=官方客服默认人工 1=官方客服新会话AI接待（单会话仍可由坐席手动转接）")
    private Integer platformAiEnabled;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

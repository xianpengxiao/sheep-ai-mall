package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户画像 */
@Data
@TableName("chat_user_profile")
@Schema(description = "用户画像")
public class ChatUserProfile {

    @Schema(description = "画像ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "标签JSON数组")
    private String tags;

    @Schema(description = "累计消息数")
    private Integer totalMessages;

    @Schema(description = "累计会话数")
    private Integer totalSessions;

    @Schema(description = "最近活跃时间")
    private LocalDateTime lastActiveAt;

    @Schema(description = "AI生成的画像摘要")
    private String summary;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** RAG知识库文档 */
@Data
@TableName("chat_rag_doc")
@Schema(description = "RAG知识库文档")
public class ChatRagDoc {

    @Schema(description = "文档ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "关键词，逗号分隔")
    private String keywords;

    @Schema(description = "分类：faq/product/policy")
    private String category;

    @Schema(description = "归属商家ID，null=平台公共文档")
    private Long merchantId;

    @Schema(description = "启用状态：0=禁用 1=启用")
    private Integer enabled;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "向量嵌入状态（瞬态，仅写操作响应返回）：null=成功，其他为未同步/失败原因")
    @TableField(exist = false)
    private String embedStatus;
}

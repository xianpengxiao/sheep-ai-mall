package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 知识库文档新增/编辑请求（平台管理员可指定归属商家，商家端自动归属当前商家） */
@Data
@Schema(description = "知识库文档新增/编辑请求")
public class KnowledgeDocSaveDTO {

    @Schema(description = "文档ID，编辑时传入")
    private Long id;

    @Schema(description = "文档标题")
    @NotBlank(message = "文档标题不能为空")
    private String title;

    @Schema(description = "文档内容")
    @NotBlank(message = "文档内容不能为空")
    private String content;

    @Schema(description = "关键词，逗号分隔")
    private String keywords;

    @Schema(description = "分类：faq/product/policy")
    private String category;

    @Schema(description = "归属商家ID，仅平台管理员可指定；null=平台公共文档")
    private Long merchantId;
}

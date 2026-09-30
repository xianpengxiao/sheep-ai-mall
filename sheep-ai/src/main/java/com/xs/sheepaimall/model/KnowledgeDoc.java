package com.xs.sheepaimall.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 知识库文档（PostgreSQL ai_knowledge_doc 表） */
@Data
public class KnowledgeDoc {

    private Long id;
    private String ownerType;
    private Long ownerId;
    private String title;
    private String content;
    private String keywords;
    private String category;
    private Integer status;
    private Double similarity;      // 向量相似度（查询时计算）
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

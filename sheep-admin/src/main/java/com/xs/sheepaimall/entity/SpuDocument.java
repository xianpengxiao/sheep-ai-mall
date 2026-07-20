package com.xs.sheepaimall.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品 SPU 搜索文档（Elasticsearch 索引映射） */
@Data
@Document(indexName = "spu_index")
/**
 忽略 ES 自动添加的 _class 未知字段
 **/
@JsonIgnoreProperties(ignoreUnknown = true)
@Setting(
        shards = 3,
        replicas = 1,
        refreshInterval = "1s"
)
public class SpuDocument {

    @Id
    private Long id;

    /** 商品名称 — ik 中文分词，boost=3 提高匹配权重 */
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String name;

    /** 副标题 — ik 分词 */
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String subTitle;

    /** 商品描述 — ik 分词 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String description;

    /** 品牌 — 精确匹配 */
    @Field(type = FieldType.Keyword)
    private String brand;

    /** 主图 */
    @Field(type = FieldType.Keyword, index = false)
    private String mainImage;

    /** 分类 ID */
    @Field(type = FieldType.Long)
    private Long categoryId;

    /** 分类名称 — 精确匹配 */
    @Field(type = FieldType.Keyword)
    private String categoryName;

    /** 最低售价（SKU聚合） */
    @Field(type = FieldType.Double)
    private BigDecimal minPrice;

    /** 最高售价（SKU聚合） */
    @Field(type = FieldType.Double)
    private BigDecimal maxPrice;

    /** 销量 */
    @Field(type = FieldType.Integer)
    private Integer salesCount;

    /** 状态：1=上架 0=下架 */
    @Field(type = FieldType.Integer)
    private Integer status;

    /** 创建时间 */
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}

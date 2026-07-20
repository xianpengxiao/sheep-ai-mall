package com.xs.sheepaimall.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Document(indexName = "merchant_index")
@JsonIgnoreProperties(ignoreUnknown = true)
@Setting(shards = 3, replicas = 1, refreshInterval = "1s")
public class MerchantDocument {

    @Id
    private Long id;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String shopName;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String shopDesc;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String businessScope;

    @Field(type = FieldType.Keyword, index = false)
    private String shopLogo;

    @Field(type = FieldType.Integer)
    private Integer shopStatus;

    @Field(type = FieldType.Integer)
    private Integer status;

    @Field(type = FieldType.Double)
    private BigDecimal describeScore;

    @Field(type = FieldType.Double)
    private BigDecimal serviceScore;

    @Field(type = FieldType.Double)
    private BigDecimal logisticsScore;

    @Field(type = FieldType.Double)
    private BigDecimal compositeScore;

    @Field(type = FieldType.Integer)
    private Integer dsrCount;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}

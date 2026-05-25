package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 商品SPU新增/编辑请求 */
@Data
@Schema(description = "商品SPU新增/编辑请求")
public class SpuSaveDTO {

    @Schema(description = "SPU ID，编辑时传入")
    private Long id;

    @Schema(description = "所属分类ID")
    @NotNull(message = "商品分类不能为空")
    private Long categoryId;

    @Schema(description = "商品名称")
    @NotBlank(message = "商品名称不能为空")
    private String name;

    @Schema(description = "副标题")
    private String subTitle;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "商品描述")
    private String description;

    @Schema(description = "主图URL")
    private String mainImage;

    @Schema(description = "图片URL列表")
    private List<String> imageList;

    @Schema(description = "状态：1=上架 0=下架")
    private Integer status;

    @Schema(description = "同时保存的SKU列表")
    private List<SkuSaveDTO> skuList;
}

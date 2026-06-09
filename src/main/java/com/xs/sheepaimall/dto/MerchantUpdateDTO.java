package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 商家信息修改 */
@Data
@Schema(description = "商家信息修改")
public class MerchantUpdateDTO {

    // ===== B类字段（展示类，机审后直接生效） =====

    @Schema(description = "店铺名称")
    @Size(max = 50, message = "店铺名称不超过50字")
    private String shopName;

    @Schema(description = "店铺logo")
    private String shopLogo;

    @Schema(description = "店铺简介")
    @Size(max = 200, message = "店铺简介不超过200字")
    private String shopDesc;

    @Schema(description = "店铺公告")
    @Size(max = 200, message = "店铺公告不超过200字")
    private String shopNotice;

    @Schema(description = "营业时间")
    @Size(max = 100, message = "营业时间不超过100字")
    private String businessHours;

    @Schema(description = "售后说明")
    @Size(max = 200, message = "售后说明不超过200字")
    private String afterSaleInfo;

    // ===== A类字段（资质类，需人工审核） =====

    @Schema(description = "营业执照图片URL")
    private String businessLicense;

    @Schema(description = "食品经营许可证")
    private String foodLicense;

    @Schema(description = "经营范围")
    private String businessScope;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "法人信息")
    private String legalPerson;

    @Schema(description = "经营地址")
    private String businessAddress;

    @Schema(description = "实名联系方式")
    private String verifiedContact;
}

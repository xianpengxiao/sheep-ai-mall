package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 商家信息修改 */
@Data
@Schema(description = "商家信息修改")
public class MerchantUpdateDTO {

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "店铺logo")
    private String shopLogo;

    @Schema(description = "营业执照图片URL")
    private String businessLicense;

    @Schema(description = "经营范围")
    private String businessScope;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;
}

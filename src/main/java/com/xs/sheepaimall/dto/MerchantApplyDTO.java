package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 商家入驻申请 */
@Data
@Schema(description = "商家入驻申请")
public class MerchantApplyDTO {

    @NotBlank(message = "店铺名称不能为空")
    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "营业执照图片URL")
    private String businessLicense;

    @Schema(description = "经营范围")
    private String businessScope;

    @NotBlank(message = "联系人不能为空")
    @Schema(description = "联系人姓名")
    private String contactName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "联系电话")
    private String contactPhone;
}

package com.xs.sheepaimall.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 收货地址返回 */
@Data
@Schema(description = "收货地址")
public class ShippingAddressVO {

    @Schema(description = "地址ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "收货人姓名")
    private String receiverName;

    @Schema(description = "收货人手机号")
    private String receiverPhone;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "区县")
    private String district;

    @Schema(description = "详细地址")
    private String detailAddress;

    @Schema(description = "邮政编码")
    private String zipCode;

    @Schema(description = "是否默认地址：0=否 1=是")
    private Integer isDefault;
}

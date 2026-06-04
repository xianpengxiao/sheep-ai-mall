package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 商家信息返回 */
@Data
@Schema(description = "商家信息")
public class MerchantVO {

    @Schema(description = "商家ID")
    private Long id;

    @Schema(description = "关联用户ID")
    private Long userId;

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

    @Schema(description = "状态 0待审核 1已开通 2已关闭")
    private Integer status;

    @Schema(description = "审核驳回原因")
    private String auditRemark;

    @Schema(description = "店铺在售商品列表（商家详情时返回）")
    private List<SpuVO> goodsList;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

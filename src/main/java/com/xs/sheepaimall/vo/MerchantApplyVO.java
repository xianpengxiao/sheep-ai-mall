package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 商家入驻申请返回 */
@Data
@Schema(description = "商家入驻申请")
public class MerchantApplyVO {

    @Schema(description = "申请ID")
    private Long id;

    @Schema(description = "申请人用户ID")
    private Long userId;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "营业执照图片URL")
    private String businessLicense;

    @Schema(description = "经营范围")
    private String businessScope;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "审核状态 0待审核 1通过 2驳回")
    private Integer status;

    @Schema(description = "审核意见/驳回原因")
    private String auditRemark;

    @Schema(description = "审核人用户ID")
    private Long auditUserId;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

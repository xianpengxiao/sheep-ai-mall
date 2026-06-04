package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 商家入驻申请表 */
@Data
@TableName("merchant_apply")
public class MerchantApply {

    @Schema(description = "申请ID")
    @TableId(type = IdType.ASSIGN_ID)
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

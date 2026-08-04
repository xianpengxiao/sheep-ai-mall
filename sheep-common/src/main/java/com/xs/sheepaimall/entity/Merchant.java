package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 商家 */
@Data
@TableName("merchant")
public class Merchant {

    @Schema(description = "商家ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "关联用户ID")
    private Long userId;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "店铺logo")
    private String shopLogo;

    @Schema(description = "店铺简介")
    private String shopDesc;

    @Schema(description = "店铺公告")
    private String shopNotice;

    @Schema(description = "营业时间")
    private String businessHours;

    @Schema(description = "售后说明")
    private String afterSaleInfo;

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

    @Schema(description = "状态 0待审核 1已开通 2已关闭")
    private Integer status;

    @Schema(description = "营业状态 0已打烊 1营业中")
    private Integer shopStatus;

    @Schema(description = "审核驳回原因")
    private String auditRemark;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

package com.xs.sheepaimall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 商家信息变更审核记录 */
@Data
@TableName("merchant_info_change")
public class MerchantInfoChange {

    @Schema(description = "申请ID")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "商家ID")
    private Long merchantId;

    // ===== A类字段（需人工审核） =====

    @Schema(description = "营业执照")
    private String businessLicense;

    @Schema(description = "食品经营许可证")
    private String foodLicense;

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

    @Schema(description = "经营范围")
    private String businessScope;

    // ===== 变更字段标记 =====

    @Schema(description = "变更字段列表 JSON")
    private String changedFields;

    // ===== 审核 =====

    @Schema(description = "审核状态 0待审核 1通过 2驳回")
    private Integer auditStatus;

    @Schema(description = "驳回原因")
    private String auditMsg;

    @Schema(description = "审核人")
    private Long auditUserId;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

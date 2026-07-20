package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 商家信息变更审核记录 VO */
@Data
@Schema(description = "商家信息变更审核记录")
public class MerchantInfoChangeVO {

    @Schema(description = "申请ID")
    private Long id;

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "商家名称")
    private String shopName;

    // ===== A类字段 =====

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

    @Schema(description = "变更字段列表")
    private String changedFields;

    // ===== 审核 =====

    @Schema(description = "审核状态 0待审核 1通过 2驳回")
    private Integer auditStatus;

    @Schema(description = "审核状态文本")
    private String auditStatusText;

    @Schema(description = "驳回原因")
    private String auditMsg;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;
}

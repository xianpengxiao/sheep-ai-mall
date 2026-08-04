package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商家提现申请VO */
@Data
@Schema(description = "商家提现申请")
public class WithdrawVO {

    @Schema(description = "提现ID")
    private Long id;

    @Schema(description = "商家ID")
    private Long merchantId;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "提现单号")
    private String withdrawNo;

    @Schema(description = "提现金额")
    private BigDecimal amount;

    @Schema(description = "手续费")
    private BigDecimal fee;

    @Schema(description = "实际到账")
    private BigDecimal actualAmount;

    @Schema(description = "账户类型")
    private String accountType;

    @Schema(description = "脱敏账户信息")
    private String accountInfo;

    @Schema(description = "状态 0待审核 1待打款 2已打款 3已驳回")
    private Integer status;

    @Schema(description = "状态文本")
    private String statusText;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "审核人")
    private String auditUserName;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "打款时间")
    private LocalDateTime finishTime;

    @Schema(description = "申请时间")
    private LocalDateTime createTime;
}

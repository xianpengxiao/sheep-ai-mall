package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 实名&安全资料响应（已脱敏）
 */
@Data
@Builder
@Schema(description = "实名&安全资料响应")
public class SecurityProfileVO {

    @Schema(description = "实名状态 true=已实名")
    private Boolean realNameAuth;

    @Schema(description = "真实姓名（已脱敏，如 张**）")
    private String realName;

    @Schema(description = "身份证号（已脱敏，如 110101******1234）")
    private String idCard;

    @Schema(description = "手机号（已脱敏，如 138****5678）")
    private String phone;

    @Schema(description = "邮箱（已脱敏，如 ab***@example.com）")
    private String email;

    @Schema(description = "是否绑定手机号")
    private Boolean phoneBound;

    @Schema(description = "是否绑定邮箱")
    private Boolean emailBound;

    @Schema(description = "资料是否完善（实名+手机+邮箱均完成）")
    private Boolean profileComplete;
}

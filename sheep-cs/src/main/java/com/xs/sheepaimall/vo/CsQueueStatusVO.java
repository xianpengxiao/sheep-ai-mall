package com.xs.sheepaimall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "排队状态")
public class CsQueueStatusVO {

    @Schema(description = "是否在排队")
    private boolean queuing;

    @Schema(description = "排队位置（第几位）")
    private int position;

    @Schema(description = "预计等待时长(秒)")
    private int estimatedWaitSeconds;

    @Schema(description = "前方等待人数")
    private int aheadCount;
}

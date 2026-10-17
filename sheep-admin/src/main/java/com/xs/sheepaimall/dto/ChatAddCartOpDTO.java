package com.xs.sheepaimall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 商品卡片"加入购物车"操作单创建请求（仅生成待确认操作单，确认后才真正加购） */
@Data
@Schema(description = "加购操作单创建请求")
public class ChatAddCartOpDTO {

    @Schema(description = "SKU ID（商品规格ID）")
    @NotNull(message = "skuId 不能为空")
    private Long skuId;

    @Schema(description = "数量，缺省 1")
    private Integer quantity;

    @Schema(description = "当前会话ID（可选，仅作操作单归属记录）")
    private Long sessionId;
}

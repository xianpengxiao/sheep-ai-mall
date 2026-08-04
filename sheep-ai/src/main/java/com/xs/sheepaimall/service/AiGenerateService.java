package com.xs.sheepaimall.service;

import com.xs.sheepaimall.dto.ProductCopyRequestDTO;
import com.xs.sheepaimall.dto.ProductCopyResultDTO;
import com.xs.sheepaimall.dto.ProductCopySaveDTO;
import com.xs.sheepaimall.vo.AiGenerateRecordVO;

/** AI 文案生成 Service */
public interface AiGenerateService {

    /**
     * 根据商品名称和核心卖点生成电商营销文案（仅生成，不保存）
     *
     * @param dto 商品信息（productName + coreSellingPoints）
     * @return 生成的文案（title + detail + sellPoints）
     */
    ProductCopyResultDTO generateProductCopy(ProductCopyRequestDTO dto);

    /**
     * 用户确认后，将生成的文案保存到数据库
     *
     * @param dto 完整的商品信息 + AI 生成结果
     * @return 保存后的记录 VO
     */
    AiGenerateRecordVO saveProductCopy(ProductCopySaveDTO dto);
}

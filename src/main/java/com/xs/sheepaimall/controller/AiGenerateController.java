package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.ProductCopyRequestDTO;
import com.xs.sheepaimall.dto.ProductCopyResultDTO;
import com.xs.sheepaimall.dto.ProductCopySaveDTO;
import com.xs.sheepaimall.service.AiGenerateService;
import com.xs.sheepaimall.vo.AiGenerateRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 文案生成接口 */
@Tag(name = "AI文案生成", description = "基于 DeepSeek 大模型的电商商品文案智能生成")
@Validated
@RestController
@RequestMapping("/api/ai")
public class AiGenerateController {

    @Resource
    private AiGenerateService aiGenerateService;

    @Operation(summary = "生成商品营销文案（预览）", description = "输入商品名称和核心卖点，返回标题、详情描述、卖点列表。不落库，仅供预览。")
    @PostMapping("/product-copy")
    public R<ProductCopyResultDTO> generateProductCopy(@Valid @RequestBody ProductCopyRequestDTO dto) {
        return R.ok(aiGenerateService.generateProductCopy(dto));
    }

    @Operation(summary = "确认保存商品文案", description = "用户预览后确认保存，将生成的文案落库到 ai_generate_record 表")
    @PostMapping("/product-copy/save")
    public R<AiGenerateRecordVO> saveProductCopy(@Valid @RequestBody ProductCopySaveDTO dto) {
        return R.ok(aiGenerateService.saveProductCopy(dto));
    }
}

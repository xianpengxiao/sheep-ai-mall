package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.dto.ProductCopyRequestDTO;
import com.xs.sheepaimall.dto.ProductCopyResultDTO;
import com.xs.sheepaimall.dto.ProductCopySaveDTO;
import com.xs.sheepaimall.entity.AiGenerateRecord;
import com.xs.sheepaimall.mapper.AiGenerateRecordMapper;
import com.xs.sheepaimall.service.AiGenerateService;
import com.xs.sheepaimall.vo.AiGenerateRecordVO;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

/** AI 文案生成 Service 实现 — 基于 Spring AI + DeepSeek */
@Service
public class AiGenerateServiceImpl implements AiGenerateService {

    /** 生成类型常量：综合文案 */
    private static final int TYPE_PRODUCT_COPY = 5;

    /** Spring AI 自动注入的 ChatModel（OpenAiChatModel 代理到 DeepSeek） */
    @Autowired
    private ChatModel chatModel;

    @Autowired
    private AiGenerateRecordMapper aiGenerateRecordMapper;

    @Override
    public ProductCopyResultDTO generateProductCopy(ProductCopyRequestDTO dto) {
        String prompt = buildPrompt(dto.getProductName(), dto.getCoreSellingPoints());
        String content = chatModel.call(new Prompt(prompt))
                .getResult()
                .getOutput()
                .getText();
        return parseResult(content);
    }

    @Override
    public AiGenerateRecordVO saveProductCopy(ProductCopySaveDTO dto) {
        String prompt = buildPrompt(dto.getProductName(), dto.getCoreSellingPoints());

        // 将生成结果封装为 JSON
        ProductCopyResultDTO resultDTO = new ProductCopyResultDTO();
        resultDTO.setTitle(dto.getTitle());
        resultDTO.setDetail(dto.getDetail());
        resultDTO.setSellPoints(dto.getSellPoints());

        AiGenerateRecord record = new AiGenerateRecord();
        record.setSpuId(dto.getSpuId() != null ? dto.getSpuId() : 0L);
        record.setType(TYPE_PRODUCT_COPY);
        record.setPrompt(prompt);
        record.setResult(JSONUtil.toJsonStr(resultDTO));
        record.setModel("deepseek-chat");
        record.setStatus(1); // 1=已完成

        aiGenerateRecordMapper.insert(record);
        return toVO(record);
    }

    /** 构建电商营销文案提示词 */
    private String buildPrompt(String productName, String coreSellingPoints) {
        return """
                你是一位资深的电商营销文案专家。请根据以下商品信息，生成专业的电商营销文案。

                商品名称：%s
                核心卖点：%s

                要求：
                1. title：15字以内，吸引眼球、突出核心卖点
                2. detail：200-300字，有场景感、激发购买欲
                3. sellPoints：5条，每条精炼有力（10字以内）

                请严格按照以下JSON格式输出，不要包含任何JSON代码块标记（```）或额外说明文字：
                {"title":"商品标题","detail":"详细描述文案","sellPoints":["卖点1","卖点2","卖点3","卖点4","卖点5"]}
                """.formatted(productName, coreSellingPoints);
    }

    /** 从 AI 返回中提取 JSON 并解析为 DTO */
    private ProductCopyResultDTO parseResult(String content) {
        if (StrUtil.isBlank(content)) {
            throw new BizException("AI 返回内容为空，请重试");
        }
        String json = cleanJson(content);
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            ProductCopyResultDTO result = new ProductCopyResultDTO();
            result.setTitle(obj.getStr("title"));
            result.setDetail(obj.getStr("detail"));
            result.setSellPoints(obj.getBeanList("sellPoints", String.class));
            return result;
        } catch (Exception e) {
            throw new BizException("AI 生成结果解析失败: " + e.getMessage());
        }
    }

    /** 清洗 AI 返回内容：去除 ```json 标记、首尾空白 */
    private String cleanJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int headEnd = s.indexOf('\n');
            if (headEnd > 0) {
                s = s.substring(headEnd + 1);
            }
            int tailStart = s.lastIndexOf("```");
            if (tailStart >= 0) {
                s = s.substring(0, tailStart);
            }
            s = s.trim();
        }
        return s;
    }

    /** Entity → VO */
    private AiGenerateRecordVO toVO(AiGenerateRecord record) {
        AiGenerateRecordVO vo = new AiGenerateRecordVO();
        BeanUtil.copyProperties(record, vo);
        // 类型文本映射
        Map<Integer, String> typeMap = Map.of(
                1, "商品标题",
                2, "商品描述",
                3, "广告文案",
                4, "营销话术",
                5, "综合文案"
        );
        vo.setTypeText(typeMap.getOrDefault(record.getType(), "未知"));
        // 状态文本映射
        Map<Integer, String> statusMap = Map.of(
                0, "处理中",
                1, "已完成",
                2, "失败"
        );
        vo.setStatusText(statusMap.getOrDefault(record.getStatus(), "未知"));
        return vo;
    }
}

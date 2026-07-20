package com.xs.sheepaimall.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 敏感词检测工具（简易版）
 * <p>
 * 检查商品标题/描述/图片名中是否包含违规关键词，
 * 高风险类目自动标记需人工复审。
 * </p>
 */
@Slf4j
@Component
public class SensitiveWordUtil {

    /** 敏感词库（可动态扩展） */
    private static final Set<String> SENSITIVE_WORDS = new CopyOnWriteArraySet<>(Arrays.asList(
            "违禁", "假货", "仿品", "高仿", "走私", "枪支", "弹药", "毒品",
            "赌博", "色情", "暴力", "反动", "诈骗", "传销", "刷单", "代刷",
            "外挂", "破解", "盗版", "翻墙", "VPN", "发票", "代购", "原单"
    ));

    /** 高风险类目关键词（命中需人工审核） */
    private static final Set<String> HIGH_RISK_CATEGORIES = new CopyOnWriteArraySet<>(Arrays.asList(
            "食品", "美妆", "彩妆", "护肤品", "化妆品", "3C", "数码", "电器",
            "保健品", "药品", "医疗器械", "母婴", "玩具", "酒类", "茶叶"
    ));

    /** 侵权关键词 */
    private static final Set<String> INFRINGEMENT_KEYWORDS = new CopyOnWriteArraySet<>(Arrays.asList(
            "LV", "GUCCI", "CHANEL", "HERMES", "PRADA", "Cartier", "ROLEX",
            "NIKE", "ADIDAS", "Apple", "iPhone", "Samsung", "华为", "小米"
    ));

    /**
     * 检测文本是否包含敏感词
     *
     * @return 命中的敏感词列表，空表示无敏感词
     */
    public List<String> checkSensitive(String text) {
        if (text == null || text.isBlank()) return List.of();
        String upper = text.toUpperCase();
        return SENSITIVE_WORDS.stream()
                .filter(word -> upper.contains(word.toUpperCase()))
                .toList();
    }

    /**
     * 检测是否包含侵权关键词
     */
    public List<String> checkInfringement(String text) {
        if (text == null || text.isBlank()) return List.of();
        String upper = text.toUpperCase();
        return INFRINGEMENT_KEYWORDS.stream()
                .filter(word -> upper.contains(word.toUpperCase()))
                .toList();
    }

    /**
     * 判断类目名称是否为高风险类目
     */
    public boolean isHighRiskCategory(String categoryName) {
        if (categoryName == null) return false;
        String upper = categoryName.toUpperCase();
        return HIGH_RISK_CATEGORIES.stream().anyMatch(rc -> upper.contains(rc.toUpperCase()));
    }

    /**
     * 综合检测：返回是否需要人工审核
     */
    public boolean needsManualReview(String title, String description, String brand, String categoryName) {
        if (!checkSensitive(title).isEmpty()) return true;
        if (!checkSensitive(description).isEmpty()) return true;
        if (!checkInfringement(brand).isEmpty()) return true;
        return isHighRiskCategory(categoryName);
    }
}

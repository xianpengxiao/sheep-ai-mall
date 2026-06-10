package com.xs.sheepaimall.util;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.config.IdCardVerifyProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 身份证实名认证工具
 * <p>
 * 对接阿里云 API 市场标准接口（APPCODE 认证）。
 * API 地址为空时跳过真实校验，仅做格式校验（开发/演示模式）。
 * </p>
 */
@Slf4j
@Component
public class IdCardVerifyUtil {

    @Resource
    private IdCardVerifyProperties properties;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 校验姓名+身份证是否一致
     *
     * @param realName 真实姓名
     * @param idCard   身份证号
     * @return true=一致  false=不一致或未配置API
     * @throws BizException API 调用失败时抛出
     */
    public boolean verify(String realName, String idCard) {
        // API 地址为空 → 跳过真实校验（开发模式）
        if (properties.getApiUrl() == null || properties.getApiUrl().isBlank()) {
            log.warn("身份证校验API未配置，跳过真实校验：name={}, idCard={}", realName, idCard);
            return true;
        }

        try {
            String url = properties.getApiUrl()
                    .replace("{idCard}", idCard)
                    .replace("{realName}", realName);

            String response = restTemplate.getForObject(url, String.class);
            return parseResult(response);
        } catch (Exception e) {
            log.error("身份证校验API调用失败: {}", e.getMessage());
            throw new BizException("实名认证服务异常，请稍后重试");
        }
    }

    /**
     * 解析API返回结果
     */
    private boolean parseResult(String response) {
        if (response == null || response.isBlank()) {
            throw new BizException("实名认证服务返回异常");
        }
        JSONObject json = JSONUtil.parseObj(response);

        int code = json.getInt("code", -1);
        // 常见成功码: 0, 200, 10000, true
        if (code == 0 || code == 200) {
            JSONObject data = json.getJSONObject("data");
            if (data != null) {
                String result = data.getStr("result", "");
                return "一致".equals(result) || "1".equals(result)
                        || Boolean.TRUE.equals(data.getBool("isConsistent", false));
            }
            // 部分接口直接在顶层返回 result
            String result = json.getStr("result", "");
            return "一致".equals(result) || "1".equals(result);
        }

        // 接口调用成功但不一致
        if (code == 1 || code == 201) {
            return false;
        }

        String msg = json.getStr("msg", "未知错误");
        // 常见业务状态码
        if (code == 101) {
            throw new BizException("身份证号格式不正确");
        }
        if (code == 102) {
            throw new BizException("姓名格式不正确");
        }
        if (code == 103) {
            throw new BizException("无此身份证记录");
        }
        if (code == 403 || code == 401) {
            throw new BizException("实名认证服务授权失效，请联系管理员");
        }
        if (code == 429) {
            throw new BizException("实名认证服务调用过于频繁，请稍后重试");
        }

        throw new BizException("实名认证失败：" + msg);
    }
}

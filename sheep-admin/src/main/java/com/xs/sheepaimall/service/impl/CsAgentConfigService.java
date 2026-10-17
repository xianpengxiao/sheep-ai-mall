package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.entity.CsAgentConfig;
import com.xs.sheepaimall.mapper.CsAgentConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 客服全局配置读取 —— 单行表 cs_agent_config，内存缓存 60s 刷新，避免每消息查表。
 * <p>表为空或读表失败时回退代码默认值（CsDataInit 启动时已保证有一行，此处兜底）。</p>
 */
@Slf4j
@Service
public class CsAgentConfigService {

    private static final long CACHE_MILLIS = 60_000L;

    @Autowired
    private CsAgentConfigMapper csAgentConfigMapper;

    /** 缓存的配置快照与加载时间 */
    private volatile CsAgentConfig cached;
    private volatile long cachedAt;

    /** 获取全局配置（60s 内的缓存直接返回） */
    public CsAgentConfig getConfig() {
        CsAgentConfig config = cached;
        long now = System.currentTimeMillis();
        if (config != null && now - cachedAt < CACHE_MILLIS) {
            return config;
        }
        synchronized (this) {
            if (cached != null && now - cachedAt < CACHE_MILLIS) {
                return cached;
            }
            try {
                CsAgentConfig row = csAgentConfigMapper.selectOne(
                        new LambdaQueryWrapper<CsAgentConfig>().last("LIMIT 1"));
                cached = (row != null) ? row : defaultConfig();
            } catch (Exception e) {
                log.warn("客服全局配置读表失败，使用默认配置: {}", e.getMessage());
                cached = defaultConfig();
            }
            cachedAt = now;
            return cached;
        }
    }

    /** 代码默认值（与 CsDataInit 的默认行一致） */
    private CsAgentConfig defaultConfig() {
        CsAgentConfig config = new CsAgentConfig();
        config.setUserDailyRequestLimit(100);
        config.setUserDailyTokenLimit(20000);
        config.setMaxContextTokens(4000);
        config.setMaxOutputTokens(1024);
        config.setQuotaEnabled(1);
        config.setAiEnabled(0);
        config.setPlatformAiEnabled(0);
        return config;
    }

    /**
     * 更新 AI 自动接待总开关（admin）：关闭时所有会话按人工处理（商家/平台各自开关被覆盖但状态保留）。
     * 单行表 upsert 并刷新缓存，立即生效。
     */
    public CsAgentConfig updateAiEnabled(Integer enabled) {
        return updateConfigColumn(enabled, CsAgentConfig::setAiEnabled);
    }

    /** 更新平台会话 AI 接待开关（admin）：控制官方客服新会话默认 AI/人工，单会话仍可由坐席手动转接 */
    public CsAgentConfig updatePlatformAiEnabled(Integer enabled) {
        return updateConfigColumn(enabled, CsAgentConfig::setPlatformAiEnabled);
    }

    /** 单行 upsert 某一开关列并刷新缓存（null 视为 0） */
    private CsAgentConfig updateConfigColumn(Integer value,
                                             java.util.function.BiConsumer<CsAgentConfig, Integer> setter) {
        int v = value != null && value == 1 ? 1 : 0;
        CsAgentConfig row = csAgentConfigMapper.selectOne(new LambdaQueryWrapper<CsAgentConfig>().last("LIMIT 1"));
        if (row == null) {
            row = defaultConfig();
            setter.accept(row, v);
            csAgentConfigMapper.insert(row);
        } else {
            setter.accept(row, v);
            csAgentConfigMapper.updateById(row);
        }
        cached = row;
        cachedAt = System.currentTimeMillis();
        log.info("客服全局配置开关已更新 value={}", v);
        return row;
    }
}

package com.xs.sheepaimall.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.entity.CsAgentConfig;
import com.xs.sheepaimall.entity.CsPlan;
import com.xs.sheepaimall.mapper.CsAgentConfigMapper;
import com.xs.sheepaimall.mapper.CsPlanMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 客服数据初始化：cs_plan 无数据则插入默认套餐，cs_agent_config 无数据则插入默认全局配置。
 * <p>幂等：仅当表为空时插入，参考 {@link DataInitRunner} 的写法。</p>
 */
@Slf4j
@Component
public class CsDataInit implements ApplicationRunner {

    @Autowired
    private CsPlanMapper csPlanMapper;

    @Autowired
    private CsAgentConfigMapper csAgentConfigMapper;

    @Override
    public void run(ApplicationArguments args) {
        initDefaultPlan();
        initAgentConfig();
    }

    /** 默认套餐：标准版（额度体系实施时的内置档位，运营后续可调整） */
    private void initDefaultPlan() {
        Long count = csPlanMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        CsPlan plan = new CsPlan();
        plan.setName("标准版");
        plan.setMonthlyTokenQuota(1000000L);
        plan.setDailyRequestLimit(1000);
        plan.setDailyTokenLimit(100000);
        plan.setPrice(new BigDecimal("299.00"));
        plan.setStatus(1);
        csPlanMapper.insert(plan);
        log.info("客服默认套餐已创建: 标准版, planId={}", plan.getId());
    }

    /** 默认全局配置：用户日限 100 请求/20000 Token，上下文预算 4000，输出上限 1024，额度开关启用 */
    private void initAgentConfig() {
        Long count = csAgentConfigMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        CsAgentConfig config = new CsAgentConfig();
        config.setUserDailyRequestLimit(100);
        config.setUserDailyTokenLimit(20000);
        config.setMaxContextTokens(4000);
        config.setMaxOutputTokens(1024);
        config.setQuotaEnabled(1);
        csAgentConfigMapper.insert(config);
        log.info("客服全局配置已创建, configId={}", config.getId());
    }
}

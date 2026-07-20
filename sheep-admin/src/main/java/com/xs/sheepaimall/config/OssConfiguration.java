package com.xs.sheepaimall.config;

import com.xs.sheepaimall.util.OssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class OssConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OssUtil ossUtil(AliOssProperties aliOssProperties) {
        log.info("初始化阿里云 OSS 上传工具类");
        return new OssUtil(aliOssProperties);
    }
}

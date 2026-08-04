package com.xs.sheepaimall.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 通用 Web MVC 配置：注册 UserContextInterceptor，从 Gateway Header 还原用户上下文。
 * <p>各业务服务可继承此类扩展白名单。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserContextInterceptor())
                .addPathPatterns("/api/**");
    }
}

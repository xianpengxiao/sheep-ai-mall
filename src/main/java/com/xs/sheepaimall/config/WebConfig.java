package com.xs.sheepaimall.config;

import com.xs.sheepaimall.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JWT 认证拦截器 + 白名单
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")              // 拦截所有 API 请求
                .excludePathPatterns(                    // 白名单：无需认证
                        "/api/auth/login",               // 登录接口
                        "/api/auth/register",            // 注册接口（预留）
                        "/swagger-ui/**",                // Knife4j 文档
                        "/v3/api-docs/**",               // OpenAPI 文档
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/error"                         // Spring Boot 错误页
                );
    }
}

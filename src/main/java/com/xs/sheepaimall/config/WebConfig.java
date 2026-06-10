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
                        "/api/auth/register",            // 注册接口
                        "/api/auth/send-code",           // 发送短信验证码
                        "/api/auth/verify-code",         // 校验短信验证码
                        "/api/auth/check-phone",         // 检查手机号是否已注册
                        "/api/auth/sms-login",           // 短信验证码登录
                        "/api/auth/send-login-code",     // 发送登录短信验证码
                        "/api/auth/send-email",          // 发送邮箱验证码
                        "/api/payment/notify",           // 微信支付回调（由微信服务器调用，无 JWT）
                        "/api/category/tree",            // 分类树（游客可浏览）
                        "/api/category/children/**",     // 子分类（游客可浏览）
                        "/api/spu/page",                 // 商品分页（游客可浏览）
                        "/api/spu/hot",                  // 热门商品（游客可浏览）
                        "/api/sku/spu/**",               // SPU下的SKU列表（游客可浏览）
                        "/api/search/product",           // 商品搜索（游客可浏览）
                        "/swagger-ui/**",                // Knife4j 文档
                        "/v3/api-docs/**",               // OpenAPI 文档
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/error"                         // Spring Boot 错误页
                );
    }
}

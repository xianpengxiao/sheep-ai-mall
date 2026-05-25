package com.xs.sheepaimall.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / SpringDoc OpenAPI 配置
 * <p>文档页: <a href="http://localhost:8080/doc.html">/doc.html</a></p>
 */
@Configuration
public class Knife4jConfig {

    /** API 基本信息 */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SheepAIMall 智能电商商品服务")
                        .description("商品分类、SPU、SKU 管理接口")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SheepAIMall")
                                .email("admin@sheepaimall.com")));
    }
}

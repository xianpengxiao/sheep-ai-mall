package com.xs.sheepaimall.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;

/**
 * 向量知识库配置：独立 PostgreSQL 数据源 + 百炼 Embedding 模型 + 平台向量库。
 * <p>注意：不使用 spring-ai-pgvector-store starter 的自动装配，避免其抓取
 * MyBatis 的 @Primary 数据源；平台库与商家库（每商家一表）共用此处的
 * PG 数据源与 EmbeddingModel，商家库由 VectorStoreService 动态创建。</p>
 */
@Configuration
public class VectorStoreConfig {

    /** 向量维度，需与 Embedding 模型输出一致；qwen3.7-text-embedding-flash 支持 256/512/768/1024 */
    public static final int EMBEDDING_DIMENSIONS = 1024;

    /** 平台公共知识库表名；商家专属库表名为 VECTOR_TABLE_PREFIX + merchantId */
    public static final String PLATFORM_TABLE = "vector_store";
    public static final String MERCHANT_TABLE_PREFIX = "vector_store_m_";

    // ======================== PG 数据源（非主数据源） ========================

    @Bean
    public DataSource pgVectorDataSource(
            @Value("${sheep.pgvector.host}") String host,
            @Value("${sheep.pgvector.port}") int port,
            @Value("${sheep.pgvector.database}") String database,
            @Value("${sheep.pgvector.username}") String username,
            @Value("${sheep.pgvector.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(4);
        config.setPoolName("pgvector-pool");
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // ======================== Embedding（DashScope 百炼） ========================

    /**
     * 百炼 qwen3.7-text-embedding-flash。application.yml 中已通过
     * spring.ai.openai.embedding.enabled=false 关闭指向 DeepSeek 的
     * 自动装配 EmbeddingModel，此处为主实现。
     * <p>本工作区（sk-ws- 开头的工作区 key）的百炼网关不兼容非 ASCII 原文请求体：
     * 中文内容以 UTF-8 原文发送会被以 "Required body invalid" 拒绝（实测验证，ASCII 内容正常），
     * 导致所有中文文档嵌入静默失败、向量库为空。此处通过 RestClient 拦截器把请求体重写为
     * ASCII 安全形式（非 ASCII 字符转 "\\uXXXX" 转义，JSON 标准转义、网关解析后语义不变）。</p>
     */
    @Bean
    @Primary
    public EmbeddingModel dashScopeEmbeddingModel(
            @Value("${sheep.ai.dashscope.api-key}") String apiKey,
            @Value("${sheep.ai.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode}") String baseUrl,
            @Value("${sheep.ai.dashscope.embedding-model:qwen3.7-text-embedding-flash}") String model) {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    byte[] escaped = toAsciiSafeJson(body);
                    if (escaped != body) {
                        // 替换后的 ASCII 转义体更长，必须同步 Content-Length，否则底层 HttpClient 拒绝发送
                        request.getHeaders().setContentLength(escaped.length);
                    }
                    return execution.execute(request, escaped);
                });
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .dimensions(EMBEDDING_DIMENSIONS)
                .build();
        return new OpenAiEmbeddingModel(api, org.springframework.ai.document.MetadataMode.EMBED, options);
    }

    /**
     * 把 JSON 请求体重写为纯 ASCII：非 ASCII 字符转 "\\uXXXX" 标准 JSON 转义。
     * JSON 结构中只有字符串值内可能出现非 ASCII 字符，因此对全文字符转义是安全的；
     * 网关 JSON 解析后还原为原字符，embedding 语义不变。
     */
    public static byte[] toAsciiSafeJson(byte[] body) {
        if (body == null || body.length == 0) {
            return body;
        }
        String text = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        boolean hasNonAscii = false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                hasNonAscii = true;
                break;
            }
        }
        if (!hasNonAscii) {
            return body;
        }
        StringBuilder sb = new StringBuilder(text.length() + 64);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 0x7F) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    // ======================== 平台公共向量库 ========================

    @Bean
    public VectorStore platformVectorStore(
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(PLATFORM_TABLE)
                .dimensions(EMBEDDING_DIMENSIONS)
                // 建表走 SQL 迁移脚本（sheep-pgvector.sql），不在启动时触碰 PG，
                // 保证 PG 不可用时应用仍可启动
                .initializeSchema(false)
                .build();
    }
}

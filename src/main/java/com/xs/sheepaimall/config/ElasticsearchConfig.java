package com.xs.sheepaimall.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Elasticsearch 客户端配置 — 仅在配置 spring.elasticsearch.uris 后生效 */
@Configuration
@ConditionalOnProperty(name = "spring.elasticsearch.uris")
public class ElasticsearchConfig {

    /** ES 日期格式：与 SpuDocument 中 @Field(pattern) 保持一致 */
    private static final DateTimeFormatter ES_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${spring.elasticsearch.uris}")
    private String uris;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    /** 创建 ElasticsearchClient Bean，注册自定义 LocalDateTime 序列化/反序列化 */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(ES_DATETIME_FORMATTER));
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(ES_DATETIME_FORMATTER));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(javaTimeModule);

        var restClientBuilder = RestClient.builder(HttpHost.create(uris));

        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            CredentialsProvider cp = new BasicCredentialsProvider();
            cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            restClientBuilder.setHttpClientConfigCallback(
                    hcb -> hcb.setDefaultCredentialsProvider(cp));
        }

        RestClient restClient = restClientBuilder.build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(mapper));
        return new ElasticsearchClient(transport);
    }
}

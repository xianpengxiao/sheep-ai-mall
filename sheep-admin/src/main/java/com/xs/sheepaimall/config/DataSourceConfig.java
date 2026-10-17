package com.xs.sheepaimall.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 业务主数据源（MySQL）显式声明为 @Primary。
 * <p>背景：引入 pgVectorDataSource 后，Spring Boot 的 DataSourceAutoConfiguration
 * 因存在用户自定义 DataSource Bean 而整体退避，导致 MyBatis-Plus 抓到 PG 数据源。
 * 此处通过 DataSourceProperties 装配 MySQL 数据源（url 会正确映射到 Hikari 的
 * jdbcUrl），并标 @Primary，保证 MyBatis 等所有按类型注入点使用 MySQL，
 * PG 数据源仅被 pgVectorJdbcTemplate 使用。</p>
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}

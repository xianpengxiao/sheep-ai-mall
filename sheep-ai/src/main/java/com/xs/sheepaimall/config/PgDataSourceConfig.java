package com.xs.sheepaimall.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/** PostgreSQL 数据源（pgvector 向量检索专用） */
@Configuration
public class PgDataSourceConfig {

    @Value("${sheep.pg.datasource.host:localhost}")
    private String host;

    @Value("${sheep.pg.datasource.port:5432}")
    private int port;

    @Value("${sheep.pg.datasource.database:sheep_ai_pg}")
    private String database;

    @Value("${sheep.pg.datasource.username:postgres}")
    private String username;

    @Value("${sheep.pg.datasource.password:123456}")
    private String password;

    @Bean
    public DataSource pgDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean
    public JdbcTemplate pgJdbcTemplate(DataSource pgDataSource) {
        return new JdbcTemplate(pgDataSource);
    }
}

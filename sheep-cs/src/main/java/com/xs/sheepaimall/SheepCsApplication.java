package com.xs.sheepaimall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.xs.sheepaimall")
@MapperScan("com.xs.sheepaimall.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.xs.sheepaimall")
public class SheepCsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SheepCsApplication.class, args);
    }
}

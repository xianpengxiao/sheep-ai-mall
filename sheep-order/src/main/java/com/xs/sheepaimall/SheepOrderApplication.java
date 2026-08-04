package com.xs.sheepaimall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.xs.sheepaimall")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.xs.sheepaimall")
public class SheepOrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(SheepOrderApplication.class, args);
    }
}

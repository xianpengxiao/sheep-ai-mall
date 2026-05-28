package com.xs.sheepaimall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SheepAiMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(SheepAiMallApplication.class, args);
    }

}

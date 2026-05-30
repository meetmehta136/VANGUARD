package com.vanguard.limiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LimiterApplication {
    public static void main(String[] args) {
        SpringApplication.run(LimiterApplication.class, args);
    }
}

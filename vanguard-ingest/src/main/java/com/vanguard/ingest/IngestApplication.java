package com.vanguard.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class IngestApplication {
    public static void main(String[] args) {
        SpringApplication.run(IngestApplication.class, args);
    }
}

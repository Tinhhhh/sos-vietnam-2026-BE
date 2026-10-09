package com.sosvietnam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SosVietnamBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SosVietnamBackendApplication.class, args);
    }
}

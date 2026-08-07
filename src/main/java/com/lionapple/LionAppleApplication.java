package com.lionapple;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LionAppleApplication {

    public static void main(String[] args) {
        SpringApplication.run(LionAppleApplication.class, args);
    }
}

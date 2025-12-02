package com.jingwei.rsswithai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SuppressWarnings("SpellCheckingInspection")
@SpringBootApplication
@EnableScheduling
public class RSSwithAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RSSwithAiApplication.class, args);
    }

}

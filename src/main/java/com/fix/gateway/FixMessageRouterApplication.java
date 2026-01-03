package com.fix.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class FixMessageRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(FixMessageRouterApplication.class, args);
    }
}
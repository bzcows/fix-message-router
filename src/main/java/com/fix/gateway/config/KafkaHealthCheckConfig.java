package com.fix.gateway.config;

import com.fix.gateway.service.KafkaHealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class KafkaHealthCheckConfig {

    @Bean
    public ApplicationRunner kafkaHealthCheckRunner(KafkaHealthService kafkaHealthService) {
        return args -> {
            log.info("Starting Kafka health check on application startup...");
            kafkaHealthService.healthCheckWithShutdown();
            log.info("Kafka health check passed. Application continuing startup.");
        };
    }
}
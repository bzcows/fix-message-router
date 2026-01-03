package com.fix.gateway.config;

import com.fix.gateway.model.RoutingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class RoutingConfigurationLoader {

    @Value("classpath:routing-config.json")
    private Resource routingConfigResource;

    @Bean
    public RoutingConfig routingConfig(ObjectMapper objectMapper) throws IOException {
        log.info("Loading routing configuration from: {}", routingConfigResource.getURI());
        
        try (InputStream inputStream = routingConfigResource.getInputStream()) {
            RoutingConfig config = objectMapper.readValue(inputStream, RoutingConfig.class);
            log.info("Loaded {} route mappings", config.getRoutes().size());
            
            // Log route details including topic information
            config.getRoutes().forEach(route -> {
                String inputTopic = route.getInputTopic();
                String outputTopic = route.getOutputTopic();
                
                // Check for legacy configuration (using default topic names)
                boolean usingLegacyInput = route.getInputTopic() == null || route.getInputTopic().trim().isEmpty();
                boolean usingLegacyOutput = route.getOutputTopic() == null || route.getOutputTopic().trim().isEmpty();
                
                if (usingLegacyInput || usingLegacyOutput) {
                    log.warn("Route {} is using auto-generated topic names. Consider explicitly setting inputTopic and outputTopic in configuration.",
                        route.getRouteId());
                }
                
                log.info("Route {}: {} -> {} | Type: {} | Input: {} | Output: {} | Destinations: {}",
                    route.getRouteId(),
                    route.getSenderCompId(),
                    route.getTargetCompId(),
                    route.getType(),
                    inputTopic,
                    outputTopic,
                    route.getDestinations()
                );
            });
            
            return config;
        } catch (IOException e) {
            log.error("Failed to load routing configuration from {}", routingConfigResource.getFilename(), e);
            throw e;
        }
    }
}
package com.fix.gateway.config;

import com.fix.gateway.model.EnhancedRoutingConfig;
import com.fix.gateway.model.RoutingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Enhanced routing configuration loader that supports both legacy and enhanced configurations.
 * Can load enhanced configuration from a separate file or convert legacy configuration.
 */
@Configuration
@Slf4j
public class EnhancedRoutingConfigurationLoader {

    @Value("classpath:enhanced-routing-config.json")
    private Resource enhancedRoutingConfigResource;
    
    @Value("classpath:routing-config.json")
    private Resource legacyRoutingConfigResource;

    /**
     * Primary bean for enhanced routing configuration.
     * Tries to load enhanced configuration first, falls back to legacy.
     */
    @Bean
    @Primary
    public EnhancedRoutingConfig enhancedRoutingConfig(ObjectMapper objectMapper) throws IOException {
        EnhancedRoutingConfig enhancedConfig = null;
        
        // Try to load enhanced configuration
        if (enhancedRoutingConfigResource.exists()) {
            try {
                enhancedConfig = loadEnhancedConfiguration(objectMapper);
                log.info("Successfully loaded enhanced routing configuration");
            } catch (IOException e) {
                log.warn("Failed to load enhanced routing configuration, falling back to legacy", e);
            }
        }
        
        // If enhanced config not available, convert from legacy
        if (enhancedConfig == null) {
            enhancedConfig = convertFromLegacyConfiguration(objectMapper);
            log.info("Using converted legacy routing configuration");
        }
        
        logEnhancedConfiguration(enhancedConfig);
        return enhancedConfig;
    }
    
    /**
     * Loads enhanced routing configuration from JSON file.
     */
    private EnhancedRoutingConfig loadEnhancedConfiguration(ObjectMapper objectMapper) throws IOException {
        log.info("Loading enhanced routing configuration from: {}", enhancedRoutingConfigResource.getURI());
        
        try (InputStream inputStream = enhancedRoutingConfigResource.getInputStream()) {
            EnhancedRoutingConfig config = objectMapper.readValue(inputStream, EnhancedRoutingConfig.class);
            log.info("Loaded {} enhanced route mappings", config.getRoutes().size());
            return config;
        }
    }
    
    /**
     * Converts legacy routing configuration to enhanced format.
     */
    private EnhancedRoutingConfig convertFromLegacyConfiguration(ObjectMapper objectMapper) throws IOException {
        log.info("Loading legacy routing configuration from: {}", legacyRoutingConfigResource.getURI());
        
        try (InputStream inputStream = legacyRoutingConfigResource.getInputStream()) {
            RoutingConfig legacyConfig = objectMapper.readValue(inputStream, RoutingConfig.class);
            log.info("Loaded {} legacy route mappings for conversion", legacyConfig.getRoutes().size());
            
            // Convert to enhanced configuration
            EnhancedRoutingConfig enhancedConfig = EnhancedRoutingConfig.fromLegacyConfig(legacyConfig);
            
            // Set enhanced routing enabled by default for converted routes
            enhancedConfig.getRoutes().forEach(route -> {
                route.setEnhancedRouting(true);
                log.info("Converted route {} to enhanced format with {} destination(s)", 
                    route.getRouteId(), route.getDestinationConfigs().size());
            });
            
            return enhancedConfig;
        }
    }
    
    /**
     * Logs details of the enhanced configuration.
     */
    private void logEnhancedConfiguration(EnhancedRoutingConfig config) {
        log.info("=== Enhanced Routing Configuration Summary ===");
        log.info("Total routes: {}", config.getRoutes().size());
        log.info("Enhanced routing enabled globally: {}", config.isEnableEnhancedRouting());
        
        config.getRoutes().forEach(route -> {
            log.info("Route: {}", route.getRouteId());
            log.info("  Type: {}", route.getType());
            log.info("  Enhanced routing: {}", route.isEnhancedRouting());
            log.info("  Input topic: {}", route.getInputTopic());
            log.info("  Output topic: {}", route.getOutputTopic());
            log.info("  Destinations: {}", route.getDestinationConfigs().size());
            
            route.getDestinationConfigs().forEach(dest -> {
                log.info("    - URI: {}", dest.getUri());
                log.info("      Max retries: {}", dest.getMaxRetries());
                log.info("      Timeout: {}ms", dest.getTimeout());
                log.info("      Dead letter: {}", dest.getDeadLetterTopic(route.getRouteId()));
                if (dest.getMsgTypes() != null && !dest.getMsgTypes().isEmpty()) {
                    log.info("      Msg types: {}", dest.getMsgTypes());
                } else {
                    log.info("      Msg types: [ALL] (no filtering)");
                }
            });
            
            if (route.getErrorHandling() != null) {
                log.info("  Error handling - Max redeliveries: {}, Delay: {}ms",
                    route.getErrorHandling().getMaxRedeliveries(),
                    route.getErrorHandling().getRedeliveryDelay());
            }
        });
        
        log.info("=============================================");
    }
    
    /**
     * Bean for backward compatibility - provides legacy RoutingConfig if needed.
     */
    @Bean
    public RoutingConfig legacyRoutingConfig(ObjectMapper objectMapper) throws IOException {
        log.info("Loading legacy routing configuration for backward compatibility");
        
        try (InputStream inputStream = legacyRoutingConfigResource.getInputStream()) {
            RoutingConfig config = objectMapper.readValue(inputStream, RoutingConfig.class);
            log.info("Loaded {} legacy route mappings", config.getRoutes().size());
            return config;
        }
    }
}
package com.fix.gateway.config;

import com.fix.gateway.model.RoutingConfig;
import com.fix.gateway.model.RouteMapping;
import com.fix.gateway.util.MvelExpressionEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Routing configuration loader that loads enhanced routing configuration.
 * This is the single configuration loader for the application.
 */
@Configuration
@Slf4j
public class RoutingConfigurationLoader {

    @Value("classpath:routing-config.json")
    private Resource routingConfigResource;

    /**
     * Primary bean for routing configuration.
     */
    @Bean
    public RoutingConfig routingConfig(ObjectMapper objectMapper) throws IOException {
        log.info("Loading routing configuration from: {}", routingConfigResource.getURI());
        
        try (InputStream inputStream = routingConfigResource.getInputStream()) {
            RoutingConfig config = objectMapper.readValue(inputStream, RoutingConfig.class);
            log.info("Loaded {} route mappings", config.getRoutes().size());
            
            // Pre-compile MVEL expressions for all routes
            preCompileMvelExpressions(config);
            
            logConfiguration(config);
            return config;
        } catch (IOException e) {
            log.error("Failed to load routing configuration from {}", routingConfigResource.getFilename(), e);
            throw e;
        }
    }
    
    /**
     * Pre-compiles MVEL expressions for all routes to warm up the cache.
     * This ensures expressions are compiled once at startup rather than on first message.
     */
    private void preCompileMvelExpressions(RoutingConfig config) {
        int compiledCount = 0;
        for (RouteMapping route : config.getRoutes()) {
            if (route.getPartitionExpression() != null && !route.getPartitionExpression().trim().isEmpty()) {
                try {
                    // This will compile and cache the expression
                    MvelExpressionEvaluator.preCompileExpression(route.getPartitionExpression());
                    compiledCount++;
                    log.debug("Pre-compiled partition expression for route {}: {}",
                             route.getRouteId(), route.getPartitionExpression());
                } catch (Exception e) {
                    log.warn("Failed to pre-compile partition expression for route {}: {}",
                            route.getRouteId(), e.getMessage());
                }
            }
        }
        if (compiledCount > 0) {
            log.info("Pre-compiled {} MVEL partition expressions", compiledCount);
        }
    }
    
    /**
     * Logs details of the routing configuration.
     */
    private void logConfiguration(RoutingConfig config) {
        log.info("=== Routing Configuration Summary ===");
        log.info("Total routes: {}", config.getRoutes().size());
        log.info("Enhanced routing enabled globally: {}", config.isEnableEnhancedRouting());
        
        config.getRoutes().forEach(route -> {
            log.info("Route: {}", route.getRouteId());
            log.info("  Type: {}", route.getType());
            log.info("  Enhanced routing: {}", route.isEnhancedRouting());
            log.info("  Partition strategy: {}", route.getPartitionStrategy());
            if (route.getPartitionExpression() != null && !route.getPartitionExpression().isEmpty()) {
                log.info("  Partition expression: {}", route.getPartitionExpression());
            }
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
        
        log.info("=====================================");
    }
}
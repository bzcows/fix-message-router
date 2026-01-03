package com.fix.gateway.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced route mapping with destination-specific configurations
 * and individual exception handling capabilities.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnhancedRouteMapping extends RoutingConfig.RouteMapping {
    
    /**
     * Enhanced destination configurations with individual policies
     */
    private List<DestinationConfig> destinationConfigs = new ArrayList<>();
    
    /**
     * Default error handling configuration for this route
     */
    private ErrorHandlingConfig errorHandling = new ErrorHandlingConfig();
    
    /**
     * Whether to use the enhanced routing (individual routes per destination)
     * Defaults to true for new configurations
     */
    private boolean enhancedRouting = true;
    
    /**
     * Gets the destination configurations, falling back to simple URI strings
     * if destinationConfigs is empty but destinations is populated.
     * This provides backward compatibility.
     */
    public List<DestinationConfig> getDestinationConfigs() {
        if (!destinationConfigs.isEmpty()) {
            return destinationConfigs;
        }
        
        // Convert simple URI strings to DestinationConfig objects
        List<String> simpleDestinations = getDestinations();
        if (simpleDestinations != null && !simpleDestinations.isEmpty()) {
            List<DestinationConfig> converted = new ArrayList<>();
            for (String uri : simpleDestinations) {
                DestinationConfig config = new DestinationConfig();
                config.setUri(uri);
                // Apply default timeout for Netty endpoints
                if (uri.contains("netty:")) {
                    config.setTimeout(10000);
                    config.getEndpointParameters().put("connectTimeout", "5000");
                    config.getEndpointParameters().put("requestTimeout", "5000");
                }
                converted.add(config);
            }
            return converted;
        }
        
        return destinationConfigs;
    }
    
    /**
     * Gets all destination URIs from the configuration
     * @return List of destination URIs
     */
    public List<String> getAllDestinationUris() {
        List<String> uris = new ArrayList<>();
        for (DestinationConfig config : getDestinationConfigs()) {
            uris.add(config.buildCompleteUri());
        }
        return uris;
    }
    
    /**
     * Configuration for error handling at the route level
     */
    @Data
    public static class ErrorHandlingConfig {
        /**
         * Maximum redeliveries for the main route (before splitting to destinations)
         */
        private int maxRedeliveries = 1;
        
        /**
         * Redelivery delay in milliseconds
         */
        private long redeliveryDelay = 500;
        
        /**
         * Whether to use dead letter channel for main route errors
         */
        private boolean useDeadLetterChannel = true;
        
        /**
         * Custom dead letter channel URI
         */
        private String deadLetterChannelUri = "direct:deadLetterChannel";
    }
}
package com.fix.gateway.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Route mapping with destination-specific configurations
 * and individual exception handling capabilities.
 */
@Data
public class RouteMapping {
    
    private String routeId;
    private String senderCompId;
    private String targetCompId;
    private RouteType type = RouteType.OUTPUT; // Default to OUTPUT for backward compatibility
    private List<String> destinations = new ArrayList<>();
    private String inputTopic;  // Topic for INPUT route messages (auto-generated if null)
    private String outputTopic; // Topic for OUTPUT route messages (auto-generated if null)
    
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
     * Partition strategy for content-based routing (only applicable for OUTPUT routes)
     * Defaults to NONE (no content-based routing)
     */
    private PartitionStrategy partitionStrategy = PartitionStrategy.NONE;
    
    /**
     * MVEL expression for partition determination
     * For KEY strategy: expression should evaluate to the key value
     * For EXPR strategy: expression should evaluate to partition number (integer)
     */
    private String partitionExpression;
    
    /**
     * Get the input topic name, auto-generating if not set.
     * Format: fix.{senderCompId}.{targetCompId}.input
     */
    public String getInputTopic() {
        if (inputTopic == null || inputTopic.trim().isEmpty()) {
            return String.format("fix.%s.%s.input",
                senderCompId != null ? senderCompId : "UNKNOWN",
                targetCompId != null ? targetCompId : "UNKNOWN");
        }
        return inputTopic;
    }
    
    /**
     * Get the output topic name, auto-generating if not set.
     * Format: fix.{senderCompId}.{targetCompId}.output
     */
    public String getOutputTopic() {
        if (outputTopic == null || outputTopic.trim().isEmpty()) {
            return String.format("fix.%s.%s.output",
                senderCompId != null ? senderCompId : "UNKNOWN",
                targetCompId != null ? targetCompId : "UNKNOWN");
        }
        return outputTopic;
    }
    
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
        if (destinations != null && !destinations.isEmpty()) {
            List<DestinationConfig> converted = new ArrayList<>();
            for (String uri : destinations) {
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
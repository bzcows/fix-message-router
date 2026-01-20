package com.fix.gateway.model;

import lombok.Data;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Configuration for a single destination with individual exception handling
 * and routing policies.
 */
@Data
public class DestinationConfig {
    
    /**
     * Destination URI (e.g., "netty:tcp://localhost:9999", "kafka:topic-name")
     */
    private String uri;
    
    /**
     * Maximum number of retry attempts for this destination
     */
    private int maxRetries = 3;
    
    /**
     * Delay between retries in milliseconds
     */
    private long retryDelay = 1000;
    
    /**
     * Connection/request timeout in milliseconds
     */
    private long timeout = 5000;
    
    /**
     * Dead letter topic for failed messages specific to this destination
     * If null, uses the default dead letter channel
     */
    private String deadLetterTopic;
    
    /**
     * Additional endpoint parameters specific to this destination
     */
    private Map<String, String> endpointParameters = new HashMap<>();
    
    /**
     * Whether to use parallel processing for this destination
     */
    private boolean parallelProcessing = true;
    
    /**
     * Whether to stop processing other destinations if this one fails
     */
    private boolean stopOnException = false;
    
    /**
     * List of FIX message types (msgType field values) that should be routed to this destination.
     * If null or empty, all messages are routed (backward compatibility).
     * Special value "*" means all message types.
     */
    private List<String> msgTypes = new ArrayList<>();
    
    /**
     * Builds the complete URI with all parameters
     * @return Complete URI string with query parameters
     */
    public String buildCompleteUri() {
        if (endpointParameters == null || endpointParameters.isEmpty()) {
            return uri;
        }
        
        StringBuilder sb = new StringBuilder(uri);
        if (!uri.contains("?")) {
            sb.append("?");
        } else {
            sb.append("&");
        }
        
        boolean first = true;
        for (Map.Entry<String, String> param : endpointParameters.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(param.getKey()).append("=").append(param.getValue());
            first = false;
        }
        
        return sb.toString();
    }
    
    /**
     * Gets the dead letter topic, falling back to a default if not specified
     * @param routeId The parent route ID for generating default topic name
     * @return Dead letter topic name
     */
    public String getDeadLetterTopic(String routeId) {
        if (deadLetterTopic != null && !deadLetterTopic.trim().isEmpty()) {
            return deadLetterTopic;
        }
        
        // Generate default dead letter topic
        String destName = extractDestinationName();
        return String.format("dead-letter-%s-%s", 
            routeId.toLowerCase().replaceAll("[^a-z0-9]", "-"),
            destName.toLowerCase().replaceAll("[^a-z0-9]", "-"));
    }
    
    /**
     * Extracts a simple name from the URI for logging and topic naming
     * @return Simplified destination name
     */
    private String extractDestinationName() {
        if (uri == null) {
            return "unknown";
        }
        
        // Extract protocol and host/port
        String[] parts = uri.split(":");
        if (parts.length > 1) {
            String endpoint = parts[1];
            // Remove query parameters
            if (endpoint.contains("?")) {
                endpoint = endpoint.substring(0, endpoint.indexOf('?'));
            }
            // Remove slashes and special characters
            return endpoint.replaceAll("[^a-zA-Z0-9]", "-");
        }
        
        return uri.replaceAll("[^a-zA-Z0-9]", "-");
    }
    
    /**
     * Checks if this destination should receive a message with the given msgType.
     * @param msgType The FIX message type (e.g., "D", "8", "AE")
     * @return true if the destination should receive the message, false otherwise
     */
    public boolean matchesMsgType(String msgType) {
        if (msgTypes == null || msgTypes.isEmpty()) {
            // Backward compatibility: no filtering configured
            return true;
        }
        
        // Check for wildcard "*" meaning all message types
        if (msgTypes.contains("*")) {
            return true;
        }
        
        // Case-sensitive exact match (FIX msgTypes are typically uppercase)
        return msgTypes.contains(msgType);
    }
}
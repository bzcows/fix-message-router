package com.fix.gateway.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Routing configuration supporting individual destination routes
 * with separate exception handling.
 */
@Data
public class RoutingConfig {
    
    /**
     * List of route mappings
     */
    private List<RouteMapping> routes = new ArrayList<>();
    
    /**
     * Global error handling configuration
     */
    private GlobalErrorHandlingConfig globalErrorHandling = new GlobalErrorHandlingConfig();
    
    /**
     * Whether to enable enhanced routing globally
     * Can be overridden per route
     */
    private boolean enableEnhancedRouting = true;
    
    /**
     * Default destination configuration for routes that don't specify
     */
    private DestinationConfig defaultDestinationConfig = new DestinationConfig();
    
    /**
     * Gets all INPUT routes
     * @return List of INPUT route mappings
     */
    public List<RouteMapping> getInputRoutes() {
        List<RouteMapping> inputRoutes = new ArrayList<>();
        for (RouteMapping route : routes) {
            if (route.getType() == RouteType.INPUT) {
                inputRoutes.add(route);
            }
        }
        return inputRoutes;
    }
    
    /**
     * Gets all OUTPUT routes
     * @return List of OUTPUT route mappings
     */
    public List<RouteMapping> getOutputRoutes() {
        List<RouteMapping> outputRoutes = new ArrayList<>();
        for (RouteMapping route : routes) {
            if (route.getType() == RouteType.OUTPUT) {
                outputRoutes.add(route);
            }
        }
        return outputRoutes;
    }
    
    /**
     * Finds a route by its ID
     * @param routeId The route ID to find
     * @return The route mapping, or null if not found
     */
    public RouteMapping findRouteById(String routeId) {
        for (RouteMapping route : routes) {
            if (route.getRouteId().equals(routeId)) {
                return route;
            }
        }
        return null;
    }
    
    /**
     * Global error handling configuration
     */
    @Data
    public static class GlobalErrorHandlingConfig {
        /**
         * Default maximum redeliveries for all routes
         */
        private int defaultMaxRedeliveries = 1;
        
        /**
         * Default redelivery delay in milliseconds
         */
        private long defaultRedeliveryDelay = 500;
        
        /**
         * Whether to log all errors
         */
        private boolean logAllErrors = true;
        
        /**
         * Default dead letter topic for routes without specific configuration
         */
        private String defaultDeadLetterTopic = "fix-dead-letter";
        
        /**
         * Whether to use transaction for error handling
         */
        private boolean useTransactions = false;
    }
}
package com.fix.gateway.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced routing configuration supporting individual destination routes
 * with separate exception handling.
 */
@Data
public class EnhancedRoutingConfig {
    
    /**
     * List of enhanced route mappings
     */
    private List<EnhancedRouteMapping> routes = new ArrayList<>();
    
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
    public List<EnhancedRouteMapping> getInputRoutes() {
        List<EnhancedRouteMapping> inputRoutes = new ArrayList<>();
        for (EnhancedRouteMapping route : routes) {
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
    public List<EnhancedRouteMapping> getOutputRoutes() {
        List<EnhancedRouteMapping> outputRoutes = new ArrayList<>();
        for (EnhancedRouteMapping route : routes) {
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
    public EnhancedRouteMapping findRouteById(String routeId) {
        for (EnhancedRouteMapping route : routes) {
            if (route.getRouteId().equals(routeId)) {
                return route;
            }
        }
        return null;
    }
    
    /**
     * Converts from legacy RoutingConfig to EnhancedRoutingConfig
     * @param legacyConfig The legacy configuration
     * @return Enhanced configuration
     */
    public static EnhancedRoutingConfig fromLegacyConfig(RoutingConfig legacyConfig) {
        EnhancedRoutingConfig enhancedConfig = new EnhancedRoutingConfig();
        
        if (legacyConfig.getRoutes() != null) {
            for (RoutingConfig.RouteMapping legacyRoute : legacyConfig.getRoutes()) {
                EnhancedRouteMapping enhancedRoute = new EnhancedRouteMapping();
                
                // Copy basic properties
                enhancedRoute.setRouteId(legacyRoute.getRouteId());
                enhancedRoute.setSenderCompId(legacyRoute.getSenderCompId());
                enhancedRoute.setTargetCompId(legacyRoute.getTargetCompId());
                enhancedRoute.setType(legacyRoute.getType());
                enhancedRoute.setInputTopic(legacyRoute.getInputTopic());
                enhancedRoute.setOutputTopic(legacyRoute.getOutputTopic());
                
                // Convert simple destinations to DestinationConfig objects
                if (legacyRoute.getDestinations() != null) {
                    for (String uri : legacyRoute.getDestinations()) {
                        DestinationConfig destConfig = new DestinationConfig();
                        destConfig.setUri(uri);
                        enhancedRoute.getDestinationConfigs().add(destConfig);
                    }
                }
                
                enhancedConfig.getRoutes().add(enhancedRoute);
            }
        }
        
        return enhancedConfig;
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
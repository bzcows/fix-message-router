package com.fix.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for routing mode selection.
 * Allows switching between legacy toD-based routing and enhanced individual route routing.
 */
@Component
@ConfigurationProperties(prefix = "fix.routing")
@Data
public class RoutingModeConfig {
    
    /**
     * Routing mode to use.
     * Options: "legacy" (toD-based) or "enhanced" (individual routes with separate exception handling)
     * Default: "enhanced"
     */
    private String mode = "enhanced";
    
    /**
     * Whether enhanced routing is enabled.
     * @return true if mode is "enhanced"
     */
    public boolean isEnhancedRoutingEnabled() {
        return "enhanced".equalsIgnoreCase(mode);
    }
    
    /**
     * Whether legacy routing is enabled.
     * @return true if mode is "legacy"
     */
    public boolean isLegacyRoutingEnabled() {
        return "legacy".equalsIgnoreCase(mode);
    }
}
package com.fix.gateway.model;

import lombok.Data;
import java.util.List;

@Data
public class RoutingConfig {
    private List<RouteMapping> routes;
    
    @Data
    public static class RouteMapping {
        private String routeId;
        private String senderCompId;
        private String targetCompId;
        private RouteType type = RouteType.OUTPUT; // Default to OUTPUT for backward compatibility
        private List<String> destinations;
        private String inputTopic;  // Topic for INPUT route messages (auto-generated if null)
        private String outputTopic; // Topic for OUTPUT route messages (auto-generated if null)
        
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
    }
}
package com.fix.gateway.model;

import lombok.Data;
import java.util.List;

@Data
public class FixSessionConfig {
    private List<SessionConfig> sessions;
    
    @Data
    public static class SessionConfig {
        private String sessionId;
        private String senderCompId;
        private String targetCompId;
        private List<DestinationConfig> destinations;
    }
    
    @Data
    public static class DestinationConfig {
        private DestinationType type;
        private String uri;
        
        public enum DestinationType {
            NETTY,
            JMS,
            KAFKA
        }
    }
}
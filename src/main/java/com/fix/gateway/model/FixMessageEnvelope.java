package com.fix.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixMessageEnvelope {
   @JsonProperty("sessionId")
    private String sessionId;
    
    @JsonProperty("senderCompId")
    private String senderCompId;
    
    @JsonProperty("targetCompId")
    private String targetCompId;
    
    @JsonProperty("msgType")
    private String msgType;
    
    @JsonProperty("createdTimestamp")
    @Builder.Default
    private Instant createdTimestamp=Instant.now();;
    
    @JsonProperty("rawMessage")
    private String rawMessage;

    @JsonProperty("errorMessage")
    private String errorMessage;
    
    @JsonProperty("errorType")
    private String errorType;
    
    @JsonProperty("errorTimestamp")
    private Instant errorTimestamp;
    
    @JsonProperty("errorRouteId")
    private String errorRouteId;

/*
    public static FixMessageEnvelope create(String rawMessage, String sessionId, String senderCompId, String targetCompId) {
        return FixMessageEnvelope.builder()
                .rawMessage(rawMessage)
                .sessionId(sessionId)
                .senderCompId(senderCompId)
                .targetCompId(targetCompId)
                .build();
    }
    
    public static FixMessageEnvelope fromSessionConfig(String rawMessage, FixSessionConfig.SessionConfig sessionConfig) {
        return FixMessageEnvelope.builder()
                .rawMessage(rawMessage)
                .sessionId(sessionConfig.getSessionId())
                .senderCompId(sessionConfig.getSenderCompId())
                .targetCompId(sessionConfig.getTargetCompId())
                .build();
    }
*/
}
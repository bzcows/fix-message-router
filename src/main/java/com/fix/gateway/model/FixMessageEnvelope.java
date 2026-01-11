package com.fix.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixMessageEnvelope {
   @JsonProperty("sessionId")
    private String sessionId;
    
    @JsonProperty("senderCompId")
    private String senderCompId;
    
    @JsonProperty("targetCompId")
    private String targetCompId;
    
    @JsonProperty("msgType")
    private String msgType;

    @JsonProperty("clOrdID")
    private String clOrdID;
    
    
    @Getter(onMethod = @__({@JsonIgnore}))
    @Setter(onMethod = @__({@JsonIgnore}))
    private String symbol;
    
    @Getter(onMethod = @__({@JsonIgnore}))
    @Setter(onMethod = @__({@JsonIgnore}))
    private String side;
    
    @Getter(onMethod = @__({@JsonIgnore}))
    @Setter(onMethod = @__({@JsonIgnore}))
    private String orderQty;
    
    @Getter(onMethod = @__({@JsonIgnore}))
    @Setter(onMethod = @__({@JsonIgnore}))
    private String price;
    
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
    
    /**
     * Map of parsed FIX tags for efficient access during expression evaluation.
     * This is populated during envelope creation to avoid repeated parsing.
     * Should not be serialized to Kafka.
     */
    @Getter(onMethod = @__({@JsonIgnore}))
    @Setter(onMethod = @__({@JsonIgnore}))
    @Builder.Default
    private Map<Integer, String> parsedTags = new HashMap<>();

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
package com.fix.gateway.util;

import com.fix.gateway.model.FixMessageEnvelope;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringMessageEnvelopeParser {
    
    private static final Pattern ENVELOPE_PATTERN = Pattern.compile(
        "MessageEnvelope\\(sessionId=([^,]+),\\s*senderCompId=([^,]+),\\s*targetCompId=([^,]+),\\s*msgType=([^,]+),\\s*createdTimestamp=([^,]+),\\s*rawMessage=([^,]+),\\s*errorMessage=(.+)\\)"
    );
    
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    
    public static FixMessageEnvelope parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }
        
        // Try to match the pattern
        Matcher matcher = ENVELOPE_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Input does not match expected MessageEnvelope string format");
        }
        
        try {
            String sessionId = matcher.group(1).trim();
            String senderCompId = matcher.group(2).trim();
            String targetCompId = matcher.group(3).trim();
            String msgType = matcher.group(4).trim();
            String createdTimestampStr = matcher.group(5).trim();
            String rawMessage = matcher.group(6);
            // Don't trim the raw message - preserve trailing SOH character
            // Log raw message length and last character for debugging
            if (rawMessage != null && !rawMessage.isEmpty()) {
                int length = rawMessage.length();
                char lastChar = rawMessage.charAt(length - 1);
                System.out.println("[DEBUG] StringMessageEnvelopeParser - Raw message length: " + length +
                                 ", last char code: " + (int)lastChar +
                                 " (0x" + Integer.toHexString(lastChar) + ")");
            }
            
            // Parse timestamp
            Instant timestamp;
            try {
                timestamp = Instant.parse(createdTimestampStr);
            } catch (DateTimeParseException e) {
                timestamp = Instant.now();
            }
            
            // Create FixMessageEnvelope using builder
            return FixMessageEnvelope.builder()
                    .sessionId(sessionId)
                    .senderCompId(senderCompId)
                    .targetCompId(targetCompId)
                    .msgType(msgType)
                    .createdTimestamp(timestamp)
                    .rawMessage(rawMessage)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse MessageEnvelope string: " + e.getMessage(), e);
        }
    }
    
    public static boolean isStringFormat(String input) {
        if (input == null) return false;
        return input.trim().startsWith("MessageEnvelope(");
    }
}
package com.fix.gateway.util;

import com.fix.gateway.model.FixMessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringMessageEnvelopeParser {
    
    private static final Logger log = LoggerFactory.getLogger(StringMessageEnvelopeParser.class);
    
    private static final Pattern ENVELOPE_PATTERN = Pattern.compile(
        "MessageEnvelope\\(messageId=([^,]+),\\s*sessionId=([^,]+),\\s*senderCompId=([^,]+),\\s*targetCompId=([^,]+),\\s*msgType=([^,]+),\\s*clOrdID=([^,]+),\\s*msgSeqNum=([^,]+),\\s*createdTimestamp=([^,]+),\\s*rawMessage=([^,]+),\\s*messageFingerprint=([^,]+)(?:,.+)?\\)"
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
            String messageId = matcher.group(1).trim();
            String sessionId = matcher.group(2).trim();
            String senderCompId = matcher.group(3).trim();
            String targetCompId = matcher.group(4).trim();
            String msgType = matcher.group(5).trim();
            String clOrdID = matcher.group(6).trim();
            String msgSeqNum = matcher.group(7).trim();
            String createdTimestampStr = matcher.group(8).trim();
            String rawMessage = matcher.group(9);
            String messageFingerprint = matcher.group(10).trim();
            
            // Don't trim the raw message - preserve trailing SOH character
            // Log raw message length and last character for debugging
            if (rawMessage != null && !rawMessage.isEmpty()) {
                int length = rawMessage.length();
                char lastChar = rawMessage.charAt(length - 1);
                log.debug("StringMessageEnvelopeParser - Raw message length: {}, last char code: {} (0x{})",
                         length, (int)lastChar, Integer.toHexString(lastChar));
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
                    .clOrdID(clOrdID)
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
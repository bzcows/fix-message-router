package com.fix.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.gateway.model.FixMessageEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testObjectMapperCanDeserializeInstant() throws Exception {
        // Create a test JSON with Instant timestamp using new structure
        String json = """
            {
                "sessionId": "TEST_SESSION",
                "senderCompId": "SENDER",
                "targetCompId": "TARGET",
                "msgType": "D",
                "seqNum": 1,
                "possDupFlag": false,
                "possResend": false,
                "additionalFields": {},
                "rawMessage": "8=FIX.4.4\\u00019=100\\u000135=D\\u000134=1\\u000149=SENDER\\u000156=TARGET\\u000110=000\\u0001",
                "createdTimestamp": "2025-12-22T23:00:00.000Z"
            }
            """;

        // Deserialize the JSON
        FixMessageEnvelope envelope = objectMapper.readValue(json, FixMessageEnvelope.class);

        // Verify the Instant was deserialized correctly
        assertNotNull(envelope);
        assertNotNull(envelope.getCreatedTimestamp());
        assertEquals("TEST_SESSION", envelope.getSessionId());
        assertEquals("SENDER", envelope.getSenderCompId());
        assertEquals("TARGET", envelope.getTargetCompId());
        
        // Verify the timestamp is approximately correct (within a reasonable range)
        Instant expected = Instant.parse("2025-12-22T23:00:00.000Z");
        assertEquals(expected, envelope.getCreatedTimestamp());
    }

    @Test
    void testObjectMapperCanSerializeInstant() throws Exception {
        // Create a FixMessageEnvelope with Instant using builder
        FixMessageEnvelope envelope = FixMessageEnvelope.builder()
                .sessionId("TEST_SESSION")
                .senderCompId("SENDER")
                .targetCompId("TARGET")
                .rawMessage("test message")
                .createdTimestamp(Instant.parse("2025-12-22T23:00:00.000Z"))
                .build();

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(envelope);

        // Verify JSON contains the timestamp in ISO format (could be with or without milliseconds)
        // The format should be something like "2025-12-22T23:00:00Z" or "2025-12-22T23:00:00.000Z"
        assertTrue(json.contains("2025-12-22T23:00:00"),
            "JSON should contain ISO timestamp: " + json);
        
        // Deserialize back to verify round-trip
        FixMessageEnvelope deserialized = objectMapper.readValue(json, FixMessageEnvelope.class);
        assertEquals(envelope.getCreatedTimestamp(), deserialized.getCreatedTimestamp());
    }
}
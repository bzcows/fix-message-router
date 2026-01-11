package com.fix.gateway.util;

import com.fix.gateway.model.FixMessageEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MvelExpressionEvaluatorTest {

    @Test
    void testEvaluateExpressionWithEnvelopeFields() {
        Map<Integer, String> parsedTags = new HashMap<>();
        parsedTags.put(55, "AAPL");
        parsedTags.put(35, "D");
        parsedTags.put(11, "ORDER123");
        parsedTags.put(54, "1");
        parsedTags.put(38, "100");
        parsedTags.put(44, "150.25");
        
        FixMessageEnvelope envelope = FixMessageEnvelope.builder()
                .sessionId("FIX.4.4:GTWY->EXEC")
                .senderCompId("GTWY")
                .targetCompId("EXEC")
                .msgType("D")
                .clOrdID("ORDER123")
                .symbol("AAPL")
                .side("1")
                .orderQty("100")
                .price("150.25")
                .rawMessage("8=FIX.4.4\u00019=100\u000135=D\u000149=GTWY\u000156=EXEC\u000155=AAPL\u000111=ORDER123\u000110=000\u0001")
                .parsedTags(parsedTags)
                .createdTimestamp(Instant.now())
                .build();
        
        // Test accessing envelope fields
        Object result = MvelExpressionEvaluator.evaluateExpression("msgType", envelope, null);
        assertEquals("D", result);
        
        // Test accessing envelope symbol field
        result = MvelExpressionEvaluator.evaluateExpression("symbol", envelope, null);
        assertEquals("AAPL", result);
        
        // Test accessing parsed tags via envelope
        result = MvelExpressionEvaluator.evaluateExpression("Symbol", envelope, null);
        assertEquals("AAPL", result);
        
        // Test complex expression
        result = MvelExpressionEvaluator.evaluateExpression("msgType == 'D' ? 'EQUITY' : 'OTHER'", envelope, null);
        assertEquals("EQUITY", result);
        
        // Test accessing side field
        result = MvelExpressionEvaluator.evaluateExpression("side == '1' ? 'BUY' : 'SELL'", envelope, null);
        assertEquals("BUY", result);
    }
    
    @Test
    void testParseFixTags() {
        String rawMessage = "8=FIX.4.4\u00019=100\u000135=D\u000149=GTWY\u000156=EXEC\u000155=AAPL\u000111=ORDER123\u000110=000\u0001";
        
        Map<Integer, String> tags = MvelExpressionEvaluator.parseFixTags(rawMessage);
        
        assertEquals("FIX.4.4", tags.get(8));
        assertEquals("100", tags.get(9));
        assertEquals("D", tags.get(35));
        assertEquals("GTWY", tags.get(49));
        assertEquals("EXEC", tags.get(56));
        assertEquals("AAPL", tags.get(55));
        assertEquals("ORDER123", tags.get(11));
        assertEquals("000", tags.get(10));
    }
    
    @Test
    void testEvaluatePartitionExpressionKeyStrategy() {
        FixMessageEnvelope envelope = FixMessageEnvelope.builder()
                .sessionId("FIX.4.4:GTWY->EXEC")
                .senderCompId("GTWY")
                .targetCompId("EXEC")
                .msgType("D")
                .rawMessage("8=FIX.4.4\u00019=100\u000135=D\u000149=GTWY\u000156=EXEC\u000155=AAPL\u000111=ORDER123\u000110=000\u0001")
                .build();
        
        Map<Integer, String> parsedTags = MvelExpressionEvaluator.parseFixTags(envelope.getRawMessage());
        
        // Test KEY strategy expression
        Object result = MvelExpressionEvaluator.evaluatePartitionExpression("Symbol", envelope, parsedTags);
        assertEquals("AAPL", result);
        
        // Test expression with conditional
        result = MvelExpressionEvaluator.evaluatePartitionExpression("msgType == 'D' ? 'EQUITY_' + Symbol : 'OTHER'", envelope, parsedTags);
        assertEquals("EQUITY_AAPL", result);
    }
    
    @Test
    void testEvaluatePartitionExpressionExprStrategy() {
        FixMessageEnvelope envelope = FixMessageEnvelope.builder()
                .sessionId("FIX.4.4:GTWY->EXEC")
                .senderCompId("GTWY")
                .targetCompId("EXEC")
                .msgType("D")
                .rawMessage("8=FIX.4.4\u00019=100\u000135=D\u000149=GTWY\u000156=EXEC\u000155=AAPL\u000111=ORDER123\u000110=000\u0001")
                .build();
        
        Map<Integer, String> parsedTags = MvelExpressionEvaluator.parseFixTags(envelope.getRawMessage());
        
        // Test EXPR strategy expression (returns partition number)
        Object result = MvelExpressionEvaluator.evaluatePartitionExpression("if (MsgType == 'D') { return 1; } else { return 0; }", envelope, parsedTags);
        assertTrue(result instanceof Integer || result instanceof Number);
        int partitionNum = ((Number) result).intValue();
        assertEquals(1, partitionNum);
    }
}
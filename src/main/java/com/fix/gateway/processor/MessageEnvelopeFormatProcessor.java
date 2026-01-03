package com.fix.gateway.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.gateway.model.FixMessageEnvelope;
import com.fix.gateway.util.StringMessageEnvelopeParser;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MessageEnvelopeFormatProcessor implements Processor {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        
        if (body == null) {
            throw new IllegalArgumentException("Message body is null");
        }
        
        String bodyStr = body.toString();
        
        // Check if it's already a FixMessageEnvelope object
        if (body instanceof FixMessageEnvelope) {
            // Already parsed, nothing to do
            return;
        }
        
        // Try to parse as JSON first
        try {
            FixMessageEnvelope envelope = objectMapper.readValue(bodyStr, FixMessageEnvelope.class);
            exchange.getIn().setBody(envelope);
            exchange.getIn().setHeader("messageFormat", "json");
            return;
        } catch (Exception e) {
            // JSON parsing failed, try string format
        }
        
        // Try to parse as string format
        if (StringMessageEnvelopeParser.isStringFormat(bodyStr)) {
            try {
                FixMessageEnvelope envelope = StringMessageEnvelopeParser.parse(bodyStr);
                exchange.getIn().setBody(envelope);
                exchange.getIn().setHeader("messageFormat", "string");
                return;
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse MessageEnvelope in string format: " + e.getMessage(), e);
            }
        }
        
        // If we get here, the format is unrecognized
        throw new IllegalArgumentException("Unrecognized message format. Expected JSON or MessageEnvelope string format.");
    }
}
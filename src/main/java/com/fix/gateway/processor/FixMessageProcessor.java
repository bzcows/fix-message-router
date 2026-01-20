package com.fix.gateway.processor;

import com.fix.gateway.model.FixMessageEnvelope;
import com.fix.gateway.model.RouteMapping;
import com.fix.gateway.model.RouteType;
import com.fix.gateway.model.RoutingConfig;
import com.fix.gateway.util.FixMessageUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class FixMessageProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(FixMessageProcessor.class);

    @Autowired
    private RoutingConfig routingConfig;

    @Override
    public void process(Exchange exchange) throws Exception {
        FixMessageEnvelope envelope = exchange.getIn().getBody(FixMessageEnvelope.class);
        
        if (envelope == null) {
            throw new IllegalArgumentException("Invalid FIX message envelope");
        }
        
        String senderCompId = envelope.getSenderCompId();
        String targetCompId = envelope.getTargetCompId();
        String sessionId = envelope.getSessionId();
        String msgType = envelope.getMsgType();
        
        // Get routeId from headers (set by FixMessageRouter)
        String routeId = exchange.getIn().getHeader("routeId", String.class);
        
        List<String> destinations;
        
        // Get routeType from headers (set by FixMessageRouter)
        RouteType routeType = exchange.getIn().getHeader("routeType", RouteType.class);
        
        if (routeId != null && routeType != null) {
            // Route-specific processing: find the specific route by routeId and type
            destinations = routingConfig.getRoutes().stream()
                .filter(route -> route.getRouteId().equals(routeId))
                .filter(route -> route.getType() == routeType)
                .flatMap(route -> route.getDestinationConfigs().stream())
                .map(dest -> dest.buildCompleteUri())
                .collect(Collectors.toList());
        } else {
            // Fallback: find matching routes by sender/target IDs
            // Default to INPUT routes for backward compatibility
            RouteType fallbackType = routeType != null ? routeType : RouteType.INPUT;
            destinations = routingConfig.getRoutes().stream()
                .filter(route -> route.getType() == fallbackType)
                .filter(route -> matchesRoute(route, senderCompId, targetCompId))
                .flatMap(route -> route.getDestinationConfigs().stream())
                .map(dest -> dest.buildCompleteUri())
                .collect(Collectors.toList());
            
            if (!destinations.isEmpty()) {
                // Log warning about using legacy matching
                exchange.getIn().setHeader("legacyRouteMatching", true);
            }
        }
        
        // Log destinations found
        log.debug("Found {} destinations for route {}", destinations.size(), routeId);
        if (!destinations.isEmpty()) {
            log.debug("Destinations: {}", destinations);
        }
        
        // Set headers for routing
        exchange.getIn().setHeader("sessionId", sessionId);
        exchange.getIn().setHeader("senderCompId", senderCompId);
        exchange.getIn().setHeader("targetCompId", targetCompId);
        exchange.getIn().setHeader("msgType", msgType);
        exchange.getIn().setHeader("destinations", destinations);
        exchange.getIn().setHeader("routeType", routeType != null ? routeType : RouteType.INPUT);
        
        // Set the raw FIX message as the body for forwarding
        String rawMessage = envelope.getRawMessage();
        
        // Ensure the FIX message has proper trailing SOH character
        if (rawMessage != null && !rawMessage.isEmpty()) {
            rawMessage = FixMessageUtils.ensureTrailingSOH(rawMessage);
            
            // Validate FIX message structure
            if (!FixMessageUtils.isValidFixMessage(rawMessage)) {
                log.warn("Message may not be valid FIX format");
            }
        }
        
        exchange.getIn().setBody(rawMessage);
        
        // Log routing decision
        if (destinations.isEmpty()) {
            exchange.getIn().setHeader("noRouteMatch", true);
            exchange.getIn().setHeader("routeId", routeId);
            exchange.getIn().setHeader("senderCompId", senderCompId);
            exchange.getIn().setHeader("targetCompId", targetCompId);
        }
    }
    
    private boolean matchesRoute(RouteMapping route, String senderCompId, String targetCompId) {
        boolean senderMatch = route.getSenderCompId().equalsIgnoreCase(senderCompId);
        boolean targetMatch = route.getTargetCompId().equalsIgnoreCase(targetCompId);
        return senderMatch && targetMatch;
    }
}
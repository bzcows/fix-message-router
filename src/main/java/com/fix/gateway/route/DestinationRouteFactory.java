package com.fix.gateway.route;

import com.fix.gateway.model.DestinationConfig;
import com.fix.gateway.model.EnhancedRouteMapping;
import org.apache.camel.builder.Builder;
import org.apache.camel.model.RouteDefinition;
import org.springframework.stereotype.Component;

/**
 * Factory for creating individual destination routes with custom exception handling.
 * Each destination gets its own route with isolated error handling and retry policies.
 */
@Component
public class DestinationRouteFactory {
    
    /**
     * Creates a route definition for a single destination with individual exception handling.
     * 
     * @param parentRoute The parent route configuration
     * @param destinationConfig The destination configuration
     * @param destinationIndex Index of this destination in the parent route
     * @return Configured route definition
     */
    public RouteDefinition createDestinationRoute(
            EnhancedRouteMapping parentRoute,
            DestinationConfig destinationConfig,
            int destinationIndex) {
        
        String parentRouteId = parentRoute.getRouteId();
        String routeId = buildDestinationRouteId(parentRouteId, destinationIndex);
        String destinationUri = destinationConfig.buildCompleteUri();
        
        RouteDefinition route = new RouteDefinition();
        route.routeId(routeId);
        
        // Start from a direct endpoint that will be called by the parent route
        route.from("direct:" + routeId)
            .log(String.format("Destination route %s: Processing message for %s", 
                routeId, destinationUri))
            .setProperty("destinationUri", constant(destinationUri))
            .setProperty("parentRouteId", constant(parentRouteId))
            .setProperty("destinationIndex", constant(destinationIndex));
        
        // Configure destination-specific exception handling
        configureExceptionHandling(route, parentRoute, destinationConfig);
        
        // Add destination-specific processing
        addDestinationProcessing(route, destinationConfig);
        
        // Send to the actual destination
        route.to(destinationUri)
            .log(String.format("Destination route %s: Successfully sent to %s", 
                routeId, destinationUri));
        
        return route;
    }
    
    /**
     * Configures exception handling for a destination route.
     */
    private void configureExceptionHandling(
            RouteDefinition route,
            EnhancedRouteMapping parentRoute,
            DestinationConfig destinationConfig) {
        
        String deadLetterTopic = destinationConfig.getDeadLetterTopic(parentRoute.getRouteId());
        
        route.onException(Exception.class)
            .handled(true)
            .maximumRedeliveries(destinationConfig.getMaxRetries())
            .redeliveryDelay(destinationConfig.getRetryDelay())
            .useOriginalMessage()
            .log(String.format("Destination %s failed after ${header.CamelRedeliveryCounter} attempts: ${exception.message}", 
                destinationConfig.getUri()))
            .choice()
                .when(Builder.simple("${header.CamelRedeliveryCounter} >= " + destinationConfig.getMaxRetries()))
                    // Final failure - send to dead letter queue
                    .log(String.format("Destination %s: Maximum retries exceeded, sending to dead letter topic %s", 
                        destinationConfig.getUri(), deadLetterTopic))
                    .toF("kafka:%s?brokers={{kafka.brokers:localhost:9092}}"
                        + "&keySerializer=org.apache.kafka.common.serialization.StringSerializer"
                        + "&valueSerializer=org.apache.kafka.common.serialization.StringSerializer"
                        + "&requestTimeoutMs=10000", deadLetterTopic)
                .endChoice()
            .end();
    }
    
    /**
     * Adds destination-specific processing to the route.
     */
    private void addDestinationProcessing(RouteDefinition route, DestinationConfig destinationConfig) {
        String uri = destinationConfig.getUri();
        
        // Add protocol-specific processing
        if (uri.startsWith("netty:")) {
            addNettyProcessing(route, destinationConfig);
        } else if (uri.startsWith("kafka:")) {
            addKafkaProcessing(route, destinationConfig);
        } else if (uri.startsWith("direct:") || uri.startsWith("seda:")) {
            addInternalProcessing(route, destinationConfig);
        }
        
        // Add timeout if configured
        if (destinationConfig.getTimeout() > 0) {
            route.setHeader("CamelHttpTimeout", constant(destinationConfig.getTimeout()));
        }
    }
    
    /**
     * Adds Netty-specific processing.
     */
    private void addNettyProcessing(RouteDefinition route, DestinationConfig destinationConfig) {
        route.process(exchange -> {
            // Ensure Netty-specific headers are set
            exchange.getIn().setHeader("CamelNettyTextlineDelimiter", "NULL");
            exchange.getIn().setHeader("CamelNettySync", true);
            
            // Log Netty-specific details
            exchange.getIn().setHeader("nettyDestination", destinationConfig.getUri());
        })
        .log("Sending to Netty endpoint: ${header.nettyDestination}");
    }
    
    /**
     * Adds Kafka-specific processing.
     */
    private void addKafkaProcessing(RouteDefinition route, DestinationConfig destinationConfig) {
        route.process(exchange -> {
            // Ensure Kafka headers are preserved
            exchange.getIn().setHeader("kafka.KEY", exchange.getIn().getHeader("kafka.KEY"));
            exchange.getIn().setHeader("kafka.PARTITION", exchange.getIn().getHeader("kafka.PARTITION"));
            exchange.getIn().setHeader("kafka.TOPIC", exchange.getIn().getHeader("kafka.TOPIC"));
        })
        .log("Sending to Kafka topic: " + extractKafkaTopic(destinationConfig.getUri()));
    }
    
    /**
     * Adds processing for internal Camel endpoints.
     */
    private void addInternalProcessing(RouteDefinition route, DestinationConfig destinationConfig) {
        route.log("Routing to internal endpoint: " + destinationConfig.getUri());
    }
    
    /**
     * Builds a unique route ID for a destination.
     */
    private String buildDestinationRouteId(String parentRouteId, int destinationIndex) {
        return parentRouteId + "_DEST_" + destinationIndex;
    }
    
    /**
     * Extracts Kafka topic from URI.
     */
    private String extractKafkaTopic(String uri) {
        if (uri.startsWith("kafka:")) {
            String topicPart = uri.substring(6); // Remove "kafka:"
            if (topicPart.contains("?")) {
                return topicPart.substring(0, topicPart.indexOf('?'));
            }
            return topicPart;
        }
        return "unknown";
    }
    
    /**
     * Helper method to create a constant expression.
     */
    private org.apache.camel.Expression constant(Object value) {
        return Builder.constant(value);
    }
}
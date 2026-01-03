package com.fix.gateway.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.gateway.model.FixMessageEnvelope;
import com.fix.gateway.model.RouteType;
import com.fix.gateway.model.RoutingConfig;
import com.fix.gateway.processor.FixMessageProcessor;
import com.fix.gateway.processor.MessageEnvelopeFormatProcessor;
import com.fix.gateway.util.FixMessageUtils;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fix.routing.mode", havingValue = "legacy")
public class FixMessageRouter extends RouteBuilder {

    @Autowired
    private RoutingConfig routingConfig;
    
    @Autowired
    private FixMessageProcessor fixMessageProcessor;
    
    @Autowired
    private MessageEnvelopeFormatProcessor messageEnvelopeFormatProcessor;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void configure() throws Exception {

        
            
        // Configure JSON data format for FixMessageEnvelope using the Spring-configured ObjectMapper
        JacksonDataFormat envelopeFormat = new JacksonDataFormat(objectMapper, FixMessageEnvelope.class);
        envelopeFormat.setPrettyPrint(false);
        
        // Configure error handling with minimal retries to prevent infinite loops
        errorHandler(deadLetterChannel("direct:deadLetterChannel")
            .maximumRedeliveries(1)
            .redeliveryDelay(500)
            .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));

        // ===== INPUT ROUTES =====
        // For INPUT routes: consume from inputTopic and forward to all destinations
        routingConfig.getRoutes().stream()
            .filter(route -> route.getType() == RouteType.INPUT)
            .forEach(route -> {
                String routeId = route.getRouteId();
                String inputTopic = route.getInputTopic();
                String consumerGroup = "fix-router-input-" + routeId.toLowerCase().replaceAll("[^a-zA-Z0-9]", "-");
                
                from("kafka:" + inputTopic
                    + "?brokers={{kafka.brokers:localhost:9092}}"
                    + "&groupId=" + consumerGroup
                    + "&autoOffsetReset={{kafka.autoOffsetReset:earliest}}"
                    + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                    + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                    + "&sessionTimeoutMs=30000"
                    + "&connectionMaxIdleMs=30000"
                    + "&requestTimeoutMs=30000"
                    + "&maxPollIntervalMs=300000"
                    + "&autoCommitEnable=true"
                    + "&allowManualCommit=false"
                    + "&maxPollRecords=10")
                    .routeId(routeId + "_INPUT_CONSUMER")
                    .onException(Exception.class)
                        .handled(true)
                        .maximumRedeliveries(0)
                        .log("INPUT route " + routeId + ": Failed to send to ${exchangeProperty.destinationUri}: ${exception.message}")
                        .setProperty("destinationFailed", constant(true))
                        .transform().constant("FAILED")
                    .end()
                    .log("INPUT route " + routeId + ": Received FIX message envelope from Kafka topic " + inputTopic + ": ${body}")
                    .process(messageEnvelopeFormatProcessor)
                    .process(exchange -> {
                        // Set route-specific headers before processing
                        exchange.getIn().setHeader("routeId", routeId);
                        exchange.getIn().setHeader("routeType", RouteType.INPUT);
                        exchange.getIn().setHeader("inputTopic", inputTopic);
                    })
                    .process(fixMessageProcessor)
                    .log("INPUT route " + routeId + ": Processed INPUT FIX message for session: ${header.sessionId}")
                    .choice()
                        .when(header("destinations").isNotNull())
                            // Store the original message body before splitting
                            .setProperty("originalBody", body())
                            // Split destinations in parallel to prevent blocking
                            .split(header("destinations"))
                                .parallelProcessing(true)
                                .stopOnException(false)
                                .log("INPUT route " + routeId + ": Forwarding to destination: ${body}")
                                // Store the destination URI before restoring the body
                                .setProperty("destinationUri", body())
                                // Restore the original message body for sending (raw FIX message)
                                .setBody(exchangeProperty("originalBody"))
                                // Add timeout for Netty connections (5 seconds) and disable connection pooling
                                .choice()
                                    .when(simple("${exchangeProperty.destinationUri} contains 'netty:'"))
                                        // Properly construct Netty URI by merging parameters
                                        .process(exchange -> {
                                            String destinationUri = exchange.getProperty("destinationUri", String.class);
                                            
                                            // Parse the URI to extract base and existing parameters
                                            String baseUri;
                                            String existingParams = "";
                                            
                                            int queryIndex = destinationUri.indexOf('?');
                                            if (queryIndex > 0) {
                                                baseUri = destinationUri.substring(0, queryIndex);
                                                existingParams = destinationUri.substring(queryIndex + 1);
                                            } else {
                                                baseUri = destinationUri;
                                            }
                                            
                                            // Parameters to add (Netty-specific timeouts and settings)
                                            // Only add parameters that aren't already specified
                                            StringBuilder paramsBuilder = new StringBuilder();
                                            if (!existingParams.isEmpty()) {
                                                paramsBuilder.append(existingParams);
                                            }
                                            
                                            // Add essential Netty parameters if not already present
                                            String[] paramsToAdd = {
                                                "connectTimeout=2000",
                                                "requestTimeout=2000",
                                                "disconnect=true",
                                                "reuseChannel=false",
                                                "sync=true"
                                            };
                                            
                                            for (String paramToAdd : paramsToAdd) {
                                                String key = paramToAdd.substring(0, paramToAdd.indexOf('='));
                                                // Check if this parameter already exists
                                                if (!existingParams.contains(key + "=")) {
                                                    if (paramsBuilder.length() > 0) {
                                                        paramsBuilder.append('&');
                                                    }
                                                    paramsBuilder.append(paramToAdd);
                                                }
                                            }
                                            
                                            // Construct final URI
                                            String finalUri;
                                            if (paramsBuilder.length() > 0) {
                                                finalUri = baseUri + "?" + paramsBuilder.toString();
                                            } else {
                                                finalUri = baseUri;
                                            }
                                            
                                            exchange.setProperty("nettyFullUri", finalUri);
                                        })
                                        .toD("${exchangeProperty.nettyFullUri}")
                                    .otherwise()
                                        .toD("${exchangeProperty.destinationUri}")
                                .end()
                                // Log status - if we reach here, sending was successful
                                .log("INPUT route " + routeId + ": Successfully sent to ${exchangeProperty.destinationUri} - Status: OK")
                                .transform().constant("OK")
                            .end()
                        .endChoice()
                        .otherwise()
                            .log("INPUT route " + routeId + ": No destinations found for INPUT message")
                    .end();
            });
        
        // ===== OUTPUT ROUTES =====
        // For OUTPUT routes: listen on all destinations and forward to outputTopic
        routingConfig.getRoutes().stream()
            .filter(route -> route.getType() == RouteType.OUTPUT)
            .forEach(route -> {
                String routeId = route.getRouteId();
                String senderCompId = route.getSenderCompId();
                String targetCompId = route.getTargetCompId();
                String outputTopic = route.getOutputTopic();
                
                // For each destination in the OUTPUT route, create a listening endpoint
                route.getDestinations().forEach(destination -> {
                    // Create a unique route ID for this listening endpoint
                    String listenerRouteId = routeId + "_FROM_" + extractEndpointName(destination);
                    
                    from(destination)
                        .routeId(listenerRouteId)
                        .log("OUTPUT route " + routeId + ": Received raw FIX message from " + destination + ": ${body}")
                        .process(exchange -> {
                            // Build envelope from raw message using route configuration
                            String rawMessage = exchange.getIn().getBody(String.class);
                            // Use proper sessionId format: "FIX.4.4:GATEWAY->EXEC"
                            String properSessionId = "FIX.4.4:" + senderCompId + "->" + targetCompId;
                            
                            // Remove standard headers and trailer from raw message
                            //String filteredRawMessage = FixMessageUtils.removeStandardHeadersAndTrailer(rawMessage);
                            
                            FixMessageEnvelope envelope = FixMessageEnvelope.builder()
                                    .sessionId(properSessionId)
                                    .senderCompId(senderCompId)
                                    .targetCompId(targetCompId)
                                    .rawMessage(rawMessage)
                                    .build();
                            exchange.getIn().setBody(envelope);
                            
                            // Set Kafka headers for message enrichment
                            exchange.getIn().setHeader("__TypeId__", "fixMessageEnvelope");
                            exchange.getIn().setHeader("senderCompId", senderCompId);
                            exchange.getIn().setHeader("sessionId", properSessionId);
                            exchange.getIn().setHeader("targetCompId", targetCompId);
                            exchange.getIn().setHeader("routeId", routeId);
                            exchange.getIn().setHeader("outputTopic", outputTopic);
                        })
                        .marshal(envelopeFormat)
                        .log("OUTPUT route " + routeId + ": Forwarding envelope to output topic " + outputTopic + " with headers: "
                            + "__TypeId__=fixMessageEnvelope, "
                            + "senderCompId=" + senderCompId + ", "
                            + "sessionId=FIX.4.4:" + senderCompId + "->" + targetCompId + ", "
                            + "targetCompId=" + targetCompId)
                        .to("kafka:" + outputTopic + "?brokers={{kafka.brokers:localhost:9092}}"
                           + "&keySerializer=org.apache.kafka.common.serialization.StringSerializer"
                           + "&valueSerializer=org.apache.kafka.common.serialization.StringSerializer"
                           + "&requestTimeoutMs=10000"
                           + "&maxBlockMs=10000"
                           + "&deliveryTimeoutMs=10000"
                           + "&enableIdempotence=false"
                           + "&transactionalId=")
                        .transform().constant("OK");
                });
            });
        
        // Dead letter channel for failed messages
        from("direct:deadLetterChannel")
            .log("Message failed to route: ${body}")
            .to("kafka:fix-dead-letter?brokers={{kafka.brokers:localhost:9092}}"
               + "&requestTimeoutMs=10000"
               + "&maxBlockMs=10000"
               + "&deliveryTimeoutMs=10000"
               + "&enableIdempotence=false"
               + "&transactionalId=");

        
        //from("netty:tcp://0.0.0.0:9999?textline=true&sync=true")
        // Print the incoming message to the log
        //    .log("NETTY-9999 Received message: ${body}")
        // Set the response body to "OK"
        //    .setBody(constant("OK")); 
        
    }
    
    private String extractEndpointName(String destinationUri) {
        // Extract a simple name from the destination URI for route ID
        if (destinationUri.contains(":")) {
            String[] parts = destinationUri.split(":");
            if (parts.length > 1) {
                // Remove query parameters
                String endpoint = parts[1];
                if (endpoint.contains("?")) {
                    endpoint = endpoint.substring(0, endpoint.indexOf('?'));
                }
                return endpoint.replaceAll("[^a-zA-Z0-9]", "_");
            }
        }
        return "unknown";
    }
}
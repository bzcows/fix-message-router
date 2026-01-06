package com.fix.gateway.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.gateway.model.*;
import com.fix.gateway.processor.FixMessageProcessor;
import com.fix.gateway.processor.MessageEnvelopeFormatProcessor;
import com.fix.gateway.util.FixMessageUtils;
import com.fix.gateway.util.StringMessageEnvelopeParser;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.model.RouteDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Enhanced FIX message router that creates individual routes for each destination
 * with separate exception handling mechanisms.
 */
@Component
@ConditionalOnProperty(name = "fix.routing.mode", havingValue = "enhanced", matchIfMissing = true)
public class EnhancedFixMessageRouter extends RouteBuilder {

    @Autowired
    private EnhancedRoutingConfig enhancedRoutingConfig;
    
    @Autowired
    private FixMessageProcessor fixMessageProcessor;
    
    @Autowired
    private MessageEnvelopeFormatProcessor messageEnvelopeFormatProcessor;
    
    @Autowired
    private DestinationRouteFactory destinationRouteFactory;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void configure() throws Exception {
        
        // Configure JSON data format for FixMessageEnvelope
        JacksonDataFormat envelopeFormat = new JacksonDataFormat(objectMapper, FixMessageEnvelope.class);
        envelopeFormat.setPrettyPrint(false);
        
        // Configure global error handling
        configureGlobalErrorHandling();
        
        // Process INPUT routes with enhanced routing
        configureInputRoutes(envelopeFormat);
        
        // Process OUTPUT routes (can also be enhanced if needed)
        configureOutputRoutes(envelopeFormat);
        
        // Configure dead letter channel
        configureDeadLetterChannel();

        // Configure local Netty 9999 listner
        //configureLocalNetty9999Listener();
    }
    
    /**
     * Configures global error handling for the router.
     */
    private void configureGlobalErrorHandling() {
        EnhancedRoutingConfig.GlobalErrorHandlingConfig globalConfig = 
            enhancedRoutingConfig.getGlobalErrorHandling();
        
        errorHandler(deadLetterChannel("direct:enhancedDeadLetterChannel")
            .maximumRedeliveries(globalConfig.getDefaultMaxRedeliveries())
            .redeliveryDelay(globalConfig.getDefaultRedeliveryDelay())
            .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));
    }
    
    /**
     * Configures INPUT routes with enhanced destination routing.
     */
    private void configureInputRoutes(JacksonDataFormat envelopeFormat) {
        List<EnhancedRouteMapping> inputRoutes = enhancedRoutingConfig.getInputRoutes();
        
        for (EnhancedRouteMapping route : inputRoutes) {
            String routeId = route.getRouteId();
            
            if (route.isEnhancedRouting()) {
                // Use enhanced routing with individual destination routes
                configureEnhancedInputRoute(route, routeId, envelopeFormat);
            } else {
                // Fallback to legacy routing for backward compatibility
                configureLegacyInputRoute(route, routeId, envelopeFormat);
            }
        }
    }
    
    /**
     * Configures an INPUT route with enhanced destination routing.
     */
    private void configureEnhancedInputRoute(
            EnhancedRouteMapping route, 
            String routeId,
            JacksonDataFormat envelopeFormat) {
        
        String inputTopic = route.getInputTopic();
        String consumerGroup = "enhanced-fix-router-input-" + routeId.toLowerCase().replaceAll("[^a-zA-Z0-9]", "-");
        
        // Main input route that consumes from Kafka
        from(buildKafkaConsumerUri(inputTopic, consumerGroup))
            .routeId(routeId + "_ENHANCED_INPUT")
            .log("Enhanced INPUT route " + routeId + ": Received FIX message envelope from Kafka topic " + inputTopic + ": msg=${body}")
            .process(messageEnvelopeFormatProcessor)
            .process(exchange -> {
                // Set route-specific headers
                exchange.getIn().setHeader("routeId", routeId);
                exchange.getIn().setHeader("routeType", RouteType.INPUT);
                exchange.getIn().setHeader("inputTopic", inputTopic);
            })
            .process(fixMessageProcessor)
            .log("Enhanced INPUT route " + routeId + ": Processed FIX message for session: ${header.sessionId}")
            .choice()
                .when(header("destinations").isNotNull())
                    // Use enhanced destination routing
                    .process(new EnhancedDestinationRouter(route))
                .otherwise()
                    .log("Enhanced INPUT route " + routeId + ": No destinations found")
            .end();
        
        // Create individual destination routes
        createDestinationRoutes(route);
    }
    
    /**
     * Configures a legacy INPUT route (for backward compatibility).
     */
    private void configureLegacyInputRoute(
            EnhancedRouteMapping route, 
            String routeId,
            JacksonDataFormat envelopeFormat) {
        
        // This would use the existing toD-based approach
        // For now, we'll log that legacy routing is being used
        from(buildKafkaConsumerUri(route.getInputTopic(), "legacy-" + routeId))
            .routeId(routeId + "_LEGACY_INPUT")
            .log("Using legacy routing for route " + routeId + " (enhancedRouting=false)")
            .process(messageEnvelopeFormatProcessor)
            .process(fixMessageProcessor)
            .log("Legacy route processing complete");
    }
    
    /**
     * Creates individual destination routes for an enhanced route.
     * This method creates routes directly using from() within configure().
     */
    private void createDestinationRoutes(EnhancedRouteMapping route) {
        List<DestinationConfig> destinationConfigs = route.getDestinationConfigs();
        
        for (int i = 0; i < destinationConfigs.size(); i++) {
            DestinationConfig destConfig = destinationConfigs.get(i);
            String routeId = buildDestinationRouteId(route.getRouteId(), i);
            String destinationUri = destConfig.buildCompleteUri();
            
            // Create destination route directly
            from("direct:" + routeId)
                .routeId(routeId)
                .log("Destination route " + routeId + ": Processing message for " + destinationUri)
                .setProperty("destinationUri", org.apache.camel.builder.Builder.constant(destinationUri))
                .setProperty("parentRouteId", org.apache.camel.builder.Builder.constant(route.getRouteId()))
                .setProperty("destinationIndex", org.apache.camel.builder.Builder.constant(i))
                // Configure exception handling
                .onException(Exception.class)
                    .handled(true)
                    .maximumRedeliveries(destConfig.getMaxRetries())
                    .redeliveryDelay(destConfig.getRetryDelay())
                    .useOriginalMessage()
                    .log("Destination " + destinationUri + " failed after ${header.CamelRedeliveryCounter} attempts: ${exception.message}")
                    .choice()
                        .when(simple("${header.CamelRedeliveryCounter} >= " + destConfig.getMaxRetries()))
                            .log("Destination " + destinationUri + ": Maximum retries exceeded, sending to dead letter topic")
                            .toF("kafka:%s?brokers={{kafka.brokers:localhost:9092}}"
                                + "&keySerializer=org.apache.kafka.common.serialization.StringSerializer"
                                + "&valueSerializer=org.apache.kafka.common.serialization.StringSerializer"
                                + "&requestTimeoutMs=10000", destConfig.getDeadLetterTopic(route.getRouteId()))
                        .endChoice()
                    .end()
                .end()
                // Send to destination
                .to(destinationUri)
                .log("Destination route " + routeId + ": Successfully sent to " + destinationUri);
        }
    }
    
    /**
     * Builds destination route ID.
     */
    private String buildDestinationRouteId(String parentRouteId, int destinationIndex) {
        return parentRouteId + "_DEST_" + destinationIndex;
    }
    
    /**
     * Configures OUTPUT routes.
     */
    private void configureOutputRoutes(JacksonDataFormat envelopeFormat) {
        List<EnhancedRouteMapping> outputRoutes = enhancedRoutingConfig.getOutputRoutes();
        
        for (EnhancedRouteMapping route : outputRoutes) {
            String routeId = route.getRouteId();
            String senderCompId = route.getSenderCompId();
            String targetCompId = route.getTargetCompId();
            String outputTopic = route.getOutputTopic();
            
            // For each destination in the OUTPUT route, create a listening endpoint
            List<DestinationConfig> destinationConfigs = route.getDestinationConfigs();
            for (int i = 0; i < destinationConfigs.size(); i++) {
                DestinationConfig destConfig = destinationConfigs.get(i);
                String destinationUri = destConfig.buildCompleteUri();
                
                String listenerRouteId = routeId + "_ENHANCED_FROM_" + extractEndpointName(destinationUri) + "_" + i;
                
                from(destinationUri)
                    .routeId(listenerRouteId)
                    .log("Enhanced OUTPUT route " + routeId + ": Received raw FIX message from " + destinationUri+ " : msg= ${body}")
                    .process(exchange -> {
                        // Build envelope from raw message
                        String rawMessage = exchange.getIn().getBody(String.class);
                        String properSessionId = "FIX.4.4:" + senderCompId + "->" + targetCompId;
                        
                        // Debug logging for raw message analysis
                        if (rawMessage != null) {
                            System.out.println("[DEBUG] EnhancedFixMessageRouter OUTPUT - Raw message length: " + rawMessage.length());
                            System.out.println("[DEBUG] EnhancedFixMessageRouter OUTPUT - Raw message first 100 chars: " +
                                (rawMessage.length() > 100 ? rawMessage.substring(0, 100) : rawMessage));
                            // Check for escape sequences
                            if (rawMessage.contains("\\u0001")) {
                                System.out.println("[DEBUG] EnhancedFixMessageRouter OUTPUT - Message contains \\u0001 escape sequence");
                            }
                            // Check for actual SOH character
                            boolean hasActualSOH = rawMessage.indexOf('\u0001') >= 0;
                            System.out.println("[DEBUG] EnhancedFixMessageRouter OUTPUT - Message contains actual SOH char: " + hasActualSOH);
                            
                            // Process the raw message to handle escape sequences
                            String processedMessage = FixMessageUtils.processRawMessage(rawMessage);
                            if (!processedMessage.equals(rawMessage)) {
                                System.out.println("[DEBUG] EnhancedFixMessageRouter OUTPUT - Message was processed (escape sequences converted)");
                                rawMessage = processedMessage;
                            }
                        }
                        
                        FixMessageEnvelope envelope = FixMessageEnvelope.builder()
                                .sessionId(properSessionId)
                                .senderCompId(senderCompId)
                                .targetCompId(targetCompId)
                                .rawMessage(rawMessage)
                                .build();
                        exchange.getIn().setBody(envelope);
                        
                        // Set Kafka headers
                        exchange.getIn().setHeader("__TypeId__", "fixMessageEnvelope");
                        exchange.getIn().setHeader("senderCompId", senderCompId);
                        exchange.getIn().setHeader("sessionId", properSessionId);
                        exchange.getIn().setHeader("targetCompId", targetCompId);
                        exchange.getIn().setHeader("routeId", routeId);
                        exchange.getIn().setHeader("outputTopic", outputTopic);
                    })
                    .marshal(envelopeFormat)
                    .log("Enhanced OUTPUT route " + routeId + ": Forwarding envelope to output topic " + outputTopic)
                    .to(buildKafkaProducerUri(outputTopic))
                    .transform().constant("OK");
            }
        }
    }
    
    /**
     * Configures the dead letter channel for enhanced routing.
     */
    private void configureDeadLetterChannel() {
        from("direct:enhancedDeadLetterChannel")
            .routeId("ENHANCED_DEAD_LETTER_CHANNEL")
            .log("Enhanced dead letter channel: Message failed to route: ${body}")
            .to("kafka:enhanced-fix-dead-letter?brokers={{kafka.brokers:localhost:9092}}"
               + "&requestTimeoutMs=10000"
               + "&maxBlockMs=10000"
               + "&deliveryTimeoutMs=10000");
    }
    
    /**
     * Builds Kafka consumer URI.
     */
    private String buildKafkaConsumerUri(String topic, String groupId) {
        return "kafka:" + topic
            + "?brokers={{kafka.brokers:localhost:9092}}"
            + "&groupId=" + groupId
            + "&autoOffsetReset={{kafka.autoOffsetReset:earliest}}"
            + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
            + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
            + "&sessionTimeoutMs=30000"
            + "&maxPollRecords=10";
    }
    
    /**
     * Builds Kafka producer URI.
     */
    private String buildKafkaProducerUri(String topic) {
        return "kafka:" + topic
            + "?brokers={{kafka.brokers:localhost:9092}}"
            + "&keySerializer=org.apache.kafka.common.serialization.StringSerializer"
            + "&valueSerializer=org.apache.kafka.common.serialization.StringSerializer"
            + "&requestTimeoutMs=10000";
    }
    
    /**
     * Extracts endpoint name from URI for route ID generation.
     */
    private String extractEndpointName(String destinationUri) {
        if (destinationUri.contains(":")) {
            String[] parts = destinationUri.split(":");
            if (parts.length > 1) {
                String endpoint = parts[1];
                if (endpoint.contains("?")) {
                    endpoint = endpoint.substring(0, endpoint.indexOf('?'));
                }
                return endpoint.replaceAll("[^a-zA-Z0-9]", "_");
            }
        }
        return "unknown";
    }
    
    /**
     * Processor for routing messages to enhanced destination routes.
     */
    private static class EnhancedDestinationRouter implements Processor {
        private final EnhancedRouteMapping route;
        
        EnhancedDestinationRouter(EnhancedRouteMapping route) {
            this.route = route;
        }
        
        @Override
        public void process(Exchange exchange) throws Exception {
            String messageBody = exchange.getIn().getBody(String.class);
            List<DestinationConfig> destinationConfigs = route.getDestinationConfigs();
            
            // Get msgType from headers (set by FixMessageProcessor)
            String msgType = exchange.getIn().getHeader("msgType", String.class);
            
            // Log message body details for debugging SOH issue
            if (messageBody != null && !messageBody.isEmpty()) {
                int length = messageBody.length();
                char lastChar = messageBody.charAt(length - 1);
                System.out.println("[DEBUG] EnhancedDestinationRouter - Message body length: " + length +
                                 ", last char code: " + (int)lastChar +
                                 " (0x" + Integer.toHexString(lastChar) + ")");
                // Also check for SOH characters
                int sohCount = 0;
                for (int i = 0; i < messageBody.length(); i++) {
                    if (messageBody.charAt(i) == '\u0001') {
                        sohCount++;
                    }
                }
                System.out.println("[DEBUG] EnhancedDestinationRouter - Total SOH characters: " + sohCount);
            }
            
            // Log msgType for debugging
            System.out.println("[DEBUG] EnhancedDestinationRouter - msgType: " + msgType);
            System.out.println("[DEBUG] EnhancedDestinationRouter - Total destinations: " + destinationConfigs.size());
            
            // Log all headers for debugging
            System.out.println("[DEBUG] EnhancedDestinationRouter - All headers: " + exchange.getIn().getHeaders());
            
            for (int i = 0; i < destinationConfigs.size(); i++) {
                DestinationConfig destConfig = destinationConfigs.get(i);
                System.out.println("[DEBUG] EnhancedDestinationRouter - Processing destination " + i +
                                 " (uri: " + destConfig.getUri() + ")");
                System.out.println("[DEBUG] EnhancedDestinationRouter - Destination msgTypes: " + destConfig.getMsgTypes());
                
                // Check if destination should receive this message type
                boolean matches = destConfig.matchesMsgType(msgType);
                System.out.println("[DEBUG] EnhancedDestinationRouter - matchesMsgType(" + msgType + ") = " + matches);
                
                if (!matches) {
                    System.out.println("[DEBUG] EnhancedDestinationRouter - Skipping destination " + i +
                                     " (uri: " + destConfig.getUri() + ") because msgType " + msgType +
                                     " not in allowed list: " + destConfig.getMsgTypes());
                    continue;
                }
                
                String destRouteId = route.getRouteId() + "_DEST_" + i;
                System.out.println("[DEBUG] EnhancedDestinationRouter - Sending to destination route: " + destRouteId);
                
                // Create new exchange for each destination
                Exchange destExchange = exchange.getContext()
                    .getEndpoint("direct:" + destRouteId)
                    .createExchange(ExchangePattern.InOnly);
                
                // Copy message body and headers from original exchange
                destExchange.getIn().setBody(messageBody);
                destExchange.getIn().setHeaders(exchange.getIn().getHeaders());
                // Copy properties
                destExchange.getProperties().putAll(exchange.getProperties());
                destExchange.setProperty("originalExchange", exchange);
                destExchange.setProperty("destinationIndex", i);
                destExchange.setProperty("destinationConfig", destConfig);
                
                // Apply destination-specific parallel processing
                if (destConfig.isParallelProcessing()) {
                    // Send asynchronously
                    exchange.getContext().createProducerTemplate()
                        .asyncSend("direct:" + destRouteId, destExchange);
                } else {
                    // Send synchronously
                    exchange.getContext().createProducerTemplate()
                        .send("direct:" + destRouteId, destExchange);
                }
                
                // Check if we should stop on exception
                if (destConfig.isStopOnException() && destExchange.getException() != null) {
                    exchange.setException(destExchange.getException());
                    break;
                }
            }
        }
    }

}
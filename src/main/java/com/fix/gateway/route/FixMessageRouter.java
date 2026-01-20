package com.fix.gateway.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.gateway.model.*;
import com.fix.gateway.processor.FixMessageProcessor;
import com.fix.gateway.processor.MessageEnvelopeFormatProcessor;
import com.fix.gateway.util.FixMessageUtils;
import com.fix.gateway.util.MvelExpressionEvaluator;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.kafka.KafkaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * FIX message router that creates individual routes for each destination
 * with separate exception handling mechanisms.
 */
@Component
public class FixMessageRouter extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(FixMessageRouter.class);
    
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
        
        // Configure JSON data format for FixMessageEnvelope
        JacksonDataFormat envelopeFormat = new JacksonDataFormat(objectMapper, FixMessageEnvelope.class);
        envelopeFormat.setPrettyPrint(false);
        
        // Configure global error handling
        configureGlobalErrorHandling();
        
        // Process INPUT routes
        configureInputRoutes(envelopeFormat);
        
        // Process OUTPUT routes
        configureOutputRoutes(envelopeFormat);
        
        // Configure dead letter channel
        configureDeadLetterChannel();
    }
    
    /**
     * Configures global error handling for the router.
     */
    private void configureGlobalErrorHandling() {
        RoutingConfig.GlobalErrorHandlingConfig globalConfig =
            routingConfig.getGlobalErrorHandling();
        
        errorHandler(deadLetterChannel("direct:deadLetterChannel")
            .maximumRedeliveries(globalConfig.getDefaultMaxRedeliveries())
            .redeliveryDelay(globalConfig.getDefaultRedeliveryDelay())
            .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));
    }
    
    /**
     * Configures INPUT routes with destination routing.
     */
    private void configureInputRoutes(JacksonDataFormat envelopeFormat) {
        List<RouteMapping> inputRoutes = routingConfig.getInputRoutes();
        
        for (RouteMapping route : inputRoutes) {
            String routeId = route.getRouteId();
            
            // Use ordered processing with manual commits for guaranteed ordering
            configureOrderedInputRoute(route, routeId, envelopeFormat);
            log.info("Configured ordered processing for route {}", routeId);
        }
    }
    
    /**
     * Configures an INPUT route with ordered processing and manual offset commits.
     * This guarantees strict ordering per partition with crash safety.
     */
    private void configureOrderedInputRoute(
            RouteMapping route,
            String routeId,
            JacksonDataFormat envelopeFormat) {
        
        String inputTopic = route.getInputTopic();
        String consumerGroup = "fix-router-" + routeId.toLowerCase().replaceAll("[^a-zA-Z0-9]", "-");
        
        from(buildKafkaConsumerUri(inputTopic, consumerGroup))
            .routeId(routeId + "_INPUT")
            .process(exchange -> {
                // Log partition/offset for debugging
                Integer partition = exchange.getIn().getHeader("kafka.PARTITION", Integer.class);
                Long offset = exchange.getIn().getHeader("kafka.OFFSET", Long.class);
                log.debug("Route {}: Processing partition {} offset {}", routeId, partition, offset);
                
                // Set route-specific headers
                exchange.getIn().setHeader("routeId", routeId);
                exchange.getIn().setHeader("routeType", RouteType.INPUT);
                exchange.getIn().setHeader("inputTopic", inputTopic);
            })
            .process(messageEnvelopeFormatProcessor)
            .process(fixMessageProcessor)
            .log("INPUT route " + routeId + ": Processed FIX message for session: ${header.sessionId}")
            .choice()
                .when(header("destinations").isNotNull())
                    // Use sequential destination processing
                    .process(new SequentialDestinationProcessor(route))
                .otherwise()
                    .log("INPUT route " + routeId + ": No destinations found")
            .end()
            // MANUAL COMMIT after successful processing
            .process(exchange -> {
                // Get the manual commit from headers
                Object manualCommit = exchange.getIn().getHeader(KafkaConstants.MANUAL_COMMIT);
                if (manualCommit != null) {
                    try {
                        String className = manualCommit.getClass().getName();
                        
                        // For Camel Kafka, we can try to find the commit method
                        // Check for any Kafka manual commit implementation (KafkaManualCommit, KafkaManualSyncCommit, etc.)
                        if (className.contains("KafkaManual")) {
                            try {
                                java.lang.invoke.MethodHandles.Lookup lookup =
                                    java.lang.invoke.MethodHandles.lookup();
                                java.lang.invoke.MethodType mt =
                                    java.lang.invoke.MethodType.methodType(void.class);
                                java.lang.invoke.MethodHandle mh =
                                    lookup.findVirtual(manualCommit.getClass(), "commit", mt);
                                mh.invoke(manualCommit);
                                
                                Integer partition = exchange.getIn().getHeader("kafka.PARTITION", Integer.class);
                                Long offset = exchange.getIn().getHeader("kafka.OFFSET", Long.class);
                                log.debug("Route {}: Committed offset for partition {} offset {}",
                                    routeId, partition, offset);
                            } catch (NoSuchMethodException | IllegalAccessException e) {
                                log.warn("Route {}: Commit method not found or inaccessible in {}: {}",
                                    routeId, className, e.getMessage());
                            }
                        } else {
                            log.warn("Route {}: Unknown manual commit type: {}", routeId, className);
                        }
                    } catch (Throwable e) {
                        log.warn("Route {}: Failed to commit offset: {}", routeId, e.getMessage());
                    }
                } else {
                    log.debug("Route {}: No manual commit available (auto-commit may be enabled)", routeId);
                }
            })
            .log("Route " + routeId + ": Completed processing with commit");
    }
    
    /**
     * Configures OUTPUT routes.
     */
    private void configureOutputRoutes(JacksonDataFormat envelopeFormat) {
        List<RouteMapping> outputRoutes = routingConfig.getOutputRoutes();
        
        for (RouteMapping route : outputRoutes) {
            String routeId = route.getRouteId();
            String senderCompId = route.getSenderCompId();
            String targetCompId = route.getTargetCompId();
            String outputTopic = route.getOutputTopic();
            PartitionStrategy partitionStrategy = route.getPartitionStrategy();
            String partitionExpression = route.getPartitionExpression();
            
            // For each destination in the OUTPUT route, create a listening endpoint
            List<DestinationConfig> destinationConfigs = route.getDestinationConfigs();
            for (int i = 0; i < destinationConfigs.size(); i++) {
                DestinationConfig destConfig = destinationConfigs.get(i);
                String destinationUri = destConfig.buildCompleteUri();
                
                String listenerRouteId = routeId + "_FROM_" + extractEndpointName(destinationUri) + "_" + i;
                
                from(destinationUri)
                    .routeId(listenerRouteId)
                    .log("OUTPUT route " + routeId + ": Received raw FIX message from " + destinationUri)
                    .process(exchange -> {
                        // Build envelope from raw message
                        String rawMessage = exchange.getIn().getBody(String.class);
                        String properSessionId = "FIX.4.4:" + senderCompId + "->" + targetCompId;
                        
                        // Process the raw message to handle escape sequences
                        if (rawMessage != null) {
                            rawMessage = FixMessageUtils.processRawMessage(rawMessage);
                        }
                        
                        // Parse FIX tags and extract common fields
                        Map<Integer, String> parsedTags = MvelExpressionEvaluator.parseFixTags(rawMessage);
                        
                        // Extract common fields for easy access
                        String symbol = parsedTags.get(55);
                        String side = parsedTags.get(54);
                        String orderQty = parsedTags.get(38);
                        String price = parsedTags.get(44);
                        
                        FixMessageEnvelope envelope = FixMessageEnvelope.builder()
                                .sessionId(properSessionId)
                                .senderCompId(senderCompId)
                                .targetCompId(targetCompId)
                                .rawMessage(rawMessage)
                                .symbol(symbol)
                                .side(side)
                                .orderQty(orderQty)
                                .price(price)
                                .parsedTags(parsedTags)
                                .build();
                        exchange.getIn().setBody(envelope);
                        
                        // Set Kafka headers
                        exchange.getIn().setHeader("__TypeId__", "fixMessageEnvelope");
                        exchange.getIn().setHeader("senderCompId", senderCompId);
                        exchange.getIn().setHeader("sessionId", properSessionId);
                        exchange.getIn().setHeader("targetCompId", targetCompId);
                        exchange.getIn().setHeader("routeId", routeId);
                        exchange.getIn().setHeader("outputTopic", outputTopic);
                        exchange.getIn().setHeader("partitionStrategy", partitionStrategy);
                        exchange.getIn().setHeader("partitionExpression", partitionExpression);
                        
                        // Apply content-based routing if configured
                        if (partitionStrategy != PartitionStrategy.NONE && partitionExpression != null && !partitionExpression.trim().isEmpty()) {
                            // Use already parsed tags from envelope
                            Object partitionResult = MvelExpressionEvaluator.evaluatePartitionExpression(
                                partitionExpression, envelope, envelope.getParsedTags());
                            
                            if (partitionResult != null) {
                                if (partitionStrategy == PartitionStrategy.KEY) {
                                    // Set partition key (will be used as Kafka message key)
                                    exchange.getIn().setHeader("kafka.KEY", partitionResult.toString());
                                    log.debug("Setting partition key: {}", partitionResult);
                                } else if (partitionStrategy == PartitionStrategy.EXPR) {
                                    // Set partition number (must be integer)
                                    try {
                                        int partitionNum;
                                        if (partitionResult instanceof Number) {
                                            partitionNum = ((Number) partitionResult).intValue();
                                        } else {
                                            partitionNum = Integer.parseInt(partitionResult.toString());
                                        }
                                        exchange.getIn().setHeader("kafka.PARTITION", partitionNum);
                                        log.debug("Setting partition number: {}", partitionNum);
                                    } catch (NumberFormatException e) {
                                        log.error("Invalid partition number from expression: {}", partitionResult);
                                    }
                                }
                            }
                        }
                    })
                    .marshal(envelopeFormat)
                    .log("OUTPUT route " + routeId + ": Forwarding envelope to output topic " + outputTopic)
                    .to(buildKafkaProducerUri(outputTopic))
                    .transform().constant("OK");
            }
        }
    }
    
    /**
     * Configures the dead letter channel.
     */
    private void configureDeadLetterChannel() {
        from("direct:deadLetterChannel")
            .routeId("DEAD_LETTER_CHANNEL")
            .log("Dead letter channel: Message failed to route: ${body}")
            .to("kafka:fix-dead-letter?brokers={{kafka.brokers:localhost:9092}}"
               + "&requestTimeoutMs=10000"
               + "&maxBlockMs=10000"
               + "&deliveryTimeoutMs=10000");
    }
    
    /**
     * Builds Kafka consumer URI for single-record processing with manual commits.
     */
    private String buildKafkaConsumerUri(String topic, String groupId) {
        return "kafka:" + topic
            + "?brokers={{kafka.brokers:localhost:9092}}"
            + "&groupId=" + groupId
            + "&autoOffsetReset={{kafka.autoOffsetReset:earliest}}"
            + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
            + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
            + "&sessionTimeoutMs=30000"
            + "&maxPollRecords=1"  // Process ONE record at a time
            + "&autoCommitEnable=false"  // Disable auto-commit
            + "&allowManualCommit=true"  // Enable manual commits
            + "&breakOnFirstError=false";  // Continue on error for transient network issues
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
     * Processor for sequential destination processing with guaranteed ordering.
     * Processes destinations one by one, waiting for each to complete before moving to the next.
     * Includes retry logic for transient network issues.
     */
    private static class SequentialDestinationProcessor implements Processor, org.apache.camel.Service {
        private final RouteMapping route;
        private volatile org.apache.camel.ProducerTemplate producerTemplate;
        
        SequentialDestinationProcessor(RouteMapping route) {
            this.route = route;
        }
        
        @Override
        public void process(Exchange exchange) throws Exception {
            String messageBody = exchange.getIn().getBody(String.class);
            List<DestinationConfig> destinationConfigs = route.getDestinationConfigs();
            
            // Get msgType from headers (set by FixMessageProcessor)
            String msgType = exchange.getIn().getHeader("msgType", String.class);
            
            log.debug("Processing msgType {} for {} destinations",
                msgType, destinationConfigs.size());
            
            // Initialize producer template lazily
            if (producerTemplate == null) {
                synchronized (this) {
                    if (producerTemplate == null) {
                        producerTemplate = exchange.getContext().createProducerTemplate();
                        producerTemplate.start();
                        log.debug("Created and started ProducerTemplate for SequentialDestinationProcessor");
                    }
                }
            }
            
            for (int i = 0; i < destinationConfigs.size(); i++) {
                DestinationConfig destConfig = destinationConfigs.get(i);
                
                // Check if destination should receive this message type
                if (!destConfig.matchesMsgType(msgType)) {
                    log.debug("Skipping destination {} (uri: {}) - msgType {} not in allowed list: {}",
                        i, destConfig.getUri(), msgType, destConfig.getMsgTypes());
                    continue;
                }
                
                String destinationUri = destConfig.buildCompleteUri();
                log.debug("Sending to destination {}: {}", i, destinationUri);
                
                // Retry logic for network errors
                boolean success = false;
                Exception lastException = null;
                int maxRetries = destConfig.getMaxRetries();
                
                for (int retry = 0; retry <= maxRetries; retry++) {
                    try {
                        // Send SYNCHRONOUSLY to maintain ordering using the shared producer template
                        producerTemplate.send(destinationUri, exchange);
                        
                        // Check for failure
                        if (exchange.getException() != null) {
                            throw exchange.getException();
                        }
                        
                        success = true;
                        log.debug("Successfully sent to destination {} (attempt {})",
                            i, retry + 1);
                        break;
                        
                    } catch (Exception e) {
                        lastException = e;
                        
                        // Check if this is a network-related error
                        boolean isNetworkError = isNetworkError(e);
                        
                        if (isNetworkError && retry < maxRetries) {
                            // Wait before retry
                            long retryDelay = destConfig.getRetryDelay();
                            String errorMsg = e.getMessage();
                            if (errorMsg == null || errorMsg.isEmpty()) {
                                errorMsg = e.getClass().getName();
                            }
                            log.warn("Network error sending to destination {} (attempt {}): {}. Retrying in {}ms",
                                i, retry + 1, errorMsg, retryDelay);
                            try {
                                Thread.sleep(retryDelay);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw ie;
                            }
                        } else {
                            // Not a network error or retries exhausted
                            String errorMsg = e.getMessage();
                            if (errorMsg == null || errorMsg.isEmpty()) {
                                errorMsg = e.getClass().getName();
                            }
                            log.debug("Non-network error or retries exhausted for destination {} (attempt {}): {}",
                                i, retry + 1, errorMsg);
                            break;
                        }
                    }
                }
                
                if (!success) {
                    String errorMessage = "Unknown error";
                    if (lastException != null) {
                        if (lastException.getMessage() != null && !lastException.getMessage().isEmpty()) {
                            errorMessage = lastException.getMessage();
                        } else {
                            errorMessage = lastException.getClass().getName() + " (no message)";
                        }
                    }
                    
                    log.error("Failed to send to destination {} (uri: {}) after {} attempts: {}",
                        i, destinationUri, maxRetries + 1, errorMessage);
                    
                    // Check if we should stop on exception
                    if (destConfig.isStopOnException()) {
                        throw lastException != null ? lastException : new RuntimeException("Failed to send to destination " + i + " (uri: " + destinationUri + ")");
                    }
                    // Otherwise continue to next destination
                }
            }
        }
        
        @Override
        public void start() {
            // Template is started lazily when first used
        }
        
        @Override
        public void stop() {
            if (producerTemplate != null) {
                try {
                    producerTemplate.stop();
                    log.debug("Stopped ProducerTemplate for SequentialDestinationProcessor");
                } catch (Exception e) {
                    log.warn("Error stopping ProducerTemplate: {}", e.getMessage());
                }
            }
        }
        
        /**
         * Determines if an exception is likely a network-related error.
         */
        private boolean isNetworkError(Exception e) {
            if (e == null || e.getMessage() == null) {
                return false;
            }
            
            String message = e.getMessage().toLowerCase();
            String className = e.getClass().getName().toLowerCase();
            
            // Check for common network error patterns
            return message.contains("connection") ||
                   message.contains("timeout") ||
                   message.contains("network") ||
                   message.contains("socket") ||
                   message.contains("io") ||
                   message.contains("connect") ||
                   message.contains("refused") ||
                   className.contains("connect") ||
                   className.contains("timeout") ||
                   className.contains("io") ||
                   e instanceof java.net.ConnectException ||
                   e instanceof java.net.SocketTimeoutException ||
                   e instanceof java.io.IOException;
       }
   }
}
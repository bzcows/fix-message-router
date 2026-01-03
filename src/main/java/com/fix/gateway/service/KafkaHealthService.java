package com.fix.gateway.service;

import com.fix.gateway.model.RoutingConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KafkaHealthService {

    private final ApplicationContext applicationContext;
    private final String bootstrapServers;
    private final RoutingConfig routingConfig;
    
    private static final int MAX_RETRY_SECONDS = 10;
    private static final int RETRY_INTERVAL_MS = 1000;
    private static final int TOPIC_PARTITIONS = 1;
    private static final short TOPIC_REPLICATION_FACTOR = 1;
    
    public KafkaHealthService(
            ApplicationContext applicationContext,
            @Value("${kafka.brokers:localhost:9092}") String bootstrapServers,
            RoutingConfig routingConfig) {
        this.applicationContext = applicationContext;
        this.bootstrapServers = bootstrapServers;
        this.routingConfig = routingConfig;
    }
    
    /**
     * Check Kafka connectivity with retry logic
     * @return true if Kafka is reachable, false otherwise
     */
    public boolean checkKafkaConnectivity() {
        log.info("Checking Kafka connectivity to brokers: {}", bootstrapServers);
        
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000);
        
        Instant startTime = Instant.now();
        int attempt = 1;
        
        try (AdminClient adminClient = AdminClient.create(props)) {
            while (true) {
                try {
                    log.debug("Attempt {} to connect to Kafka", attempt);
                    ListTopicsResult topics = adminClient.listTopics();
                    topics.names().get(3, TimeUnit.SECONDS); // Try to list topics with 3-second timeout
                    
                    Duration elapsed = Duration.between(startTime, Instant.now());
                    log.info("Successfully connected to Kafka after {} attempts ({} ms)",
                            attempt, elapsed.toMillis());
                    
                    // Now ensure all required topics exist
                    ensureTopicsExist(adminClient);
                    
                    return true;
                    
                } catch (ExecutionException | TimeoutException e) {
                    Duration elapsed = Duration.between(startTime, Instant.now());
                    
                    if (elapsed.getSeconds() >= MAX_RETRY_SECONDS) {
                        log.error("Failed to connect to Kafka after {} seconds. Last error: {}",
                                MAX_RETRY_SECONDS, e.getMessage());
                        return false;
                    }
                    
                    log.warn("Kafka connection attempt {} failed ({} ms elapsed): {}",
                            attempt, elapsed.toMillis(), e.getMessage());
                    
                    attempt++;
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Kafka health check interrupted", ie);
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to create Kafka AdminClient: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Ensure all required topics from routing configuration exist
     */
    private void ensureTopicsExist(AdminClient adminClient) {
        try {
            // Get all existing topics
            Set<String> existingTopics = adminClient.listTopics().names().get(5, TimeUnit.SECONDS);
            
            // Collect all required topics from routing configuration
            Set<String> requiredTopics = new HashSet<>();
            routingConfig.getRoutes().forEach(route -> {
                requiredTopics.add(route.getInputTopic());
                requiredTopics.add(route.getOutputTopic());
            });
            
            // Add dead letter topic
            requiredTopics.add("fix-dead-letter");
            
            // Filter out topics that already exist
            List<NewTopic> topicsToCreate = requiredTopics.stream()
                .filter(topic -> topic != null && !topic.trim().isEmpty())
                .filter(topic -> !existingTopics.contains(topic))
                .map(topic -> new NewTopic(topic, TOPIC_PARTITIONS, TOPIC_REPLICATION_FACTOR))
                .collect(Collectors.toList());
            
            if (!topicsToCreate.isEmpty()) {
                log.info("Creating {} Kafka topics: {}", topicsToCreate.size(),
                    topicsToCreate.stream().map(NewTopic::name).collect(Collectors.joining(", ")));
                
                CreateTopicsResult result = adminClient.createTopics(topicsToCreate);
                result.all().get(10, TimeUnit.SECONDS);
                
                log.info("Successfully created all required topics");
            } else {
                log.debug("All required topics already exist");
            }
            
        } catch (Exception e) {
            log.warn("Failed to ensure topics exist: {}", e.getMessage());
            // Don't fail the health check if topic creation fails
            // Topics might be auto-created when producers/consumers connect
        }
    }
    
    /**
     * Perform health check and shutdown application if Kafka is unreachable
     */
    public void healthCheckWithShutdown() {
        if (!checkKafkaConnectivity()) {
            log.error("Kafka health check failed after {} seconds. Shutting down application.", MAX_RETRY_SECONDS);
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }
}
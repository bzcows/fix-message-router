package com.fix.gateway.model;

/**
 * Enum representing the partition strategy for content-based routing.
 * This determines how messages are routed to specific Kafka partitions.
 */
public enum PartitionStrategy {
    /**
     * No content-based routing - messages are sent to default partition
     */
    NONE,
    
    /**
     * Key-based routing - MVEL expression determines the key for Kafka topic
     */
    KEY,
    
    /**
     * Expression-based routing - MVEL expression directly determines the partition number
     */
    EXPR
}
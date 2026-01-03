# Enhanced FIX Message Routing Implementation

## Overview

This implementation provides an alternative to the current `toD()`-based routing approach by creating individual routes for each destination with separate exception handling mechanisms.

## Key Benefits

1. **Individual Exception Handling**: Each destination can have its own retry policies, timeouts, and dead letter queues
2. **Better Observability**: Each route has a unique ID for logging and monitoring
3. **Isolated Failures**: One destination failure doesn't affect others
4. **Configuration Flexibility**: Different policies per destination type (Netty vs Kafka vs others)
5. **Easier Testing**: Individual routes can be tested in isolation

## Architecture

### Current Approach (`toD`-based)
- Single route with parallel splitting to multiple destinations
- Shared exception handling for all destinations
- Complex URI manipulation logic
- Limited control over individual destination behavior

### Enhanced Approach (Individual Routes)
- Separate route created for each destination
- Individual exception handlers per destination
- Configuration-driven routing policies
- Clear separation of concerns

## Implementation Details

### New Classes Created

1. **`DestinationConfig.java`** - Configuration for individual destinations with policies
2. **`EnhancedRouteMapping.java`** - Enhanced route configuration with destination-specific settings
3. **`EnhancedRoutingConfig.java`** - Main enhanced configuration container
4. **`EnhancedFixMessageRouter.java`** - Main router using individual destination routes
5. **`EnhancedRoutingConfigurationLoader.java`** - Loader for enhanced configuration
6. **`RoutingModeConfig.java`** - Configuration for switching between routing modes

### Configuration Files

1. **`enhanced-routing-config.json`** - Example enhanced configuration
2. **`routing-config.json`** - Legacy configuration (backward compatible)

## Configuration Example

```json
{
  "enableEnhancedRouting": true,
  "routes": [
    {
      "routeId": "FIX4.4:BANZ->GTWY",
      "type": "INPUT",
      "inputTopic": "fix.GTWY.BANZ.input",
      "enhancedRouting": true,
      "destinationConfigs": [
        {
          "uri": "netty:tcp://localhost:9999",
          "maxRetries": 5,
          "retryDelay": 2000,
          "timeout": 10000,
          "deadLetterTopic": "dead-letter-netty-9999",
          "endpointParameters": {
            "textline": "true",
            "sync": "true",
            "connectTimeout": "5000"
          }
        }
      ]
    }
  ]
}
```

## Usage

### 1. Enable Enhanced Routing

Add to `application.yml`:
```yaml
fix:
  routing:
    mode: enhanced  # Options: "legacy" or "enhanced"
```

### 2. Configure Enhanced Routes

Create or modify `enhanced-routing-config.json` with your route definitions.

### 3. Run the Application

The system will automatically:
- Load enhanced configuration if available
- Fall back to legacy configuration if needed
- Create individual routes for each destination
- Apply destination-specific exception handling

## Migration Path

1. **Phase 1**: Run both routers in parallel with `fix.routing.mode=legacy`
2. **Phase 2**: Test enhanced routing with specific routes using `enhancedRouting: true` per route
3. **Phase 3**: Switch to `fix.routing.mode=enhanced` for all routes
4. **Phase 4**: Remove legacy routing code if no longer needed

## Backward Compatibility

The system maintains full backward compatibility:
- Can load and convert legacy `routing-config.json`
- Each route can individually enable/disable enhanced routing
- Global configuration switch between modes

## Monitoring and Debugging

Each destination route provides:
- Unique route IDs (e.g., `FIX4.4:BANZ->GTWY_DEST_0`)
- Individual logging for success/failure
- Separate dead letter topics per destination
- Destination-specific metrics

## Performance Considerations

- **Slightly higher overhead** due to individual route creation
- **Better failure isolation** prevents cascading failures
- **Improved resource management** with destination-specific timeouts
- **Parallel processing** maintained for destinations that support it

## Testing

Test individual destination routes by:
1. Sending messages to the direct endpoint: `direct:FIX4.4:BANZ->GTWY_DEST_0`
2. Verifying exception handling with simulated failures
3. Checking dead letter queue routing

## Future Enhancements

1. Dynamic route addition/removal at runtime
2. Circuit breaker patterns for destinations
3. Health checks for destination endpoints
4. Metrics integration with Prometheus/Grafana
5. Configuration hot-reload capability
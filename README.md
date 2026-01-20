# FIX Message Router

A Spring Boot application using Apache Camel to route FIX messages from Kafka to multiple destinations (Netty, JMS, Kafka) based on configuration.

## Tech Stack

- **JDK 21**
- **Spring Boot 3.5.6**
- **Apache Camel 4.14.0**
- **Camel Components:**
  - camel-kafka
  - camel-netty  
  - camel-jms
- **Jackson with Java 8 Date/Time support**

## Architecture

The application reads FIX messages from a Kafka topic `fix-inbound-topic`, extracts session details, and routes the raw messages to configured destinations based on senderCompId and targetCompId matching.

### Message Flow

1. **Kafka Consumer**: Listens to `fix-inbound-topic` for FIX message envelopes
2. **Message Processing**: Extracts metadata (sessionId, senderCompId, targetCompId)
3. **Routing Logic**: Matches against configuration to find destination URIs
4. **Message Forwarding**: Sends raw FIX message to all matched destinations

### Configuration

Routing configuration is defined in `src/main/resources/routing-config.json`:

```json
{
  "routes": [
    {
      "routeId": "Route1",
      "senderCompId": "GATEWAY",
      "targetCompId": "BANZAI",
      "destinations": [
        "netty://localhost:9999",
        "jms://localhost:61616/queue.fix.outbound",
        "kafka://localhost:9092?topic=fix-outbound-topic"
      ]
    }
  ]
}
```

### Message Format

Messages are expected in JSON format matching `FixMessageEnvelope`:

```java
public class FixMessageEnvelope {
    private Metadata metadata;
    private String rawMessage;  // Raw FIX message string
    private Instant timestamp;
    
    public static class Metadata {
        private String sessionId;
        private String senderCompId;
        private String targetCompId;
        private String msgType;
        private int seqNum;
        private boolean possDupFlag;
        private boolean possResend;
        private Map<String, String> additionalFields;
    }
}
```

## API Endpoints

- `GET /api/routing/routes` - List all configured routes
- `GET /api/routing/match?senderCompId=X&targetCompId=Y` - Find matching routes
- `GET /api/routing/health` - Health check
- `GET /api/routing/config` - Configuration overview

## Running the Application

### Prerequisites

- Java 21
- Maven 3.6+
- Kafka (for message consumption)
- Optional: ActiveMQ (for JMS destinations)
- Optional: Netty server (for Netty destinations)

### Build and Run

```bash
# Build the application
mvn clean package

# Run the application
java -jar target/fix-message-router-1.0.0.jar

# Or run with Maven
mvn spring-boot:run
```

### Configuration

Edit `src/main/resources/application.yml` for environment-specific settings:

```yaml
kafka:
  brokers: localhost:9092
  groupId: fix-router-group
  
activemq:
  broker-url: tcp://localhost:61616
```

## Project Structure

```
src/main/java/com/fix/gateway/
├── FixMessageRouterApplication.java     # Main application class
├── config/
│   ├── JacksonConfig.java              # Jackson configuration
│   └── RoutingConfigurationLoader.java # Loads routing config
├── controller/
│   └── RoutingController.java          # REST API endpoints
├── model/
│   ├── FixMessageEnvelope.java         # Message envelope model
│   ├── FixSessionConfig.java           # Session configuration model
│   └── RoutingConfig.java              # Routing configuration model
├── processor/
│   └── FixMessageProcessor.java        # Message processing logic
├── route/
│   └── FixMessageRouter.java           # Camel route definitions
└── service/
    └── RoutingService.java             # Routing business logic
```

## Testing

Run the tests with:

```bash
mvn test
```

The application includes:
- Unit test for application context loading
- Test configuration in `src/test/resources/application-test.yml`

## Sample Message

```json
{
  "metadata": {
    "sessionId": "TEST_ACCEPTOR_1",
    "senderCompId": "GATEWAY",
    "targetCompId": "BANZAI",
    "msgType": null,
    "seqNum": 0,
    "possDupFlag": false,
    "possResend": false,
    "additionalFields": {}
  },
  "rawMessage": "8=FIX.4.4\u00019=162\u000135=D\u000134=9\u000143=Y\u000149=BANZAI\u000152=20251223-03:05:13.661\u000156=GATEWAY\u0001122=20251223-02:22:46.568\u00011=1\u000111=1\u000122=8\u000138=12\u000140=2\u000144=122\u000154=1\u000155=AAPL\u000159=0\u000160=20251223-02:22:46.390\u000110=150\u0001",
  "timestamp": "2025-12-23T03:05:13.967581057Z"
}
```

## Extending

### Adding New Destination Types

1. Add new Camel component dependency to `pom.xml`
2. Update `DestinationType` enum in `FixSessionConfig.java`
3. Add corresponding URI patterns in routing configuration

### Custom Processing

Modify `FixMessageProcessor.java` to add custom logic:
- Message validation
- Transformation
- Enrichment
- Filtering

## License

This project is for demonstration purposes.
package com.fix.gateway.util;

import com.fix.gateway.model.FixMessageEnvelope;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for evaluating MVEL expressions for partition determination.
 * Provides methods to evaluate expressions against FIX message envelopes.
 * Uses caching for compiled expressions to improve performance.
 */
public class MvelExpressionEvaluator {
    
    private static final Logger log = LoggerFactory.getLogger(MvelExpressionEvaluator.class);
    
    /**
     * Cache for compiled MVEL expressions to avoid recompilation.
     * Key: expression string, Value: compiled Serializable expression
     */
    private static final Map<String, Serializable> expressionCache = new ConcurrentHashMap<>();
    
    /**
     * Evaluates an MVEL expression against a FIX message envelope.
     * The expression can access envelope fields and parsed FIX tags.
     * Uses caching for compiled expressions.
     *
     * @param expression MVEL expression to evaluate
     * @param envelope FIX message envelope containing the message and parsed tags
     * @param parsedTags Map of parsed FIX tags (tag number -> value) - can be null if envelope has parsedTags
     * @return Result of the expression evaluation
     */
    public static Object evaluateExpression(String expression, FixMessageEnvelope envelope, Map<Integer, String> parsedTags) {
        if (expression == null || expression.trim().isEmpty()) {
            log.warn("Empty expression provided for evaluation");
            return null;
        }
        
        try {
            // Create evaluation context with envelope fields
            Map<String, Object> context = createEvaluationContext(envelope, parsedTags);
            
            // Get or compile the expression
            Serializable compiledExpression = getOrCompileExpression(expression);
            
            // Evaluate the expression
            Object result = MVEL.executeExpression(compiledExpression, context);
            
            log.debug("Evaluated expression '{}' with result: {}", expression, result);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to evaluate MVEL expression '{}': {}", expression, e.getMessage(), e);
            throw new RuntimeException("Failed to evaluate partition expression: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates evaluation context with envelope fields and parsed tags.
     * Prefers envelope's parsedTags if available, otherwise uses provided parsedTags.
     */
    private static Map<String, Object> createEvaluationContext(FixMessageEnvelope envelope, Map<Integer, String> parsedTags) {
        Map<String, Object> context = new HashMap<>();
        
        // Add envelope fields
        context.put("envelope", envelope);
        context.put("sessionId", envelope.getSessionId());
        context.put("senderCompId", envelope.getSenderCompId());
        context.put("targetCompId", envelope.getTargetCompId());
        context.put("msgType", envelope.getMsgType());
        context.put("clOrdID", envelope.getClOrdID());
        context.put("symbol", envelope.getSymbol());
        context.put("side", envelope.getSide());
        context.put("orderQty", envelope.getOrderQty());
        context.put("price", envelope.getPrice());
        context.put("rawMessage", envelope.getRawMessage());
        context.put("createdTimestamp", envelope.getCreatedTimestamp());
        
        // Use envelope's parsedTags if available, otherwise use provided parsedTags
        Map<Integer, String> tagsToUse = envelope.getParsedTags() != null && !envelope.getParsedTags().isEmpty()
            ? envelope.getParsedTags()
            : parsedTags;
        
        // Add parsed FIX tags as variables
        if (tagsToUse != null) {
            // Add common FIX tags as direct variables for convenience
            for (Map.Entry<Integer, String> entry : tagsToUse.entrySet()) {
                String tagName = getTagName(entry.getKey());
                context.put(tagName, entry.getValue());
                context.put("Tag" + entry.getKey(), entry.getValue());
            }
            context.put("parsedTags", tagsToUse);
        }
        
        return context;
    }
    
    /**
     * Gets compiled expression from cache or compiles it if not cached.
     */
    private static Serializable getOrCompileExpression(String expression) {
        return expressionCache.computeIfAbsent(expression, expr -> {
            try {
                ParserContext parserContext = new ParserContext();
                return MVEL.compileExpression(expr, parserContext);
            } catch (Exception e) {
                log.error("Failed to compile MVEL expression '{}': {}", expr, e.getMessage(), e);
                throw new RuntimeException("Failed to compile partition expression: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Pre-compiles an expression and adds it to the cache.
     * This can be called during startup to warm up the cache.
     *
     * @param expression MVEL expression to pre-compile
     * @return true if compilation succeeded, false otherwise
     */
    public static boolean preCompileExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        
        try {
            // This will compile and cache the expression if not already cached
            getOrCompileExpression(expression);
            return true;
        } catch (Exception e) {
            log.warn("Failed to pre-compile MVEL expression '{}': {}", expression, e.getMessage());
            return false;
        }
    }
    
    /**
     * Evaluates an MVEL expression to determine partition key or number.
     * For KEY strategy: returns the key value (will be converted to string)
     * For EXPR strategy: returns the partition number (must be integer)
     * 
     * @param expression MVEL expression
     * @param envelope FIX message envelope
     * @param parsedTags Map of parsed FIX tags
     * @return Partition key (String) or partition number (Integer)
     */
    public static Object evaluatePartitionExpression(String expression, FixMessageEnvelope envelope, 
                                                    Map<Integer, String> parsedTags) {
        Object result = evaluateExpression(expression, envelope, parsedTags);
        
        if (result == null) {
            log.warn("Partition expression evaluated to null, using default routing");
        }
        
        return result;
    }
    
    /**
     * Gets a meaningful name for common FIX tags.
     */
    private static String getTagName(int tag) {
        switch (tag) {
            case 8: return "BeginString";
            case 9: return "BodyLength";
            case 35: return "MsgType";
            case 49: return "SenderCompID";
            case 56: return "TargetCompID";
            case 34: return "MsgSeqNum";
            case 52: return "SendingTime";
            case 10: return "CheckSum";
            case 11: return "ClOrdID";
            case 55: return "Symbol";
            case 54: return "Side";
            case 38: return "OrderQty";
            case 40: return "OrdType";
            case 44: return "Price";
            case 59: return "TimeInForce";
            default: return "Tag" + tag;
        }
    }
    
    /**
     * Parses FIX message raw string into a map of tag-value pairs.
     * This is a helper method to extract FIX tags from raw messages.
     */
    public static Map<Integer, String> parseFixTags(String rawMessage) {
        Map<Integer, String> tags = new HashMap<>();
        
        if (rawMessage == null || rawMessage.isEmpty()) {
            return tags;
        }
        
        try {
            // Split by SOH character
            String[] fields = rawMessage.split(String.valueOf(FixMessageUtils.SOH));
            
            for (String field : fields) {
                if (field.isEmpty()) continue;
                
                int equalsIndex = field.indexOf('=');
                if (equalsIndex > 0) {
                    try {
                        int tag = Integer.parseInt(field.substring(0, equalsIndex));
                        String value = field.substring(equalsIndex + 1);
                        tags.put(tag, value);
                    } catch (NumberFormatException e) {
                        // Skip invalid tag numbers
                        log.debug("Invalid tag number in field: {}", field);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse FIX tags from message: {}", e.getMessage());
        }
        
        return tags;
    }
}
package com.fix.gateway.util;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for FIX message manipulation.
 * Provides methods for parsing FIX messages, replacing tags, and recomputing checksums.
 */
public class FixMessageUtils {
    
    /**
     * SOH (Start of Header) character used as field delimiter in FIX protocol.
     */
    public static final char SOH = '\u0001';
    
    /**
     * Validates that a FIX message ends with SOH character.
     * If not, adds the trailing SOH to ensure proper FIX format.
     *
     * @param fixMessage The FIX message to validate
     * @return The validated FIX message with trailing SOH if needed
     */
    public static String ensureTrailingSOH(String fixMessage) {
        if (fixMessage == null || fixMessage.isEmpty()) {
            return fixMessage;
        }
        
        // Check if message ends with SOH
        if (fixMessage.charAt(fixMessage.length() - 1) != SOH) {
            // Add trailing SOH
            return fixMessage + SOH;
        }
        
        return fixMessage;
    }
    
    /**
     * Counts the number of SOH characters in a FIX message.
     *
     * @param fixMessage The FIX message to analyze
     * @return Number of SOH characters
     */
    public static int countSOH(String fixMessage) {
        if (fixMessage == null || fixMessage.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        for (int i = 0; i < fixMessage.length(); i++) {
            if (fixMessage.charAt(i) == SOH) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Validates basic FIX message structure.
     *
     * @param fixMessage The FIX message to validate
     * @return true if the message appears to be a valid FIX message
     */
    public static boolean isValidFixMessage(String fixMessage) {
        if (fixMessage == null || fixMessage.isEmpty()) {
            return false;
        }
        
        // Basic validation: should start with "8=FIX" and contain SOH characters
        return fixMessage.startsWith("8=FIX") && countSOH(fixMessage) > 0;
    }
    
    /**
     * Converts Unicode escape sequences in a string to actual characters.
     * For example, converts "\u0001" to the SOH character.
     *
     * @param str The string containing escape sequences
     * @return String with escape sequences converted to actual characters
     */
    public static String unescapeUnicode(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < str.length()) {
            char c = str.charAt(i);
            if (c == '\\' && i + 5 < str.length() && str.charAt(i + 1) == 'u') {
                // Try to parse Unicode escape: \\uXXXX (note: double backslash in comment)
                try {
                    String hex = str.substring(i + 2, i + 6);
                    int codePoint = Integer.parseInt(hex, 16);
                    sb.append((char) codePoint);
                    i += 6;
                } catch (NumberFormatException e) {
                    // Not a valid Unicode escape, keep as-is
                    sb.append(c);
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
    
    /**
     * Processes a raw message that might contain escape sequences.
     * If the message contains Unicode escape sequences for SOH, converts them to actual SOH characters.
     * Also ensures the message ends with SOH.
     *
     * @param rawMessage The raw message to process
     * @return Processed message with actual SOH characters
     */
    public static String processRawMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return rawMessage;
        }
        
        // First, unescape any Unicode sequences
        String unescaped = unescapeUnicode(rawMessage);
        
        // Ensure trailing SOH
        return ensureTrailingSOH(unescaped);
    }
    
    public static String transformGatewayToExec(String msg) {
        // Apply transformations: GTWY -> EXEC for sender, BANZ -> GTWY for target
        String newSender = "EXEC";
        String newTarget = "GTWY";
        

       return msg.replace("GTWY","EXEC").replace("BANZ","GTWY");
    }
}
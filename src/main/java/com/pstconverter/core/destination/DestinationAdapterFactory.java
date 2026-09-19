package com.pstconverter.core.destination;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory class for dynamically loading and instantiating DestinationAdapter implementations.
 * Decouples the core interface from concrete handler classes.
 */
public class DestinationAdapterFactory {
    private static final Map<String, String> REGISTRY = new HashMap<>();

    static {
        String base = "com.pstconverter.core.destination.";
        REGISTRY.put("GMAIL", base + "GmailDestinationHandler");
        REGISTRY.put("GOOGLE", base + "GmailDestinationHandler");
        REGISTRY.put("OFFICE 365", base + "Office365DestinationHandler");
        REGISTRY.put("OFFICE_365", base + "Office365DestinationHandler");
        REGISTRY.put("MICROSOFT", base + "Office365DestinationHandler");
        REGISTRY.put("O365", base + "Office365DestinationHandler");
        REGISTRY.put("IMAP", base + "ImapDestinationHandler");
        REGISTRY.put("IMAP SERVER", base + "ImapDestinationHandler");
        REGISTRY.put("YAHOO", base + "ImapDestinationHandler");
        REGISTRY.put("YAHOO MAIL", base + "ImapDestinationHandler");
    }

    /**
     * Resolves the appropriate destination adapter dynamically at runtime.
     *
     * @param formatName The format name from configuration.
     * @return DestinationAdapter instance, or standard IMAP adapter as fallback.
     */
    public static DestinationAdapter getAdapter(String formatName) {
        if (formatName == null) {
            return loadAdapter("IMAP");
        }
        String clean = formatName.trim().toUpperCase();
        for (Map.Entry<String, String> entry : REGISTRY.entrySet()) {
            if (clean.contains(entry.getKey())) {
                DestinationAdapter adapter = loadAdapter(entry.getKey());
                if (adapter != null) {
                    return adapter;
                }
            }
        }
        return loadAdapter("IMAP");
    }

    private static DestinationAdapter loadAdapter(String key) {
        String className = REGISTRY.get(key);
        if (className != null) {
            try {
                Class<?> clazz = Class.forName(className);
                return (DestinationAdapter) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println("Failed to load destination adapter dynamically: " + className + " - " + e.getMessage());
            }
        }
        return null;
    }
}

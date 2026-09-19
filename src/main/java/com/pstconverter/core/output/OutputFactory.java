package com.pstconverter.core.output;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory class for creating and retrieving OutputHandler instances based on OutputCategory.
 * Decouples the core conversion view from the specific file writing implementations.
 */
public class OutputFactory {
    private static final Map<OutputCategory, String> HANDLER_CLASSES = new HashMap<>();
    private static final Map<OutputCategory, OutputHandler> INSTANCES = new HashMap<>();

    static {
        try (java.io.InputStream in = OutputFactory.class.getResourceAsStream("/outputs.properties")) {
            if (in != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(in);
                for (String key : props.stringPropertyNames()) {
                    try {
                        OutputCategory cat = OutputCategory.valueOf(key.toUpperCase());
                        HANDLER_CLASSES.put(cat, props.getProperty(key));
                    } catch (IllegalArgumentException e) {
                        System.err.println("Unknown output category in properties: " + key);
                    }
                }
            } else {
                System.err.println("[WARN] outputs.properties not found on classpath");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the appropriate handler for the specified format category.
     *
     * @param category The OutputCategory enum.
     * @return OutputHandler instance, or null if instantiation fails.
     */
    public static OutputHandler getHandler(OutputCategory category) {
        if (category == null) return null;
        if (INSTANCES.containsKey(category)) {
            return INSTANCES.get(category);
        }
        String className = HANDLER_CLASSES.get(category);
        if (className != null) {
            try {
                Class<?> clazz = Class.forName(className);
                OutputHandler handler;
                if (category.getGroup().equals("CLOUD")) {
                    // CloudOutputHandler constructor takes a String argument for the format name.
                    try {
                        handler = (OutputHandler) clazz.getConstructor(String.class).newInstance(category.getDisplayName());
                    } catch (NoSuchMethodException e) {
                        handler = (OutputHandler) clazz.getDeclaredConstructor().newInstance();
                    }
                } else {
                    handler = (OutputHandler) clazz.getDeclaredConstructor().newInstance();
                }
                INSTANCES.put(category, handler);
                return handler;
            } catch (Exception e) {
                System.err.println("Failed to load exporter class dynamically: " + className + " - " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Returns the appropriate handler for the format string.
     *
     * @param formatName The format name (e.g. "PDF", "Office 365").
     * @return OutputHandler instance, or null if unsupported.
     */
    public static OutputHandler getHandler(String formatName) {
        return getHandler(OutputCategory.fromString(formatName));
    }

    /**
     * Returns a list of supported OutputCategory based on registered handlers.
     *
     * @return List of supported OutputCategory.
     */
    public static java.util.List<OutputCategory> getSupportedCategories() {
        return new java.util.ArrayList<>(HANDLER_CLASSES.keySet());
    }
}

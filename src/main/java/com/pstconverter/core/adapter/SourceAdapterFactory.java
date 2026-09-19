package com.pstconverter.core.adapter;

import java.io.File;
import java.util.*;

/**
 * Factory class for creating and retrieving SourceAdapter instances.
 * Registers source adapters dynamically to minimize compile-time coupling.
 */
public class SourceAdapterFactory {
    private static final Map<String, String> ADAPTER_REGISTRY = new HashMap<>();

    static {
        try (java.io.InputStream in = SourceAdapterFactory.class.getResourceAsStream("/adapters.properties")) {
            if (in != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(in);
                for (String key : props.stringPropertyNames()) {
                    String cleanKey = key.replace("\uFEFF", "").trim().toLowerCase();
                    ADAPTER_REGISTRY.put(cleanKey, props.getProperty(key).trim());
                }
            } else {
                System.err.println("[WARN] adapters.properties not found on classpath");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static SourceAdapter getAdapterForFile(File file) {
        if (file == null) return getAdapterByType("imap");
        String ext = getFileExtension(file).toLowerCase();
        SourceAdapter adapter = getAdapterByType(ext);
        if (adapter == null) {
            return getAdapterByType("imap");
        }
        return adapter;
    }

    public static SourceAdapter getAdapterByType(String type) {
        if (type == null) return getAdapterByType("imap");
        String cleanType = type.replace("\uFEFF", "").trim().toLowerCase();
        String className = ADAPTER_REGISTRY.get(cleanType);
        if (className == null) {
            className = "com.pstconverter.imap.ImapSourceAdapter";
        }
        try {
            Class<?> clazz = Class.forName(className);
            return (SourceAdapter) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to instantiate adapter for type: " + type + " (" + className + ")");
            e.printStackTrace();
            return null;
        }
    }

    public static boolean isSupported(File file) {
        return true;
    }

    public static Set<String> getSupportedExtensions() {
        return Collections.unmodifiableSet(ADAPTER_REGISTRY.keySet());
    }

    public static List<SourceAdapter> getRegisteredAdapters() {
        List<SourceAdapter> list = new ArrayList<>();
        for (String type : ADAPTER_REGISTRY.keySet()) {
            SourceAdapter adapter = getAdapterByType(type);
            if (adapter != null) {
                list.add(adapter);
            }
        }
        return list;
    }

    public static List<File> detectAllLocalMailboxes() {
        List<File> allDetected = new ArrayList<>();
        for (SourceAdapter adapter : getRegisteredAdapters()) {
            try {
                List<File> localFiles = adapter.detectLocalMailboxes();
                if (localFiles != null) {
                    allDetected.addAll(localFiles);
                }
            } catch (Exception e) {
                System.err.println("[WARN] Auto-detection failed for adapter: " + adapter.getDisplayName());
            }
        }
        return allDetected;
    }

    private static String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1);
        }
        return "";
    }
}
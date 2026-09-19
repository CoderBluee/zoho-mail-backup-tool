package com.pstconverter.core.destination;

/**
 * Data carrier record representing the configuration for a cloud email destination.
 * Complies with Java 21 immutable record standards.
 */
public record EmailDestinationConfig(
    String destinationType,      // e.g., "Gmail", "Office 365", "IMAP Server", "Yahoo Mail"
    String authMode,             // "OAuth 2.0 (Modern Auth)" or "App Password (IMAP)"
    String host,
    int port,
    boolean ssl,
    String username,
    String password,             // App Password or basic password
    String clientId,
    String clientSecret,
    String tenantId,
    String redirectUri,
    String targetFolder,
    boolean bulkEnabled,
    String bulkCsvPath,
    int throttlingDelayMs,
    int maxMessagesPerMin,
    int maxRetries,
    int initialBackoffMs,
    boolean adminImpersonationEnabled,
    String serviceAccountJsonPath,
    String impersonatedUserEmail,
    java.util.Map<String, String> userMappingMap,
    boolean nativeItemRoutingEnabled,
    boolean preserveFolderHierarchy
) {
    /**
     * Overloaded constructor for backward compatibility.
     */
    public EmailDestinationConfig(
        String destinationType, String authMode, String host, int port, boolean ssl,
        String username, String password, String clientId, String clientSecret,
        String tenantId, String redirectUri, String targetFolder, boolean bulkEnabled,
        String bulkCsvPath, int throttlingDelayMs, int maxMessagesPerMin, int maxRetries,
        int initialBackoffMs, boolean adminImpersonationEnabled, String serviceAccountJsonPath,
        String impersonatedUserEmail, java.util.Map<String, String> userMappingMap,
        boolean nativeItemRoutingEnabled
    ) {
        this(destinationType, authMode, host, port, ssl, username, password, clientId, clientSecret,
             tenantId, redirectUri, targetFolder, bulkEnabled, bulkCsvPath, throttlingDelayMs,
             maxMessagesPerMin, maxRetries, initialBackoffMs, adminImpersonationEnabled,
             serviceAccountJsonPath, impersonatedUserEmail, userMappingMap, nativeItemRoutingEnabled, false);
    }

    public EmailDestinationConfig(
        String destinationType, String authMode, String host, int port, boolean ssl,
        String username, String password, String clientId, String clientSecret,
        String tenantId, String redirectUri, String targetFolder, boolean bulkEnabled,
        String bulkCsvPath, int throttlingDelayMs, int maxMessagesPerMin, int maxRetries,
        int initialBackoffMs, boolean adminImpersonationEnabled, String serviceAccountJsonPath,
        String impersonatedUserEmail, java.util.Map<String, String> userMappingMap
    ) {
        this(destinationType, authMode, host, port, ssl, username, password, clientId, clientSecret,
             tenantId, redirectUri, targetFolder, bulkEnabled, bulkCsvPath, throttlingDelayMs,
             maxMessagesPerMin, maxRetries, initialBackoffMs, adminImpersonationEnabled,
             serviceAccountJsonPath, impersonatedUserEmail, userMappingMap, false, false);
    }
    /**
     * Checks if this configuration targets a cloud destination.
     */
    public boolean isCloud() {
        return destinationType != null && (
            destinationType.equalsIgnoreCase("Gmail") || 
            destinationType.equalsIgnoreCase("Office 365") || 
            destinationType.equalsIgnoreCase("IMAP Server") || 
            destinationType.equalsIgnoreCase("Yahoo Mail")
        );
    }

    /**
     * Checks if Admin Impersonation (Domain-Wide Delegation / App Permissions) is active.
     */
    public boolean isAdminImpersonation() {
        return adminImpersonationEnabled || (authMode != null && (
            authMode.contains("Service Account") || 
            authMode.contains("Admin Impersonation") || 
            authMode.contains("App Permissions")
        ));
    }
}

package com.pstconverter.core.destination;

import com.pstconverter.core.auth.TokenManager;
import com.pstconverter.core.auth.GoogleOAuthService;
import com.pstconverter.model.MailMessage;
import com.chilkatsoft.CkEmail;
import com.chilkatsoft.CkByteData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;

/**
 * Concrete destination adapter for Gmail and Google Workspace accounts.
 * Supports legacy IMAP (App Password) and Modern Auth (Gmail GAPI/import endpoints).
 */
public class GmailDestinationHandler implements DestinationAdapter {

    private final ImapDestinationHandler imapHandler = new ImapDestinationHandler();
    private static final java.util.Set<String> GMAIL_SYSTEM_LABELS = java.util.Set.of("INBOX", "SENT", "TRASH", "DRAFT", "SPAM");

    private String getSystemLabelId(String folderName) {
        if (folderName == null) return null;
        String lower = folderName.trim().toLowerCase(java.util.Locale.ROOT);
        switch (lower) {
            case "inbox":
                return "INBOX";
            case "sent items":
            case "sent":
                return "SENT";
            case "deleted items":
            case "deleted":
            case "trash":
                return "TRASH";
            case "drafts":
                return "DRAFT";
            case "junk email":
            case "spam":
            case "junk":
                return "SPAM";
            default:
                return null;
        }
    }

    private String mapToGmailLabelName(String folderPath) {
        String cleanPath = folderPath.replace('\\', '/').trim();
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        if (cleanPath.endsWith("/")) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }

        String[] parts = cleanPath.split("/");
        if (parts.length == 0) return "";

        String firstSegment = parts[0].trim();
        String systemId = getSystemLabelId(firstSegment);
        if (systemId != null) {
            parts[0] = systemId;
        }

        return String.join("/", parts);
    }
    private String currentAccessToken;
    private String userEmail;
    private final java.util.Map<String, String> labelCache = new java.util.concurrent.ConcurrentHashMap<>();

    // Cached connection elements to reuse sockets and prevent network timeouts
    private static com.google.api.client.http.HttpTransport cachedTransport;
    private static final com.google.api.client.json.JsonFactory jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance();
    private Gmail cachedService;
    private String sessionSuffix;

    private static synchronized com.google.api.client.http.HttpTransport getSharedTransport() throws Exception {
        if (cachedTransport == null) {
            cachedTransport = com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport();
        }
        return cachedTransport;
    }

    @Override
    public void connect(EmailDestinationConfig config) throws Exception {
        if ("App Password (IMAP)".equalsIgnoreCase(config.authMode())) {
            imapHandler.connect(config);
            return;
        }

        labelCache.clear();
        userEmail = config.username();
        if (config.isAdminImpersonation() || (config.authMode() != null && config.authMode().contains("Service Account"))) {
            String keyConfig = config.serviceAccountJsonPath();
            if (keyConfig == null || keyConfig.trim().isEmpty()) {
                throw new IOException("GSuite Service Account key configuration (JSON or P12) is required for Service Account authentication.");
            }
            String targetUser = (config.impersonatedUserEmail() != null && !config.impersonatedUserEmail().isEmpty())
                ? config.impersonatedUserEmail()
                : (userEmail != null ? userEmail : "me");
            
            com.google.api.client.googleapis.auth.oauth2.GoogleCredential credential =
                GoogleOAuthService.createServiceAccountCredential(keyConfig, targetUser, GoogleOAuthService.GSUITE_ALL_SCOPES);
            credential.refreshToken();
            currentAccessToken = credential.getAccessToken();
        } else {
            verifyOrRefreshTokens(config);
        }
        sessionSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        cachedService = null; // Reset cached service for a fresh session connection
        System.out.println("Gmail API adapter successfully connected for user: " + userEmail);
    }

    @Override
    public void testConnection(EmailDestinationConfig config) throws Exception {
        if ("App Password (IMAP)".equalsIgnoreCase(config.authMode())) {
            imapHandler.testConnection(config);
            return;
        }
        connect(config);
        disconnect();
    }

    @Override
    public void uploadMessage(MailMessage message, String targetFolder, EmailDestinationConfig config) throws Exception {
        uploadMessage(message, targetFolder, config, null);
    }

    @Override
    public void uploadMessage(MailMessage message, String targetFolder, EmailDestinationConfig config, String overrideTargetUserEmail) throws Exception {
        if ("App Password (IMAP)".equalsIgnoreCase(config.authMode())) {
            imapHandler.uploadMessage(message, targetFolder, config, overrideTargetUserEmail);
            return;
        }

        String targetUser = (overrideTargetUserEmail != null && !overrideTargetUserEmail.isEmpty()) 
            ? overrideTargetUserEmail 
            : (config.impersonatedUserEmail() != null && !config.impersonatedUserEmail().isEmpty() ? config.impersonatedUserEmail() : (userEmail != null ? userEmail : "me"));

        if (config.isAdminImpersonation() || (config.authMode() != null && config.authMode().contains("Service Account"))) {
            String keyConfig = config.serviceAccountJsonPath();
            if (keyConfig != null && !keyConfig.trim().isEmpty()) {
                com.google.api.client.googleapis.auth.oauth2.GoogleCredential credential =
                    GoogleOAuthService.createServiceAccountCredential(keyConfig, targetUser, GoogleOAuthService.GSUITE_ALL_SCOPES);
                credential.refreshToken();
                currentAccessToken = credential.getAccessToken();
                cachedService = null;
            }
        } else {
            verifyOrRefreshTokens(config);
        }

        // Native App Sync Routing Check
        if (config.nativeItemRoutingEnabled()) {
            if (message.isContact()) {
                if (tryUploadGoogleContact(message, targetUser, currentAccessToken)) {
                    System.out.println("[INFO] Natively routed Contact to Google People API for: " + targetUser);
                    return;
                }
            } else if (message.isCalendar()) {
                if (tryUploadGoogleCalendarEvent(message, targetUser, currentAccessToken)) {
                    System.out.println("[INFO] Natively routed Calendar Event to Google Calendar API for: " + targetUser);
                    return;
                }
            } else if (message.isTask()) {
                if (tryUploadGoogleTask(message, targetUser, currentAccessToken)) {
                    System.out.println("[INFO] Natively routed Task to Google Tasks API for: " + targetUser);
                    return;
                }
            }
        }

        String fullTargetFolder = targetFolder;
        if (config.preserveFolderHierarchy()) {
            String sys = DestinationAdapter.mapSystemFolder(targetFolder);
            if (sys != null) {
                fullTargetFolder = sys;
            } else {
                fullTargetFolder = targetFolder;
            }
        } else {
            String rootFolder = config.targetFolder() != null ? config.targetFolder().trim() : "";
            if (!rootFolder.isEmpty()) {
                fullTargetFolder = rootFolder + "/" + targetFolder;
            }
        }

        // Map target folder to label name
        String labelName = mapToGmailLabelName(fullTargetFolder);
        
        // Fetch or create matching Label ID in Gmail for target user
        String labelId = getOrCreateLabel(targetUser, labelName);
        if (labelId == null) {
            throw new IOException("Failed to resolve or create Gmail label: " + labelName + " for " + targetUser);
        }

        // Enforce network upload throttling
        DestinationAdapter.applyThrottle(DestinationAdapter.estimateMessageSize(message));

        System.out.println("[INFO] Gmail API: Uploading message for " + targetUser + " to label: \"" + labelName + "\" | Subject: " + message.getSubject());

        try {
            byte[] mimeBytes;
            CkEmail ckEmail = DestinationAdapter.createCkEmail(message, targetUser);
            com.chilkatsoft.CkByteData byteData = new com.chilkatsoft.CkByteData();
            try {
                // Override Message-ID with a stable session suffix to bypass Gmail's mailbox-wide deduplication
                // across different sessions, while still allowing Gmail's deduplication to work for retries
                // of the exact same email within this migration session to prevent duplicate uploads.
                String suffix = (sessionSuffix != null) ? sessionSuffix : java.util.UUID.randomUUID().toString().substring(0, 8);
                String origMsgId = ckEmail.getHeaderField("Message-ID");
                
                if (origMsgId != null && !origMsgId.isEmpty()) {
                    String newMsgId;
                    if (origMsgId.endsWith(">")) {
                        newMsgId = origMsgId.substring(0, origMsgId.length() - 1) + "." + suffix + ">";
                    } else {
                        newMsgId = origMsgId + "." + suffix;
                    }
                    ckEmail.RemoveHeaderField("Message-ID");
                    ckEmail.AddHeaderField("Message-ID", newMsgId);
                } else {
                    // Generate a deterministic message ID for retries in case it's completely missing
                    String deterministicHash = Integer.toHexString((message.getSubject() + message.getDate() + message.getFrom()).hashCode());
                    String generatedId = "<" + deterministicHash + "." + suffix + "@pstconverter.generated>";
                    ckEmail.RemoveHeaderField("Message-ID");
                    ckEmail.AddHeaderField("Message-ID", generatedId);
                }

                ckEmail.GetMimeBinary(byteData);
                mimeBytes = byteData.toByteArray();
            } finally {
                byteData.delete();
                ckEmail.delete();
            }

            // Execute request with retries and exponential backoff on HTTP 429/401 using Google API Client Library
            int retryCount = 0;
            int unauthorizedRetryCount = 0;
            int maxRetries = config.maxRetries() > 0 ? config.maxRetries() : 3;
            int delay = config.initialBackoffMs() > 0 ? config.initialBackoffMs() : 1000;

            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Upload task interrupted by user request");
                }
                long startTime = System.currentTimeMillis();
                try {
                    Gmail service = getGmailService();
                    
                    Message messagePayload = new Message();
                    messagePayload.setRaw(com.google.api.client.util.Base64.encodeBase64URLSafeString(mimeBytes));
                    
                    java.util.List<String> labelIds = new java.util.ArrayList<>();
                    labelIds.add(labelId);
                    if (!"Read".equalsIgnoreCase(message.getStatus())) {
                        labelIds.add("UNREAD");
                    }
                    messagePayload.setLabelIds(labelIds);

                    Gmail.Users.Messages.Insert insertRequest = service.users().messages().insert(targetUser, messagePayload);
                    insertRequest.setInternalDateSource("dateHeader");

                    Message response = insertRequest.execute();
                    String gmailMsgId = response.getId();
                    
                    long duration = System.currentTimeMillis() - startTime;
                    com.pstconverter.util.DiagnosticLogger.logCloudUploadAttempt(
                        message.getUniqueIdentifier(),
                        message.getSubject(),
                        "Gmail",
                        retryCount + 1,
                        maxRetries,
                        true,
                        duration,
                        null
                    );

                    // Extract actual labelIds from response to verify our label was applied
                    boolean labelVerified = false;
                    java.util.List<String> respLabelIds = response.getLabelIds();
                    if (respLabelIds != null) {
                        labelVerified = respLabelIds.contains(labelId);
                    }
                    
                    if (gmailMsgId != null && !gmailMsgId.isEmpty()) {
                        if (labelVerified) {
                            System.out.println("[INFO] Gmail API: Message inserted successfully. ID: " + gmailMsgId + " | Label applied: " + labelName + " ✓");
                        } else {
                            System.out.println("[WARN] Gmail API: Message inserted (ID: " + gmailMsgId + ") but requested label may not have been applied. Label: " + labelName + " | Response: " + response.toPrettyString());
                        }
                    } else {
                        System.out.println("[WARN] Gmail API: Message inserted but no message ID found in response. Label: " + labelName + " | Response: " + response.toPrettyString());
                    }
                    break; // Upload Success
                } catch (GoogleJsonResponseException e) {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = e.getStatusCode();
                    String errorMsg = e.getDetails() != null ? e.getDetails().getMessage() : e.getMessage();
                    com.pstconverter.util.DiagnosticLogger.logCloudUploadAttempt(
                        message.getUniqueIdentifier(),
                        message.getSubject(),
                        "Gmail",
                        retryCount + 1,
                        maxRetries,
                        false,
                        duration,
                        "API Error " + statusCode + ": " + errorMsg
                    );

                    if (statusCode == 401) {
                        if (unauthorizedRetryCount >= 2) {
                            throw new IOException("Failed to insert message to Gmail due to persistent 401 Unauthorized after refreshing tokens. Response: " + errorMsg, e);
                        }
                        System.out.println("[WARNING] Gmail API 401 Unauthorized. Refreshing token and retrying...");
                        forceRefreshToken(config);
                        unauthorizedRetryCount++;
                    } else if (statusCode == 429) {
                        // Throttling / Rate limiting
                        if (retryCount >= maxRetries) {
                            throw new IOException("Failed to insert message to Gmail due to rate limit constraints after " + retryCount + " retries. Response: " + errorMsg, e);
                        }
                        System.out.println("[WARNING] Gmail API Rate Limit hit (HTTP 429). Backing off for " + delay + "ms...");
                        Thread.sleep(delay);
                        retryCount++;
                        delay *= 2; // Exponential backoff
                    } else {
                        throw new IOException("Failed to insert message to Gmail. HTTP Status: " + statusCode + ", Response: " + errorMsg, e);
                    }
                } catch (IOException e) {
                    long duration = System.currentTimeMillis() - startTime;
                    com.pstconverter.util.DiagnosticLogger.logCloudUploadAttempt(
                        message.getUniqueIdentifier(),
                        message.getSubject(),
                        "Gmail",
                        retryCount + 1,
                        maxRetries,
                        false,
                        duration,
                        e.getMessage()
                    );

                    if (retryCount >= maxRetries) {
                        throw e;
                    }
                    System.out.println("[WARNING] Gmail API network or I/O failure: " + e.getMessage() + ". Retrying upload in " + delay + "ms... (Attempt " + (retryCount + 1) + "/" + maxRetries + ")");
                    Thread.sleep(delay);
                    retryCount++;
                    delay *= 2; // Exponential backoff
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to upload message via Gmail API. Label: " + labelName + " | Error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public boolean isMessageAlreadyOnServer(MailMessage message, String targetFolder, EmailDestinationConfig config) throws Exception {
        if ("App Password (IMAP)".equalsIgnoreCase(config.authMode())) {
            return imapHandler.isMessageAlreadyOnServer(message, targetFolder, config);
        }
        verifyOrRefreshTokens(config);
        String targetUser = (config.impersonatedUserEmail() != null && !config.impersonatedUserEmail().isEmpty()) ? config.impersonatedUserEmail() : (userEmail != null ? userEmail : "me");
        Gmail service = getGmailService();
        String msgId = message.getMessageId();
        String query = null;
        if (msgId != null && !msgId.trim().isEmpty()) {
            String cleanId = msgId.replace("<", "").replace(">", "").trim();
            query = "rfc822msgid:" + cleanId;
        } else if (message.getSubject() != null && !message.getSubject().trim().isEmpty()) {
            query = "subject:\"" + message.getSubject().replace("\"", "") + "\"";
        }
        if (query != null) {
            var listRequest = service.users().messages().list(targetUser).setQ(query);
            var response = listRequest.execute();
            return response.getMessages() != null && !response.getMessages().isEmpty();
        }
        return false;
    }

    @Override
    public void disconnect() throws Exception {
        // Only disconnect the IMAP sub-handler when it was actually used (App Password mode).
        // In Modern Auth mode, currentAccessToken is set; imapHandler was never connected.
        if (currentAccessToken == null) {
            imapHandler.disconnect();
        }
        currentAccessToken = null;
        userEmail = null;
        labelCache.clear();
        cachedService = null;
        sessionSuffix = null;
    }

    private synchronized void forceRefreshToken(EmailDestinationConfig config) throws Exception {
        String refreshToken = TokenManager.getRefreshToken(userEmail);
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new IllegalStateException("Gmail refresh token is missing. Please re-authorize account in Step 4.");
        }
        String clientId = config.clientId();
        String clientSecret = config.clientSecret();
        if (clientId == null || clientId.isEmpty() || clientId.contains("dummy")) {
            var secrets = GoogleOAuthService.loadClientSecrets();
            clientId = secrets.clientId;
            clientSecret = secrets.clientSecret;
        }
        var result = GoogleOAuthService.refreshTokens(refreshToken, clientId, clientSecret);
        TokenManager.saveTokens(userEmail, result.accessToken(), result.refreshToken(), result.expiryTimeMs());
        currentAccessToken = result.accessToken();
    }

    private synchronized void verifyOrRefreshTokens(EmailDestinationConfig config) throws Exception {
        if (TokenManager.isTokenExpired(userEmail) || TokenManager.getAccessToken(userEmail) == null) {
            System.out.println("[INFO] Gmail access token expired or missing. Refreshing token...");
            forceRefreshToken(config);
        } else {
            currentAccessToken = TokenManager.getAccessToken(userEmail);
        }
    }

    private String getOrCreateLabel(String targetUser, String labelName) throws Exception {
        String effectiveUser = (targetUser != null && !targetUser.trim().isEmpty()) ? targetUser.trim() : (userEmail != null ? userEmail : "me");
        String userPrefix = effectiveUser.toLowerCase(java.util.Locale.ROOT) + "::";

        String cleanPath = labelName.replace('\\', '/');
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        if (cleanPath.endsWith("/")) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }

        // Lazy initialize cache for this user if no entries exist yet
        boolean hasUserEntries = false;
        for (String k : labelCache.keySet()) {
            if (k.startsWith(userPrefix)) {
                hasUserEntries = true;
                break;
            }
        }
        if (!hasUserEntries) {
            fetchAndCacheLabels(effectiveUser);
        }

        String[] parts = cleanPath.split("/");
        StringBuilder currentLabel = new StringBuilder();
        String lastLabelId = null;

        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (currentLabel.length() > 0) {
                currentLabel.append("/");
            }
            currentLabel.append(part);

            String targetLabel = currentLabel.toString();
            String cacheKey = userPrefix + targetLabel;
            String labelId = labelCache.get(cacheKey);
            if (labelId == null) {
                if (GMAIL_SYSTEM_LABELS.contains(targetLabel)) {
                    labelId = targetLabel;
                } else {
                    labelId = createLabelOnGmail(effectiveUser, targetLabel);
                }
                labelCache.put(cacheKey, labelId);
            }
            lastLabelId = labelId;
        }
        return lastLabelId;
    }

    private Gmail getGmailService() throws Exception {
        if (cachedService == null) {
            int timeoutSec;
            try {
                timeoutSec = Integer.parseInt(com.pstconverter.util.SettingsManager.getSetting("connection_timeout", "30"));
            } catch (Exception e) {
                timeoutSec = 30;
            }
            final int finalTimeoutMs = timeoutSec * 1000;
            final int readTimeoutMs = Math.max(300000, finalTimeoutMs);
            cachedService = new Gmail.Builder(getSharedTransport(), jsonFactory, request -> {
                request.getHeaders().setAuthorization("Bearer " + currentAccessToken);
                request.setConnectTimeout(finalTimeoutMs);
                request.setReadTimeout(readTimeoutMs);
            })
            .setApplicationName(com.pstconverter.config.BrandConfig.TOOL_NAME)
            .build();
        }
        return cachedService;
    }

    private synchronized void fetchAndCacheLabels(String targetUser) throws Exception {
        String effectiveUser = (targetUser != null && !targetUser.trim().isEmpty()) ? targetUser.trim() : (userEmail != null ? userEmail : "me");
        String userPrefix = effectiveUser.toLowerCase(java.util.Locale.ROOT) + "::";
        try {
            Gmail service = getGmailService();
            ListLabelsResponse response = service.users().labels().list(effectiveUser).execute();
            java.util.List<Label> labels = response.getLabels();
            if (labels != null) {
                for (Label label : labels) {
                    String id = label.getId();
                    String name = label.getName();
                    if (id != null && name != null) {
                        labelCache.put(userPrefix + name, id);
                    }
                }
            }
        } catch (GoogleJsonResponseException e) {
            throw new IOException("Failed to fetch Gmail labels for " + effectiveUser + ". Status: " + e.getStatusCode() + ", Response: " + e.getDetails().getMessage(), e);
        }
    }

    private String createLabelOnGmail(String targetUser, String labelName) throws Exception {
        String effectiveUser = (targetUser != null && !targetUser.trim().isEmpty()) ? targetUser.trim() : (userEmail != null ? userEmail : "me");
        String userPrefix = effectiveUser.toLowerCase(java.util.Locale.ROOT) + "::";
        try {
            Gmail service = getGmailService();
            Label label = new Label()
                    .setName(labelName)
                    .setLabelListVisibility("labelShow")
                    .setMessageListVisibility("show");
            
            Label createdLabel = service.users().labels().create(effectiveUser, label).execute();
            if (createdLabel != null && createdLabel.getId() != null) {
                labelCache.put(userPrefix + labelName, createdLabel.getId());
                return createdLabel.getId();
            }
        } catch (GoogleJsonResponseException createEx) {
            if (createEx.getStatusCode() == 409) {
                System.out.println("[INFO] Gmail API: Label \"" + labelName + "\" already exists for " + effectiveUser + ". Refreshing cache to find ID...");
                fetchAndCacheLabels(effectiveUser);
                String cachedId = labelCache.get(userPrefix + labelName);
                if (cachedId != null) {
                    return cachedId;
                }
            }
            throw new IOException("Failed to create Gmail label: " + labelName + " for " + effectiveUser + ". Status: " + createEx.getStatusCode() + ", Response: " + createEx.getDetails().getMessage(), createEx);
        }
        throw new IOException("Failed to create Gmail label: " + labelName + " for " + effectiveUser + " (unknown error)");
    }

    private boolean tryUploadGoogleContact(MailMessage message, String targetUser, String token) {
        try {
            if (token == null || token.isEmpty()) return false;
            String displayName = message.getSubject() != null && !message.getSubject().isEmpty() ? message.getSubject() : message.getFrom();
            String emailAddr = message.getSenderAddress() != null && !message.getSenderAddress().isEmpty() ? message.getSenderAddress() : message.getFrom();
            if (displayName == null || displayName.isEmpty()) displayName = "Imported Contact";

            String jsonPayload = String.format(
                "{\"names\":[{\"givenName\":\"%s\"}],\"emailAddresses\":[{\"value\":\"%s\"}],\"biographies\":[{\"value\":\"%s\"}]}",
                escapeJson(displayName), escapeJson(emailAddr), escapeJson(message.getBody() != null ? message.getBody() : "")
            );

            java.net.URL url = new java.net.URL("https://people.googleapis.com/v1/people:createContact");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return true;
            } else {
                System.err.println("[WARN] Google People API returned HTTP " + code + " for " + targetUser);
                return false;
            }
        } catch (Exception ex) {
            System.err.println("[WARN] Google Contact Native Sync failed, falling back to Mail label: " + ex.getMessage());
            return false;
        }
    }

    private String formatToIsoDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty() || rawDate.equalsIgnoreCase("Unknown Date")) {
            return java.time.Instant.now().toString();
        }
        try {
            return java.time.Instant.parse(rawDate).toString();
        } catch (Exception ignored1) {}
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US);
            java.util.Date d = sdf.parse(rawDate);
            return d.toInstant().toString();
        } catch (Exception ignored2) {}
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
            java.util.Date d = sdf.parse(rawDate);
            return d.toInstant().toString();
        } catch (Exception ignored3) {}
        
        return java.time.Instant.now().toString();
    }

    private String formatToIsoEndDate(String isoStartDate) {
        try {
            java.time.Instant start = java.time.Instant.parse(isoStartDate);
            return start.plusSeconds(3600).toString();
        } catch (Exception e) {
            return java.time.Instant.now().plusSeconds(3600).toString();
        }
    }

    private boolean tryUploadGoogleCalendarEvent(MailMessage message, String targetUser, String token) {
        try {
            if (token == null || token.isEmpty()) return false;
            String summary = message.getSubject() != null && !message.getSubject().isEmpty() ? message.getSubject() : "Imported Calendar Event";
            String description = message.getBody() != null ? message.getBody() : "";
            
            String startDate = formatToIsoDate(message.getDate());
            String endDate = formatToIsoEndDate(startDate);
            String jsonPayload = String.format(
                "{\"summary\":\"%s\",\"description\":\"%s\",\"start\":{\"dateTime\":\"%s\"},\"end\":{\"dateTime\":\"%s\"}}",
                escapeJson(summary), escapeJson(description), escapeJson(startDate), escapeJson(endDate)
            );

            java.net.URL url = new java.net.URL("https://www.googleapis.com/calendar/v3/calendars/primary/events");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return true;
            } else {
                String errResp = "";
                try (java.io.InputStream err = conn.getErrorStream()) {
                    if (err != null) errResp = new String(err.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
                System.err.println("[WARN] Google Calendar API returned HTTP " + code + " for " + targetUser + " | Response: " + errResp);
                return false;
            }
        } catch (Exception ex) {
            System.err.println("[WARN] Google Calendar Native Sync failed, falling back to Mail label: " + ex.getMessage());
            return false;
        }
    }

    private boolean tryUploadGoogleTask(MailMessage message, String targetUser, String token) {
        try {
            if (token == null || token.isEmpty()) return false;
            String title = message.getSubject() != null && !message.getSubject().isEmpty() ? message.getSubject() : "Imported Task";
            String notes = message.getBody() != null ? message.getBody() : "";

            String jsonPayload = String.format(
                "{\"title\":\"%s\",\"notes\":\"%s\"}",
                escapeJson(title), escapeJson(notes)
            );

            java.net.URL url = new java.net.URL("https://tasks.googleapis.com/tasks/v1/lists/@default/tasks");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return true;
            } else {
                System.err.println("[WARN] Google Tasks API returned HTTP " + code + " for " + targetUser);
                return false;
            }
        } catch (Exception ex) {
            System.err.println("[WARN] Google Task Native Sync failed, falling back to Mail label: " + ex.getMessage());
            return false;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

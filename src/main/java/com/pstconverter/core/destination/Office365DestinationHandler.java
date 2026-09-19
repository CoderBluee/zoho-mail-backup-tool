package com.pstconverter.core.destination;

import com.pstconverter.core.auth.TokenManager;
import com.pstconverter.core.auth.MicrosoftOAuthService;
import com.pstconverter.model.MailMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.models.MailFolder;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.Importance;
import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.kiota.authentication.AccessTokenProvider;
import com.microsoft.kiota.authentication.AllowedHostsValidator;
import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.ApiException;
import com.microsoft.graph.models.odataerrors.ODataError;

/**
 * Concrete destination adapter for Office 365 / Microsoft 365 cloud accounts.
 * Connects securely using Microsoft Graph API and manages mail folder creation and message uploads.
 *
 * All JSON escaping is delegated to {@link DestinationAdapter#escapeJson} so there is a single
 * canonical implementation shared across adapters.
 */
public class Office365DestinationHandler implements DestinationAdapter {

    private String currentAccessToken;
    private String userEmail;
    private final java.util.Map<String, String> folderCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Maps PST / Outlook well-known folder names (lower-case) to the equivalent
     * Microsoft Graph well-known folder IDs.  These folders exist at the mailbox root —
     * NOT as children of Inbox — so they must not be created via the childFolders API.
     */
    private static final Map<String, String> WELL_KNOWN_FOLDER_IDS = Map.ofEntries(
        Map.entry("inbox",          "inbox"),
        Map.entry("sent items",     "sentitems"),
        Map.entry("sent",           "sentitems"),
        Map.entry("deleted items",  "deleteditems"),
        Map.entry("deleted",        "deleteditems"),
        Map.entry("trash",          "deleteditems"),
        Map.entry("drafts",         "drafts"),
        Map.entry("junk email",     "junkemail"),
        Map.entry("spam",           "junkemail"),
        Map.entry("junk",           "junkemail"),
        Map.entry("outbox",         "outbox"),
        Map.entry("archive",        "archive"),
        Map.entry("clutter",        "clutter"),
        Map.entry("conversation history", "conversationhistory")
    );

    @Override
    public void connect(EmailDestinationConfig config) throws Exception {
        userEmail = config.username();
        verifyOrRefreshTokens(config);
        folderCache.clear();
        cachedClient = null;
        System.out.println("Microsoft Graph API successfully connected for user: " + userEmail);
    }

    @Override
    public void testConnection(EmailDestinationConfig config) throws Exception {
        connect(config);
        disconnect();
    }

    private GraphServiceClient cachedClient;

    private synchronized GraphServiceClient getGraphClient() {
        if (cachedClient == null) {
            AccessTokenProvider tokenProvider = new AccessTokenProvider() {
                @Override
                public String getAuthorizationToken(java.net.URI uri, java.util.Map<String, Object> additionalAuthenticationContext) {
                    return currentAccessToken;
                }

                @Override
                public com.microsoft.kiota.authentication.AllowedHostsValidator getAllowedHostsValidator() {
                    return new com.microsoft.kiota.authentication.AllowedHostsValidator("graph.microsoft.com");
                }
            };
            com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider authProvider = 
                new com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider(tokenProvider);

            int timeoutSec;
            try {
                timeoutSec = Integer.parseInt(com.pstconverter.util.SettingsManager.getSetting("connection_timeout", "30"));
            } catch (Exception e) {
                timeoutSec = 30;
            }

            // Create OkHttpClient Builder with Graph's defaults (interceptors, middleware)
            okhttp3.OkHttpClient.Builder builder = com.microsoft.graph.core.requests.GraphClientFactory.create();
            int readWriteTimeout = Math.max(300, timeoutSec);
            builder.connectTimeout(java.time.Duration.ofSeconds(timeoutSec))
                   .readTimeout(java.time.Duration.ofSeconds(readWriteTimeout))
                   .writeTimeout(java.time.Duration.ofSeconds(readWriteTimeout));

            cachedClient = new GraphServiceClient(authProvider, builder.build());
        }
        return cachedClient;
    }

    private java.util.List<Recipient> parseRecipients(String rawRecipients) {
        java.util.List<Recipient> list = new java.util.ArrayList<>();
        if (rawRecipients == null || rawRecipients.trim().isEmpty()) return list;
        String[] parts = rawRecipients.split(";|,");
        for (String p : parts) {
            String email = extractEmail(p);
            if (!email.isEmpty() && email.contains("@")) {
                Recipient r = new Recipient();
                EmailAddress ea = new EmailAddress();
                ea.setAddress(email);
                r.setEmailAddress(ea);
                list.add(r);
            }
        }
        return list;
    }

    @Override
    public void uploadMessage(MailMessage message, String targetFolder, EmailDestinationConfig config) throws Exception {
        uploadMessage(message, targetFolder, config, null);
    }

    @Override
    public void uploadMessage(MailMessage message, String targetFolder, EmailDestinationConfig config, String overrideTargetUserEmail) throws Exception {
        // Enforce network upload throttling
        DestinationAdapter.applyThrottle(DestinationAdapter.estimateMessageSize(message));

        verifyOrRefreshTokens(config);

        String targetUser = (overrideTargetUserEmail != null && !overrideTargetUserEmail.isEmpty()) 
            ? overrideTargetUserEmail 
            : (config.impersonatedUserEmail() != null && !config.impersonatedUserEmail().isEmpty() ? config.impersonatedUserEmail() : userEmail);

        // Native App Sync Routing Check
        if (config.nativeItemRoutingEnabled()) {
            if (message.isContact()) {
                if (tryUploadGraphContact(message, targetUser)) {
                    System.out.println("[INFO] Natively routed Contact to Microsoft Graph Contacts API for: " + targetUser);
                    return;
                }
            } else if (message.isCalendar()) {
                if (tryUploadGraphCalendarEvent(message, targetUser)) {
                    System.out.println("[INFO] Natively routed Calendar Event to Microsoft Graph Calendar API for: " + targetUser);
                    return;
                }
            } else if (message.isTask()) {
                if (tryUploadGraphTask(message, targetUser)) {
                    System.out.println("[INFO] Natively routed Task to Microsoft Graph To-Do API for: " + targetUser);
                    return;
                }
            }
        }

        String fullTargetFolder = targetFolder;
        if (config.preserveFolderHierarchy()) {
            String sys = DestinationAdapter.mapSystemFolder(targetFolder);
            if (sys != null) {
                if ("INBOX".equalsIgnoreCase(sys)) fullTargetFolder = "inbox";
                else if ("SENT".equalsIgnoreCase(sys)) fullTargetFolder = "sentitems";
                else if ("DRAFT".equalsIgnoreCase(sys)) fullTargetFolder = "drafts";
                else if ("TRASH".equalsIgnoreCase(sys)) fullTargetFolder = "deleteditems";
                else if ("SPAM".equalsIgnoreCase(sys)) fullTargetFolder = "junkemail";
                else fullTargetFolder = sys.toLowerCase(java.util.Locale.ROOT);
            } else {
                fullTargetFolder = targetFolder;
            }
        } else {
            String rootFolder = config.targetFolder() != null ? config.targetFolder().trim() : "";
            if (!rootFolder.isEmpty()) {
                fullTargetFolder = rootFolder + "/" + targetFolder;
            }
        }

        // Resolve or create the MS Graph mail folder hierarchy
        String folderId = getOrCreateFolderHierarchy(targetUser, fullTargetFolder);

        // ── Build Graph API Message Payload ──────────────────────────────────
        String subject     = message.getSubject() != null ? message.getSubject() : "(No Subject)";
        String bodyContent = message.getBody()    != null ? message.getBody()    : "";
        String fromEmail   = message.getFrom()    != null && !message.getFrom().isEmpty()
                             ? message.getFrom() : targetUser;

        // Sanitize — same cleaners used by IMAP and Gmail paths
        subject     = DestinationAdapter.cleanSubject(subject);
        bodyContent = DestinationAdapter.cleanBody(bodyContent);

        // Determine content type (html vs text) — matches logic in populateMimeMessage
        String lowerBody = bodyContent.toLowerCase(Locale.ROOT);
        boolean isHtmlBody = lowerBody.contains("<html")
                          || lowerBody.contains("<body")
                          || lowerBody.contains("<p>")
                          || lowerBody.contains("<br")
                          || lowerBody.contains("<div");

        Message graphMessage = new Message();
        graphMessage.setSubject(subject);
        graphMessage.setImportance(message.getImportance() != null && message.getImportance().equalsIgnoreCase("High") ? Importance.High : Importance.Normal);
        graphMessage.setIsDraft(false);
        boolean isRead = "Read".equalsIgnoreCase(message.getStatus());
        graphMessage.setIsRead(isRead);

        if (message.getMessageId() != null && !message.getMessageId().isEmpty()) {
            graphMessage.setInternetMessageId(message.getMessageId());
        }

        ItemBody body = new ItemBody();
        body.setContentType(isHtmlBody ? BodyType.Html : BodyType.Text);
        body.setContent(bodyContent);
        graphMessage.setBody(body);

        Recipient fromRecipient = new Recipient();
        EmailAddress fromAddr = new EmailAddress();
        fromAddr.setAddress(extractEmail(fromEmail));
        fromRecipient.setEmailAddress(fromAddr);
        graphMessage.setFrom(fromRecipient);

        graphMessage.setToRecipients(parseRecipients(DestinationAdapter.cleanRecipients(message.getTo())));
        graphMessage.setCcRecipients(parseRecipients(DestinationAdapter.cleanRecipients(message.getCc())));
        graphMessage.setBccRecipients(parseRecipients(DestinationAdapter.cleanRecipients(message.getBcc())));

        if (!message.getAttachmentList().isEmpty()) {
            graphMessage.setHasAttachments(true);
            java.util.List<Attachment> list = new java.util.ArrayList<>();
            for (var att : message.getAttachmentList()) {
                if (att.getData() == null) continue;
                FileAttachment fileAtt = new FileAttachment();
                fileAtt.setOdataType("#microsoft.graph.fileAttachment");
                fileAtt.setName(att.getFilename());
                fileAtt.setContentBytes(att.getData());
                list.add(fileAtt);
            }
            graphMessage.setAttachments(list);
        }

        // Execute request with retries and exponential backoff
        int retryCount           = 0;
        int unauthorizedRetryCount = 0;
        int maxRetries = config.maxRetries() > 0 ? config.maxRetries() : 3;
        int delay      = config.initialBackoffMs() > 0 ? config.initialBackoffMs() : 1000;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Upload task interrupted by user request");
            }
            long startTime = System.currentTimeMillis();
            try {
                GraphServiceClient graphClient = getGraphClient();
                if (config.isAdminImpersonation()) {
                    graphClient.users().byUserId(targetUser)
                            .mailFolders()
                            .byMailFolderId(folderId)
                            .messages()
                            .post(graphMessage);
                } else {
                    graphClient.me()
                            .mailFolders()
                            .byMailFolderId(folderId)
                            .messages()
                            .post(graphMessage);
                }
                long duration = System.currentTimeMillis() - startTime;
                com.pstconverter.util.DiagnosticLogger.logCloudUploadAttempt(
                    message.getUniqueIdentifier(),
                    message.getSubject(),
                    "Office 365",
                    retryCount + 1,
                    maxRetries,
                    true,
                    duration,
                    null
                );
                break; // Created successfully
            } catch (ApiException e) {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = e.getResponseStatusCode();
                String errorMsg = e.getMessage();
                com.pstconverter.util.DiagnosticLogger.logCloudUploadAttempt(
                    message.getUniqueIdentifier(),
                    message.getSubject(),
                    "Office 365",
                    retryCount + 1,
                    maxRetries,
                    false,
                    duration,
                    "API Error " + statusCode + ": " + errorMsg
                );

                if (statusCode == 401) {
                    if (unauthorizedRetryCount >= 2) {
                        throw new IOException("Failed to upload message to Office 365 due to persistent 401 Unauthorized after refreshing tokens. Error: " + errorMsg, e);
                    }
                    System.out.println("[WARNING] MS Graph API 401 Unauthorized. Refreshing token and retrying...");
                    forceRefreshToken(config);
                    unauthorizedRetryCount++;
                } else if (statusCode == 429) {
                    if (retryCount >= maxRetries) {
                        throw new IOException("Failed to upload message to Office 365 due to Graph API throttling after " + retryCount + " retries. Error: " + errorMsg, e);
                    }
                    System.out.println("[WARNING] MS Graph API Rate Limit hit. Backing off for " + delay + "ms...");
                    Thread.sleep(delay);
                    retryCount++;
                    delay *= 2;
                } else if (statusCode == 413) {
                    throw new IOException("Failed to upload message to Office 365. Payload too large (exceeds 4MB). Try removing large attachments.", e);
                } else {
                    throw new IOException("Failed to upload message to MS Graph. HTTP Status: " + statusCode + ", Response: " + errorMsg, e);
                }
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                com.pstconverter.util.DiagnosticLogger.logCloudUploadAttempt(
                    message.getUniqueIdentifier(),
                    message.getSubject(),
                    "Office 365",
                    retryCount + 1,
                    maxRetries,
                    false,
                    duration,
                    e.getMessage()
                );

                if (Thread.currentThread().isInterrupted() || e instanceof InterruptedException) {
                    throw e;
                }
                
                // Check if the cause is MalformedJsonException or has similar non-retryable signatures
                Throwable cause = e;
                boolean isNonRetryable = false;
                while (cause != null) {
                    if (cause instanceof com.google.gson.stream.MalformedJsonException 
                            || (cause.getMessage() != null && (cause.getMessage().contains("MalformedJsonException") 
                            || cause.getMessage().contains("Unterminated object")))) {
                        isNonRetryable = true;
                        break;
                    }
                    cause = cause.getCause();
                }

                if (isNonRetryable) {
                    throw new IOException("Failed to upload message to Office 365 due to malformed payload or attachment structure: " + e.getMessage(), e);
                }

                if (retryCount >= maxRetries) {
                    throw e;
                }
                System.out.println("[WARNING] MS Graph API network or connection failure: " + e.getMessage() + ". Retrying upload in " + delay + "ms... (Attempt " + (retryCount + 1) + "/" + maxRetries + ")");
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
                retryCount++;
                delay *= 2; // Exponential backoff
            }
        }
    }

    private String extractEmail(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        Matcher m = Pattern.compile("<([^>]+)>").matcher(raw);
        if (m.find()) return m.group(1).trim();
        return raw;
    }

    @Override
    public boolean isMessageAlreadyOnServer(MailMessage message, String targetFolder, EmailDestinationConfig config) throws Exception {
        verifyOrRefreshTokens(config);
        String rootFolder = config.targetFolder() != null ? config.targetFolder().trim() : "";
        String fullTargetFolder = targetFolder;
        if (!rootFolder.isEmpty()) {
            fullTargetFolder = rootFolder + "/" + targetFolder;
        }
        String targetUser = (config.impersonatedUserEmail() != null && !config.impersonatedUserEmail().isEmpty()) ? config.impersonatedUserEmail() : userEmail;
        String folderId = getOrCreateFolderHierarchy(targetUser, fullTargetFolder);
        GraphServiceClient graphClient = getGraphClient();
        boolean isImpersonating = targetUser != null && !targetUser.isEmpty() && !targetUser.equalsIgnoreCase(userEmail);
        String msgId = message.getMessageId();
        if (msgId != null && !msgId.trim().isEmpty()) {
            var request = isImpersonating 
                ? graphClient.users().byUserId(targetUser).mailFolders().byMailFolderId(folderId).messages()
                : graphClient.me().mailFolders().byMailFolderId(folderId).messages();
            var response = request.get(configRequest -> {
                configRequest.queryParameters.filter = "internetMessageId eq '" + msgId.replace("'", "''") + "'";
            });
            return response != null && response.getValue() != null && !response.getValue().isEmpty();
        } else if (message.getSubject() != null && !message.getSubject().trim().isEmpty()) {
            var request = isImpersonating 
                ? graphClient.users().byUserId(targetUser).mailFolders().byMailFolderId(folderId).messages()
                : graphClient.me().mailFolders().byMailFolderId(folderId).messages();
            var response = request.get(configRequest -> {
                configRequest.queryParameters.filter = "subject eq '" + message.getSubject().replace("'", "''") + "'";
            });
            return response != null && response.getValue() != null && !response.getValue().isEmpty();
        }
        return false;
    }

    @Override
    public void disconnect() throws Exception {
        currentAccessToken = null;
        userEmail = null;
        cachedClient = null;
    }

    // ── Token management ─────────────────────────────────────────────────────

    private synchronized void forceRefreshToken(EmailDestinationConfig config) throws Exception {
        String refreshToken = TokenManager.getRefreshToken(userEmail);
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new IllegalStateException("Office 365 refresh token is missing. Please re-authorize account in Step 4.");
        }
        String tenantId     = config.tenantId();
        String clientId     = config.clientId();
        String clientSecret = config.clientSecret();
        if (clientId == null || clientId.isEmpty() || clientId.contains("dummy")) {
            var secrets = MicrosoftOAuthService.loadClientSecrets();
            clientId     = secrets.clientId;
            clientSecret = secrets.clientSecret;
            tenantId     = secrets.tenantId;
        }
        var result = MicrosoftOAuthService.refreshTokens(tenantId, refreshToken, clientId, clientSecret);
        TokenManager.saveTokens(userEmail, result.accessToken(), result.refreshToken(), result.expiryTimeMs());
        currentAccessToken = result.accessToken();
    }

    private synchronized void verifyOrRefreshTokens(EmailDestinationConfig config) throws Exception {
        if (TokenManager.isTokenExpired(userEmail) || TokenManager.getAccessToken(userEmail) == null) {
            System.out.println("[INFO] Office 365 access token expired or missing. Refreshing token...");
            forceRefreshToken(config);
        } else {
            currentAccessToken = TokenManager.getAccessToken(userEmail);
        }
    }

    // ── Folder resolution ────────────────────────────────────────────────────

    private String getOrCreateFolderHierarchy(String folderPath) throws Exception {
        return getOrCreateFolderHierarchy(userEmail, folderPath);
    }

    /**
     * Resolves a PST folder hierarchy path (e.g. "Deleted Items/Subfolder") to a
     * Microsoft Graph mail folder ID, creating missing folders as needed.
     *
     * The first segment of the path is checked against {@link #WELL_KNOWN_FOLDER_IDS}
     * so that Outlook well-known folders (Deleted Items, Sent Items, Drafts …) are mapped
     * to the correct Graph IDs rather than created as children of Inbox.
     */
    private String getOrCreateFolderHierarchy(String targetUser, String folderPath) throws Exception {
        String cacheKey = (targetUser != null ? targetUser.toLowerCase(Locale.ROOT) : "") + "::" + folderPath;
        String cleanPath = folderPath.replace('\\', '/').trim();
        if (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
        if (cleanPath.endsWith("/"))   cleanPath = cleanPath.substring(0, cleanPath.length() - 1);

        String cachedId = folderCache.get(cacheKey);
        if (cachedId != null) {
            return cachedId;
        }

        String[] parts = cleanPath.split("/");

        // Resolve the first segment — may be a well-known folder
        String firstSegmentLower = parts[0].trim().toLowerCase(Locale.ROOT);
        String parentFolderId = WELL_KNOWN_FOLDER_IDS.getOrDefault(firstSegmentLower, null);

        StringBuilder currentPath = new StringBuilder();
        if (parentFolderId != null) {
            currentPath.append(parts[0]);
            folderCache.put(targetUser + "::" + currentPath.toString(), parentFolderId);
            
            // The first segment mapped to a known Graph folder ID — start hierarchy from there
            for (int i = 1; i < parts.length; i++) {
                currentPath.append("/").append(parts[i]);
                String stepPath = currentPath.toString();
                String stepId = folderCache.get(targetUser + "::" + stepPath);
                if (stepId == null) {
                    stepId = getOrCreateChildFolder(targetUser, parentFolderId, parts[i].trim(), false);
                    folderCache.put(targetUser + "::" + stepPath, stepId);
                }
                parentFolderId = stepId;
            }
        } else {
            // No well-known match — create from the mailbox root
            parentFolderId = null;
            for (String part : parts) {
                if (currentPath.length() > 0) {
                    currentPath.append("/");
                }
                currentPath.append(part);
                String stepPath = currentPath.toString();
                String stepId = folderCache.get(targetUser + "::" + stepPath);
                if (stepId == null) {
                    stepId = getOrCreateChildFolder(targetUser, parentFolderId, part.trim(), parentFolderId == null);
                    folderCache.put(targetUser + "::" + stepPath, stepId);
                }
                parentFolderId = stepId;
            }
        }
        folderCache.put(cacheKey, parentFolderId);
        return parentFolderId;
    }

    private String getOrCreateChildFolder(String targetUser, String parentFolderId, String folderName, boolean isRoot) throws Exception {
        GraphServiceClient graphClient = getGraphClient();
        boolean isImpersonating = targetUser != null && !targetUser.isEmpty() && !targetUser.equalsIgnoreCase(userEmail);
        try {
            java.util.List<MailFolder> folders;
            if (isRoot || parentFolderId == null) {
                var request = isImpersonating 
                    ? graphClient.users().byUserId(targetUser).mailFolders()
                    : graphClient.me().mailFolders();
                var response = request.get(config -> {
                    config.queryParameters.top = 100;
                });
                folders = response.getValue();
            } else {
                var request = isImpersonating 
                    ? graphClient.users().byUserId(targetUser).mailFolders().byMailFolderId(parentFolderId).childFolders()
                    : graphClient.me().mailFolders().byMailFolderId(parentFolderId).childFolders();
                var response = request.get(config -> {
                    config.queryParameters.top = 100;
                });
                folders = response.getValue();
            }

            if (folders != null) {
                for (MailFolder f : folders) {
                    if (folderName.equalsIgnoreCase(f.getDisplayName())) {
                        return f.getId();
                    }
                }
            }
        } catch (ApiException e) {
            // Ignore directory listing error and proceed to creation attempts
        }

        // Not found — create the folder
        MailFolder newFolder = new MailFolder();
        newFolder.setDisplayName(folderName);

        try {
            MailFolder created;
            if (isRoot || parentFolderId == null) {
                created = isImpersonating 
                    ? graphClient.users().byUserId(targetUser).mailFolders().post(newFolder)
                    : graphClient.me().mailFolders().post(newFolder);
            } else {
                created = isImpersonating 
                    ? graphClient.users().byUserId(targetUser).mailFolders().byMailFolderId(parentFolderId).childFolders().post(newFolder)
                    : graphClient.me().mailFolders().byMailFolderId(parentFolderId).childFolders().post(newFolder);
            }
            if (created != null && created.getId() != null) {
                return created.getId();
            }
        } catch (ApiException createEx) {
            if (createEx.getResponseStatusCode() == 409) {
                // Folder already exists (race condition) — re-fetch to get the ID
                java.util.List<MailFolder> folders;
                if (isRoot || parentFolderId == null) {
                    folders = graphClient.me().mailFolders().get(config -> {
                        config.queryParameters.top = 100;
                    }).getValue();
                } else {
                    folders = graphClient.me().mailFolders().byMailFolderId(parentFolderId).childFolders().get(config -> {
                        config.queryParameters.top = 100;
                    }).getValue();
                }
                if (folders != null) {
                    for (MailFolder f : folders) {
                        if (folderName.equalsIgnoreCase(f.getDisplayName())) {
                            return f.getId();
                        }
                    }
                }
            }
            throw new IOException("Failed to resolve or create MS Graph mail folder: \"" + folderName
                + "\". Status: " + createEx.getResponseStatusCode() + ", Response: " + createEx.getMessage(), createEx);
        }

        throw new IOException("Failed to resolve or create MS Graph mail folder: \"" + folderName + "\" (unknown error)");
    }

    private String resolveGraphUserPath(String targetUser) {
        if (targetUser == null || targetUser.isEmpty() || targetUser.equalsIgnoreCase("me") || targetUser.equalsIgnoreCase(userEmail)) {
            return "me";
        }
        return "users/" + targetUser;
    }

    private boolean tryUploadGraphContact(MailMessage message, String targetUser) {
        try {
            if (currentAccessToken == null || currentAccessToken.isEmpty()) return false;
            String name = message.getSubject() != null && !message.getSubject().isEmpty() ? message.getSubject() : message.getFrom();
            String email = message.getSenderAddress() != null && !message.getSenderAddress().isEmpty() ? message.getSenderAddress() : message.getFrom();
            if (name == null || name.isEmpty()) name = "Imported Contact";

            String jsonPayload = String.format(
                "{\"givenName\":\"%s\",\"emailAddresses\":[{\"address\":\"%s\"}]}",
                escapeJson(name), escapeJson(email)
            );

            String endpoint = "https://graph.microsoft.com/v1.0/" + resolveGraphUserPath(targetUser) + "/contacts";
            java.net.URL url = new java.net.URL(endpoint);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + currentAccessToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ex) {
            System.err.println("[WARN] Office 365 Contact Native Sync failed, falling back to Mail folder: " + ex.getMessage());
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

    private boolean tryUploadGraphCalendarEvent(MailMessage message, String targetUser) {
        try {
            if (currentAccessToken == null || currentAccessToken.isEmpty()) return false;
            String summary = message.getSubject() != null && !message.getSubject().isEmpty() ? message.getSubject() : "Imported Calendar Event";
            String bodyStr = message.getBody() != null ? message.getBody() : "";
            String startDate = formatToIsoDate(message.getDate());
            String endDate = formatToIsoEndDate(startDate);

            String jsonPayload = String.format(
                "{\"subject\":\"%s\",\"body\":{\"contentType\":\"text\",\"content\":\"%s\"},\"start\":{\"dateTime\":\"%s\",\"timeZone\":\"UTC\"},\"end\":{\"dateTime\":\"%s\",\"timeZone\":\"UTC\"}}",
                escapeJson(summary), escapeJson(bodyStr), escapeJson(startDate), escapeJson(endDate)
            );

            String endpoint = "https://graph.microsoft.com/v1.0/" + resolveGraphUserPath(targetUser) + "/events";
            java.net.URL url = new java.net.URL(endpoint);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + currentAccessToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ex) {
            System.err.println("[WARN] Office 365 Calendar Event Native Sync failed, falling back to Mail folder: " + ex.getMessage());
            return false;
        }
    }

    private boolean tryUploadGraphTask(MailMessage message, String targetUser) {
        try {
            if (currentAccessToken == null || currentAccessToken.isEmpty()) return false;
            String title = message.getSubject() != null && !message.getSubject().isEmpty() ? message.getSubject() : "Imported Task";
            String bodyStr = message.getBody() != null ? message.getBody() : "";

            String jsonPayload = String.format(
                "{\"title\":\"%s\",\"body\":{\"contentType\":\"text\",\"content\":\"%s\"}}",
                escapeJson(title), escapeJson(bodyStr)
            );

            String endpoint = "https://graph.microsoft.com/v1.0/" + resolveGraphUserPath(targetUser) + "/todo/lists/tasks";
            java.net.URL url = new java.net.URL(endpoint);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + currentAccessToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ex) {
            System.err.println("[WARN] Office 365 Task Native Sync failed, falling back to Mail folder: " + ex.getMessage());
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

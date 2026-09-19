package com.pstconverter.core.destination;

import com.pstconverter.model.MailMessage;
import com.pstconverter.imap.ImapAuthHelper;
import com.chilkatsoft.CkImap;
import com.chilkatsoft.CkEmail;
import com.chilkatsoft.CkMessageSet;

/**
 * Concrete destination adapter for Generic IMAP and Yahoo Mail servers.
 * Connects securely via SSL/TLS and appends migrated messages directly into the target mailbox folders.
 * Powered by Chilkat.
 */
public class ImapDestinationHandler implements DestinationAdapter {

    private CkImap imap;
    private char separator = '/';

    private static final java.util.Set<String> verifiedFolders = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void clearCache() {
        verifiedFolders.clear();
    }

    @Override
    public void connect(EmailDestinationConfig config) throws Exception {
        com.pstconverter.util.ChilkatLibraryLoader.loadAndUnlock();
        if (imap != null) {
            try {
                imap.delete();
            } catch (Exception ignored) {}
        }
        imap = new CkImap();

        int timeoutSec;
        try {
            timeoutSec = Integer.parseInt(com.pstconverter.util.SettingsManager.getSetting("connection_timeout", "30"));
        } catch (Exception e) {
            timeoutSec = 30;
        }

        imap.put_ConnectTimeout(timeoutSec);
        imap.put_ReadTimeout(timeoutSec);
        imap.put_Ssl(config.ssl());
        imap.put_Port(config.port());

        int connectAttempts = 0;
        int maxConnectAttempts = 5;
        try {
            maxConnectAttempts = Integer.parseInt(com.pstconverter.util.SettingsManager.getSetting("retry_count", "3"));
        } catch (Exception e) {
            maxConnectAttempts = 3;
        }
        int connectDelay = 3000;
        java.util.Random random = new java.util.Random();

        String batchData = config.serviceAccountJsonPath();
        if ((config.host() == null || config.host().trim().isEmpty() || config.host().startsWith("IMAP Batch")) && batchData != null && batchData.startsWith("IMAP_BATCH:")) {
            String[] lines = batchData.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("IMAP_BATCH:")) {
                    String payload = trimmed.substring("IMAP_BATCH:".length());
                    String[] parts = payload.split("\\|");
                    if (parts.length >= 5) {
                        String targetUser = parts[3].trim();
                        connectBatchUser(targetUser, batchData, config);
                        return;
                    }
                }
            }
        }

        String cleanUser = config.username() != null ? config.username().replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF]+", "").trim() : "";
        String rawPass = config.password() != null ? config.password() : "";
        String cleanPass = ImapAuthHelper.sanitizeAppPassword(rawPass);
        if (cleanPass.isEmpty()) {
            cleanPass = rawPass.replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF]+", "").trim();
        }

        while (true) {
            boolean success = imap.Connect(config.host());
            if (!success) {
                connectAttempts++;
                String fullErrorLog = imap.lastErrorText();
                String firstLine = fullErrorLog.split("\n")[0].trim();
                if (connectAttempts < maxConnectAttempts) {
                    int jitter = random.nextInt(1000);
                    long delay = connectDelay + jitter;
                    System.out.println("[WARNING] IMAP connection failed (attempt " + connectAttempts + "/" + maxConnectAttempts + "). Retrying in " + delay + "ms...");
                    System.err.println("[IMAP-CONNECT-DETAIL]\n" + fullErrorLog);
                    com.pstconverter.util.DiagnosticLogger.log("[IMAP CONNECT FAIL attempt=" + connectAttempts + "] " + firstLine + "\nFull log:\n" + fullErrorLog);
                    Thread.sleep(delay);
                    connectDelay *= 2;
                    continue;
                } else {
                    System.err.println("[IMAP-CONNECT-ERROR] Full Chilkat diagnostic log:\n" + fullErrorLog);
                    com.pstconverter.util.DiagnosticLogger.error("[IMAP CONNECT FAILED after " + connectAttempts + " attempts] " + firstLine + "\nFull log:\n" + fullErrorLog, null);
                    throw new Exception("IMAP connect failed: " + firstLine + " (see logs for full detail)");
                }
            }
            
            success = imap.Login(cleanUser, cleanPass);
            if (!success && !rawPass.equals(cleanPass)) {
                success = imap.Login(cleanUser, rawPass);
            }
            if (!success) {
                String fullErrorLog = imap.lastErrorText();
                String firstLine = fullErrorLog.split("\n")[0].trim();
                System.err.println("[IMAP-LOGIN-ERROR] Full Chilkat diagnostic log:\n" + fullErrorLog);
                com.pstconverter.util.DiagnosticLogger.error("[IMAP LOGIN FAILED] " + firstLine + "\nFull log:\n" + fullErrorLog, null);
                throw new Exception("IMAP login failed: " + firstLine + " (see logs for full detail)");
            }
            break;
        }

        String sep = imap.separatorChar();
        if (sep != null && !sep.isEmpty()) {
            separator = sep.charAt(0);
        }

        System.out.println("IMAP successfully connected to " + config.host() + ":" + config.port());
    }

    @Override
    public void testConnection(EmailDestinationConfig config) throws Exception {
        connect(config);
        disconnect();
    }

    @Override
    public void uploadMessage(MailMessage message, String targetFolder, EmailDestinationConfig config) throws Exception {
        String fullTargetFolder = targetFolder;
        if (config.preserveFolderHierarchy()) {
            String sys = DestinationAdapter.mapSystemFolder(targetFolder);
            if (sys != null) {
                if ("INBOX".equalsIgnoreCase(sys)) fullTargetFolder = "INBOX";
                else if ("SENT".equalsIgnoreCase(sys)) fullTargetFolder = "Sent";
                else if ("DRAFT".equalsIgnoreCase(sys)) fullTargetFolder = "Drafts";
                else if ("TRASH".equalsIgnoreCase(sys)) fullTargetFolder = "Trash";
                else if ("SPAM".equalsIgnoreCase(sys)) fullTargetFolder = "Junk";
                else fullTargetFolder = sys;
            } else {
                fullTargetFolder = targetFolder;
            }
        } else {
            String rootFolder = config.targetFolder() != null ? config.targetFolder().trim() : "";
            if (!rootFolder.isEmpty()) {
                fullTargetFolder = rootFolder + "/" + targetFolder;
            }
        }

        String sanitizedFolder = fullTargetFolder.replace('/', separator).replace('\\', separator);
        if (!sanitizedFolder.isEmpty() && sanitizedFolder.charAt(0) == separator) {
            sanitizedFolder = sanitizedFolder.substring(1);
        }

        currentConnectedUser = config.username();

        DestinationAdapter.applyThrottle(DestinationAdapter.estimateMessageSize(message));

        System.out.println("[INFO] IMAP: Uploading message to folder: \"" + sanitizedFolder + "\" | Subject: " + message.getSubject());

        int maxRetries = config.maxRetries() > 0 ? config.maxRetries() : 3;
        int delayMs = config.initialBackoffMs() > 0 ? config.initialBackoffMs() : 2000;
        if (delayMs < 1000) delayMs = 2000;

        int retryCount = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Upload task interrupted by user request");
            }
            long attemptStartTime = System.currentTimeMillis();
            try {
                if (imap == null || !imap.IsConnected()) {
                    connect(config);
                }

                getOrCreateFolder(sanitizedFolder, separator);

                CkEmail ckEmail = DestinationAdapter.createCkEmail(message, config.username());
                try {
                    imap.put_AppendSeen("Read".equalsIgnoreCase(message.getStatus()));
                    boolean success = imap.AppendMail(sanitizedFolder, ckEmail);
                    if (!success) {
                        throw new Exception(imap.lastErrorText());
                    }
                } finally {
                    ckEmail.delete();
                }

                long duration = System.currentTimeMillis() - attemptStartTime;
                com.pstconverter.util.DiagnosticLogger.logCloudUploadAttempt(
                    message.getUniqueIdentifier(),
                    message.getSubject(),
                    "IMAP Server",
                    retryCount + 1,
                    maxRetries,
                    true,
                    duration,
                    null
                );
                break;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - attemptStartTime;
                String errMsg = e.getMessage() != null ? e.getMessage() : "";
                
                com.pstconverter.util.DiagnosticLogger.logCloudUploadAttempt(
                    message.getUniqueIdentifier(),
                    message.getSubject(),
                    "IMAP Server",
                    retryCount + 1,
                    maxRetries,
                    false,
                    duration,
                    errMsg
                );
                
                if (Thread.currentThread().isInterrupted() || e instanceof InterruptedException) {
                    throw e;
                }

                synchronized (this) {
                    verifiedFolders.remove(sanitizedFolder);
                    if (imap != null) {
                        try {
                            imap.Disconnect();
                        } catch (Exception ignored) {}
                        try {
                            imap.delete();
                        } catch (Exception ignored) {}
                    }
                    imap = null;
                }

                if (retryCount < maxRetries) {
                    retryCount++;
                    System.out.println("[WARNING] IMAP upload failure: \"" + errMsg + "\". Retrying in " + delayMs + "ms... (Attempt " + retryCount + "/" + maxRetries + ")");
                    Thread.sleep(delayMs);
                    delayMs *= 2; 
                } else {
                    System.err.println("[ERROR] Failed to upload message via IMAP after " + retryCount + " attempts. Folder: " + sanitizedFolder + " | Error: " + errMsg);
                    e.printStackTrace();
                    throw e;
                }
            }
        }
    }

    private void getOrCreateFolder(String folderPath, char separator) throws Exception {
        if (verifiedFolders.contains(folderPath)) {
            return;
        }

        boolean exists = imap.SelectMailbox(folderPath);
        if (exists) {
            verifiedFolders.add(folderPath);
            return;
        }

        String[] parts = folderPath.split(java.util.regex.Pattern.quote(String.valueOf(separator)));
        StringBuilder currentPath = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (currentPath.length() > 0) {
                currentPath.append(separator);
            }
            currentPath.append(part);
            String currentPathStr = currentPath.toString();
            if (verifiedFolders.contains(currentPathStr)) {
                continue;
            }
            if (!imap.SelectMailbox(currentPathStr)) {
                System.out.println("[INFO] Creating IMAP folder hierarchy step: " + currentPathStr);
                boolean created = imap.CreateMailbox(currentPathStr);
                if (!created) {
                    throw new Exception("Failed to create mailbox " + currentPathStr + ": " + imap.lastErrorText());
                }
            }
            verifiedFolders.add(currentPathStr);
        }
        verifiedFolders.add(folderPath);
    }

    @Override
    public boolean isMessageAlreadyOnServer(MailMessage message, String targetFolder, EmailDestinationConfig config) throws Exception {
        if (imap == null || !imap.IsConnected()) {
            connect(config);
        }
        String rootFolder = config.targetFolder() != null ? config.targetFolder().trim() : "";
        String fullTargetFolder = targetFolder;
        if (!rootFolder.isEmpty()) {
            fullTargetFolder = rootFolder + "/" + targetFolder;
        }
        String sanitizedFolder = fullTargetFolder.replace('/', separator).replace('\\', separator);
        if (!sanitizedFolder.isEmpty() && sanitizedFolder.charAt(0) == separator) {
            sanitizedFolder = sanitizedFolder.substring(1);
        }
        
        if (!imap.SelectMailbox(sanitizedFolder)) {
            return false;
        }
        
        String msgId = message.getMessageId();
        if (msgId != null && !msgId.trim().isEmpty()) {
            CkMessageSet msgSet = imap.Search("HEADER Message-ID \"" + msgId + "\"", true);
            if (msgSet != null) {
                try {
                    if (msgSet.get_Count() > 0) {
                        return true;
                    }
                } finally {
                    msgSet.delete();
                }
            }
        } else if (message.getSubject() != null && !message.getSubject().trim().isEmpty()) {
            CkMessageSet msgSet = imap.Search("SUBJECT \"" + message.getSubject().replace("\"", "") + "\"", true);
            if (msgSet != null) {
                try {
                    if (msgSet.get_Count() > 0) {
                        return true;
                    }
                } finally {
                    msgSet.delete();
                }
            }
        }
        return false;
    }

    private String currentConnectedUser = null;

    @Override
    public void uploadMessage(MailMessage message, String targetFolder, EmailDestinationConfig config, String overrideTargetUserEmail) throws Exception {
        if (overrideTargetUserEmail != null && !overrideTargetUserEmail.trim().isEmpty()) {
            String targetUser = overrideTargetUserEmail.trim();
            if (currentConnectedUser == null || !targetUser.equalsIgnoreCase(currentConnectedUser)) {
                String batchData = config.serviceAccountJsonPath();
                if (batchData != null && batchData.startsWith("IMAP_BATCH:")) {
                    connectBatchUser(targetUser, batchData, config);
                }
            }
        } else if (currentConnectedUser == null) {
            String batchData = config.serviceAccountJsonPath();
            if (batchData != null && batchData.startsWith("IMAP_BATCH:")) {
                connect(config);
            }
        }
        uploadMessage(message, targetFolder, config);
    }

    private void connectBatchUser(String targetUser, String batchData, EmailDestinationConfig config) throws Exception {
        String[] lines = batchData.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("IMAP_BATCH:")) {
                String payload = trimmed.substring("IMAP_BATCH:".length());
                String[] parts = payload.split("\\|");
                if (parts.length >= 5) {
                    String host = parts[0].trim();
                    int port = Integer.parseInt(parts[1].trim());
                    boolean ssl = Boolean.parseBoolean(parts[2].trim());
                    String username = parts[3].trim();
                    String password = parts[4].trim();

                    if (username.equalsIgnoreCase(targetUser)) {
                        EmailDestinationConfig userConfig = new EmailDestinationConfig(
                            config.destinationType(), config.authMode(), host, port, ssl,
                            username, password, config.clientId(), config.clientSecret(),
                            config.tenantId(), config.redirectUri(), config.targetFolder(),
                            config.bulkEnabled(), config.bulkCsvPath(), config.throttlingDelayMs(),
                            config.maxMessagesPerMin(), config.maxRetries(), config.initialBackoffMs(),
                            config.adminImpersonationEnabled(), null, // null serviceJson to prevent recursion
                            config.impersonatedUserEmail(), config.userMappingMap(),
                            config.nativeItemRoutingEnabled(), config.preserveFolderHierarchy()
                        );
                        connect(userConfig);
                        currentConnectedUser = username;
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void disconnect() throws Exception {
        if (imap != null) {
            if (imap.IsConnected()) {
                imap.Disconnect();
            }
            imap.delete();
        }
        imap = null;
        currentConnectedUser = null;
    }
}

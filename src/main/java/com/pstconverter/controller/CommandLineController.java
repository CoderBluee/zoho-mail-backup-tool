package com.pstconverter.controller;

import com.pstconverter.core.adapter.SourceAdapter;
import com.pstconverter.core.adapter.SourceAdapterFactory;
import com.pstconverter.core.filter.FilterEngine;
import com.pstconverter.core.output.OutputFactory;
import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;
import com.pstconverter.model.MailboxFolder;
import com.pstconverter.util.LicenseManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class CommandLineController {

    private CommandLineController() {}

    public static record FolderWithKey(MailboxFolder folder, List<String> path) {
        public String getFolderPathKey() {
            StringBuilder sb = new StringBuilder();
            for (String p : path) {
                if (sb.length() > 0) sb.append("/");
                sb.append(p);
            }
            return sb.toString();
        }
    }

    public static void run(String configPath) {
        System.out.println("================================================================================");
        System.out.println(com.pstconverter.config.BrandConfig.TOOL_NAME.toUpperCase() + " - HEADLESS COMMAND LINE INTERFACE");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Config File: " + configPath);

        File configFile = new File(configPath);
        if (!configFile.exists()) {
            System.err.println("[ERROR] Configuration file does not exist: " + configPath);
            System.exit(1);
        }

        try {
            Properties config = loadConfig(configFile);
            
            String licenseKey = config.getProperty("licensing.key", "");
            if (!licenseKey.isEmpty()) {
                System.out.println("Verifying License Key: " + licenseKey);
                if (LicenseManager.activate(licenseKey)) {
                    System.out.println("License Activated successfully.");
                } else {
                    System.out.println("Warning: Invalid license key. Running in Trial Evaluation Mode.");
                }
            } else {
                System.out.println("Running in Trial Evaluation Mode (Limit " + LicenseManager.TRIAL_LIMIT_PER_FOLDER + " items per folder).");
            }

            String sourceListStr = config.getProperty("source_files", "");
            if (sourceListStr.isEmpty()) {
                System.err.println("[ERROR] No source files specified under 'source_files'.");
                System.exit(1);
            }
            List<String> sourceFiles = Arrays.asList(sourceListStr.split(","));

            String destParent = config.getProperty("destination_parent", "");
            if (destParent.isEmpty()) {
                System.err.println("[ERROR] No destination parent folder specified under 'destination_parent'.");
                System.exit(1);
            }
            File destParentDir = new File(destParent);
            if (!destParentDir.exists()) {
                destParentDir.mkdirs();
            }

            String format = config.getProperty("export_format", "TXT").toUpperCase();
            System.out.println("Export Format: " + format);
            OutputHandler handler = OutputFactory.getHandler(format);
            if (handler == null) {
                System.err.println("[ERROR] Unsupported export format: " + format);
                System.exit(1);
            }

            String namingConvention = config.getProperty("naming_convention", "Original Subject");
            String attachHandling = config.getProperty("attachment_handling", "Keep Attachments in Folder");
            String exportStructure = config.getProperty("export_structure", "Individual File per Email (One file per message)");

            // Setup Filter properties
            Properties filterProps = new Properties();
            for (String key : config.stringPropertyNames()) {
                if (key.startsWith("filters.")) {
                    filterProps.setProperty(key.substring(8), config.getProperty(key));
                }
            }
            FilterEngine filterEngine = new FilterEngine(filterProps);

            // Execute migration for each file
            for (String srcPath : sourceFiles) {
                File srcFile = new File(srcPath.trim());
                if (!srcFile.exists()) {
                    System.err.println("[WARNING] Source file does not exist, skipping: " + srcPath);
                    continue;
                }
                System.out.println("Parsing mailbox file: " + srcFile.getAbsolutePath());
                SourceAdapter adapter = SourceAdapterFactory.getAdapterForFile(srcFile);
                if (adapter == null || !adapter.validateFile(srcFile)) {
                    System.err.println("[ERROR] Failed to validate source mailbox adapter for file: " + srcFile.getName());
                    continue;
                }

                MailboxFolder rootFolder = adapter.parseFolderStructure(srcFile, (task, status) -> {});
                if (rootFolder == null) {
                    System.err.println("[ERROR] Mailbox hierarchy is empty or failed to parse.");
                    continue;
                }

                // Collect list of folders
                List<FolderWithKey> foldersList = new ArrayList<>();
                collectFolders(rootFolder, new ArrayList<>(), foldersList);
                System.out.println("Discovered " + foldersList.size() + " folders in mailbox.");

                // Create a CSV audit trail file
                String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                File csvFile = new File(destParentDir, "migration_audit_" + timeStamp + ".csv");
                try (PrintWriter csvWriter = new PrintWriter(new FileWriter(csvFile))) {
                    csvWriter.println("Timestamp,Folder Path,Sender,Subject,Message-ID,SHA-256 Hash,Status,Details");

                    AtomicInteger processedCount = new AtomicInteger(0);
                    AtomicInteger successCount = new AtomicInteger(0);
                    AtomicInteger skippedCount = new AtomicInteger(0);
                    AtomicInteger failedCount = new AtomicInteger(0);

                    for (FolderWithKey item : foldersList) {
                        String folderKey = item.getFolderPathKey();
                        List<MailMessage> messages = null;
                        try {
                            messages = adapter.getEmails(srcFile, item.path());
                        } catch (Exception ex) {
                            System.err.println("[ERROR] Failed to read emails from folder: " + item.folder().getName() + " - " + ex.getMessage());
                        }
                        if (messages == null || messages.isEmpty()) {
                            continue;
                        }

                        System.out.println("Processing folder: " + item.folder().getName() + " (" + messages.size() + " items)");
                        
                        File targetDir = new File(destParentDir, item.getFolderPathKey().replace('/', File.separatorChar));
                        if (!targetDir.exists()) {
                            targetDir.mkdirs();
                        }

                        OutputHandler.Session session = handler.openSession(targetDir, attachHandling, exportStructure, namingConvention);
                        if (session == null) {
                            System.err.println("[ERROR] Failed to open export session for folder: " + item.folder().getName());
                            failedCount.addAndGet(messages.size());
                            continue;
                        }

                        int folderSuccess = 0;
                        for (MailMessage msg : messages) {
                            processedCount.incrementAndGet();
                            boolean isSkip = !filterEngine.test(msg);
                            String skipReason = null;

                            if (!isSkip && !LicenseManager.isActivated()) {
                                if (folderSuccess >= LicenseManager.TRIAL_LIMIT_PER_FOLDER) {
                                    isSkip = true;
                                    skipReason = "Trial Mode: Limit of " + LicenseManager.TRIAL_LIMIT_PER_FOLDER + " messages per folder reached";
                                }
                            }

                            if (isSkip) {
                                skippedCount.incrementAndGet();
                                if (skipReason == null) {
                                    skipReason = filterEngine.getRejectionReason(msg);
                                }
                                writeCsvAudit(csvWriter, folderKey, msg, "SKIPPED", skipReason);
                            } else {
                                try {
                                    session.writeMessage(msg);
                                    successCount.incrementAndGet();
                                    folderSuccess++;
                                    writeCsvAudit(csvWriter, folderKey, msg, "SUCCESS", "Exported successfully");
                                } catch (Exception ex) {
                                    failedCount.incrementAndGet();
                                    writeCsvAudit(csvWriter, folderKey, msg, "FAILED", ex.getMessage());
                                }
                            }
                        }
                        session.close();
                    }

                    System.out.println("\n--------------------------------------------------------------------------------");
                    System.out.println("MIGRATION COMPLETED");
                    System.out.println("--------------------------------------------------------------------------------");
                    System.out.println("Total Processed: " + processedCount.get());
                    System.out.println("Success:         " + successCount.get());
                    System.out.println("Skipped:         " + skippedCount.get());
                    System.out.println("Failed:          " + failedCount.get());
                    System.out.println("Compliance CSV:  " + csvFile.getAbsolutePath());
                    System.out.println("================================================================================");
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] CLI migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void collectFolders(MailboxFolder folder, List<String> currentPath, List<FolderWithKey> list) {
        if (folder == null) return;
        List<String> newPath = new ArrayList<>(currentPath);
        newPath.add(folder.getName());
        list.add(new FolderWithKey(folder, newPath));
        if (folder.getChildren() != null) {
            for (MailboxFolder child : folder.getChildren()) {
                collectFolders(child, newPath, list);
            }
        }
    }

    private static synchronized void writeCsvAudit(PrintWriter writer, String folderPath, MailMessage msg, String status, String details) {
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date());
        String folder = folderPath != null ? escapeCsv(folderPath) : "";
        String sender = msg != null && msg.getFrom() != null ? escapeCsv(msg.getFrom()) : "";
        String subject = msg != null && msg.getSubject() != null ? escapeCsv(msg.getSubject()) : "";
        String msgId = msg != null && msg.getMessageId() != null ? escapeCsv(msg.getMessageId()) : "";
        String hash = msg != null ? msg.calculateSha256() : "";
        String stat = escapeCsv(status);
        String det = escapeCsv(details);
        writer.println(timestamp + "," + folder + "," + sender + "," + subject + "," + msgId + "," + hash + "," + stat + "," + det);
        writer.flush();
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        String clean = value.replace("\"", "\"\"");
        if (clean.contains(",") || clean.contains("\n") || clean.contains("\r") || clean.contains("\"")) {
            return "\"" + clean + "\"";
        }
        return clean;
    }

    private static Properties loadConfig(File file) throws IOException {
        Properties props = new Properties();
        if (file.getName().endsWith(".json")) {
            String content;
            try (FileInputStream fis = new FileInputStream(file)) {
                content = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            }
            parseJsonAndFlatten(content, "", props);
        } else {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            }
        }
        return props;
    }

    private static void parseJsonAndFlatten(String json, String prefix, Properties props) {
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1).trim();
        }
        
        int length = json.length();
        int braceCount = 0;
        int bracketCount = 0;
        boolean inQuote = false;
        StringBuilder currentToken = new StringBuilder();
        List<String> pairs = new ArrayList<>();
        
        for (int i = 0; i < length; i++) {
            char c = json.charAt(i);
            if (c == '\"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                else if (c == '[') bracketCount++;
                else if (c == ']') bracketCount--;
            }
            
            if (c == ',' && braceCount == 0 && bracketCount == 0 && !inQuote) {
                pairs.add(currentToken.toString());
                currentToken.setLength(0);
            } else {
                currentToken.append(c);
            }
        }
        if (currentToken.length() > 0) {
            pairs.add(currentToken.toString());
        }

        for (String pair : pairs) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2) {
                String key = cleanJsonToken(parts[0]);
                String value = parts[1].trim();
                String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                if (value.startsWith("{") && value.endsWith("}")) {
                    parseJsonAndFlatten(value, fullKey, props);
                } else if (value.startsWith("[") && value.endsWith("]")) {
                    String arrayContent = value.substring(1, value.length() - 1);
                    String[] items = arrayContent.split(",");
                    StringBuilder sb = new StringBuilder();
                    for (String item : items) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(cleanJsonToken(item));
                    }
                    props.setProperty(fullKey, sb.toString());
                } else {
                    props.setProperty(fullKey, cleanJsonToken(value));
                }
            }
        }
    }

    private static String cleanJsonToken(String token) {
        token = token.trim();
        if (token.startsWith("\"") && token.endsWith("\"")) {
            token = token.substring(1, token.length() - 1);
        }
        return token.trim();
    }
}

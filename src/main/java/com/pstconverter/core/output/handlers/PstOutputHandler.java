package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.util.DiagnosticLogger;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class PstOutputHandler implements OutputHandler {
    private static final AtomicInteger currentMonolithicPart = new AtomicInteger(1);

    @Override
    public void export(File targetFolder, List<MailMessage> emails) throws Exception {
        export(targetFolder, emails, "Keep Attachments in Folder");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling) throws Exception {
        export(targetFolder, emails, attachmentHandling, "Single Monolithic PST File (Entire Migration - Default)");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling, String exportStructure) throws Exception {
        export(targetFolder, emails, attachmentHandling, exportStructure, "Original Subject");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling, String exportStructure, String namingConvention) throws Exception {
        if (emails == null || emails.isEmpty()) return;

        try (Session session = openSession(targetFolder, attachmentHandling, exportStructure, namingConvention)) {
            for (MailMessage msg : emails) {
                session.writeMessage(msg);
            }
        }
    }

    @Override
    public Session openSession(File targetFolder, String attachmentHandling, String exportStructure, String namingConvention) throws Exception {
        boolean isMonolithic = exportStructure != null && exportStructure.equalsIgnoreCase("Single Monolithic PST File (Entire Migration - Default)");
        boolean isFolderPst = exportStructure != null && (exportStructure.equalsIgnoreCase("PST per Folder") || exportStructure.startsWith("Single Combined File"));
        boolean shouldEmbed = attachmentHandling != null && (attachmentHandling.contains("Embed Attachments") || attachmentHandling.contains("Keep Attachments"));

        // Retrieve split PST size settings
        String splitPstSetting = SettingsManager.getSetting("split_pst_size", "Do not split output");
        
        // Support System Properties for ValidatePstSplit tests
        String sysSplitEnabled = System.getProperty("active_export_split_pst");
        if (sysSplitEnabled != null) {
            splitPstSetting = sysSplitEnabled.equalsIgnoreCase("true") ? System.getProperty("active_export_split_size", "10 GB") : "Do not split output";
        }
        boolean isSplit = !splitPstSetting.equalsIgnoreCase("Do not split output");
        long limitBytes = 10L * 1024 * 1024 * 1024; // Default 10 GB
        if (isSplit) {
            String cleanSize = splitPstSetting.replace("limit", "").trim();
            limitBytes = parseSplitSize(cleanSize);
        }

        final boolean finalIsSplit = isSplit;
        final long finalLimitBytes = limitBytes;
        final String resolvedMonolithicPath = System.getProperty("active_export_output_path");

        return new Session() {
            private int index = 1;
            private int folderPart = 1;

            @Override
            public synchronized void writeMessage(MailMessage msg) throws Exception {
                long msgStartTime = System.nanoTime();
                File file;
                String relativeFolderInsidePst = "";

                if (isMonolithic) {
                    File rootDir = (resolvedMonolithicPath != null) ? new File(resolvedMonolithicPath) : targetFolder;
                    if (!rootDir.exists()) {
                        rootDir.mkdirs();
                    }
                    int activePart = currentMonolithicPart.get();
                    file = getSplitFile(rootDir, "PST_Export", activePart, finalIsSplit);
                    if (finalIsSplit) {
                        while (file.exists() && file.length() >= finalLimitBytes) {
                            activePart = currentMonolithicPart.incrementAndGet();
                            file = getSplitFile(rootDir, "PST_Export", activePart, finalIsSplit);
                        }
                    }
                    // For monolithic, get folder path from metadata
                    relativeFolderInsidePst = msg.getMetadata().get("Original-Folder-Path");
                } else if (isFolderPst) {
                    String baseName = targetFolder.getName();
                    file = getSplitFile(targetFolder, baseName, folderPart, finalIsSplit);
                    if (finalIsSplit) {
                        while (file.exists() && file.length() >= finalLimitBytes) {
                            folderPart++;
                            file = getSplitFile(targetFolder, baseName, folderPart, finalIsSplit);
                        }
                    }
                    // For folder-specific PST, we can also reconstruct subfolders if nested
                    relativeFolderInsidePst = msg.getMetadata().get("Original-Folder-Path");
                    if (relativeFolderInsidePst != null) {
                        // Extract path suffix starting from the targetFolder name onwards
                        int baseIdx = relativeFolderInsidePst.indexOf(baseName);
                        if (baseIdx != -1) {
                            relativeFolderInsidePst = relativeFolderInsidePst.substring(baseIdx);
                        }
                    } else {
                        relativeFolderInsidePst = baseName;
                    }
                } else {
                    // Individual PST file per email
                    String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "pst");
                    file = new File(targetFolder, fileName);
                    relativeFolderInsidePst = "Emails";
                }

                // Ensure parent directory exists
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                // Create or open the PST storage
                com.aspose.email.PersonalStorage pst = null;
                try {
                    if (file.exists()) {
                        pst = com.aspose.email.PersonalStorage.fromFile(file.getAbsolutePath());
                    } else {
                        pst = com.aspose.email.PersonalStorage.create(file.getAbsolutePath(), com.aspose.email.FileFormatVersion.Unicode);
                    }

                    // Locate or create the target subfolder inside the PST
                    com.aspose.email.FolderInfo pstFolder = getOrCreateFolderPath(pst, relativeFolderInsidePst);

                    // Create the MAPI Message
                    com.aspose.email.MapiMessage mapiMsg = createMapiMessage(msg, shouldEmbed);

                    // Add message to the folder
                    pstFolder.addMessage(mapiMsg);

                    double durationMs = (System.nanoTime() - msgStartTime) / 1_000_000.0;
                    DiagnosticLogger.logMessageSuccess(msg.getUniqueIdentifier(), msg.getSubject(), msg.getFrom(), msg.getDate(), relativeFolderInsidePst, durationMs);
                } catch (Exception ex) {
                    double durationMs = (System.nanoTime() - msgStartTime) / 1_000_000.0;
                    DiagnosticLogger.logMessageFailed(msg.getUniqueIdentifier(), msg.getSubject(), relativeFolderInsidePst, durationMs, ex.getMessage(), ex);
                    throw ex;
                } finally {
                    if (pst != null) {
                        try { pst.close(); } catch (Exception ignored) {}
                        try { pst.dispose(); } catch (Exception ignored) {}
                    }
                }

                // Save external attachments if requested
                if (attachmentHandling != null && !attachmentHandling.equalsIgnoreCase("Skip / Drop Attachments") && !attachmentHandling.contains("Embed Attachments")) {
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                }

                index++;
            }

            @Override
            public synchronized void close() throws Exception {
                // Since we open/close PersonalStorage on demand per writeMessage, nothing to do here
            }
        };
    }

    @Override
    public void cleanMonolithicFiles(File targetFolder, String exportStructure) {
        String rootPath = System.getProperty("active_export_output_path");
        File rootDir = (rootPath != null) ? new File(rootPath) : targetFolder;
        if (rootDir == null || !rootDir.exists()) return;

        boolean isMonolithic = exportStructure != null && exportStructure.equalsIgnoreCase("Single Monolithic PST File (Entire Migration - Default)");
        if (isMonolithic) {
            currentMonolithicPart.set(1);
            // Delete base file
            File file = new File(rootDir, "PST_Export.pst");
            if (file.exists()) {
                file.delete();
            }

            // Delete split files
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    if (name.startsWith("PST_Export_part") && name.endsWith(".pst")) {
                        f.delete();
                    }
                }
            }
        } else {
            // Recursive deletion of all .pst files inside rootDir
            deletePstFilesRecursively(rootDir);
        }
    }

    private void deletePstFilesRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deletePstFilesRecursively(child);
                }
            }
        } else {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".pst")) {
                file.delete();
            }
        }
    }

    private com.aspose.email.FolderInfo getOrCreateFolderPath(com.aspose.email.PersonalStorage pst, String folderPath) {
        com.aspose.email.FolderInfo current = pst.getRootFolder();
        if (folderPath == null || folderPath.trim().isEmpty()) {
            return current;
        }

        String[] parts = folderPath.split("/");
        for (String part : parts) {
            String cleanName = part.trim();
            if (cleanName.isEmpty()) continue;

            // Skip useless root display names to keep folders clean in Outlook
            if (cleanName.equalsIgnoreCase("Top of Personal Folders") ||
                cleanName.equalsIgnoreCase("Top of Outlook data file") ||
                cleanName.equalsIgnoreCase("IPM_SUBTREE")) {
                continue;
            }

            com.aspose.email.FolderInfo sub = current.getSubFolder(cleanName);
            if (sub == null) {
                sub = current.addSubFolder(cleanName);
            }
            current = sub;
        }
        return current;
    }

    private com.aspose.email.MapiMessage createMapiMessage(MailMessage msg, boolean embedAttachments) throws Exception {
        boolean exportSubject = Boolean.parseBoolean(SettingsManager.getSetting("export_field_subject", "true"));
        boolean exportFrom = Boolean.parseBoolean(SettingsManager.getSetting("export_field_from", "true"));
        boolean exportTo = Boolean.parseBoolean(SettingsManager.getSetting("export_field_to", "true"));
        boolean exportCcbcc = Boolean.parseBoolean(SettingsManager.getSetting("export_field_ccbcc", "true"));
        boolean exportDate = Boolean.parseBoolean(SettingsManager.getSetting("export_field_date", "true"));
        boolean exportBody = Boolean.parseBoolean(SettingsManager.getSetting("export_field_body", "true"));

        com.aspose.email.MailMessage aspMsg = new com.aspose.email.MailMessage();
        
        if (exportSubject) {
            aspMsg.setSubject(msg.getSubject());
        }

        // Set From
        if (exportFrom && msg.getFrom() != null && !msg.getFrom().trim().isEmpty()) {
            try {
                aspMsg.setFrom(new com.aspose.email.MailAddress(msg.getFrom()));
            } catch (Exception e) {
                // Fallback for malformed email
                aspMsg.setFrom(new com.aspose.email.MailAddress("sender@example.com", msg.getFrom()));
            }
        }

        // Set To
        if (exportTo && msg.getTo() != null && !msg.getTo().trim().isEmpty()) {
            addRecipients(aspMsg.getTo(), msg.getTo());
        }

        // Set CC
        if (exportCcbcc && msg.getCc() != null && !msg.getCc().trim().isEmpty()) {
            addRecipients(aspMsg.getCc(), msg.getCc());
        }

        // Set BCC
        if (exportCcbcc && msg.getBcc() != null && !msg.getBcc().trim().isEmpty()) {
            addRecipients(aspMsg.getBcc(), msg.getBcc());
        }

        // Set Date
        if (exportDate && msg.getDate() != null && !msg.getDate().trim().isEmpty()) {
            Date parsedDate = parseMailDate(msg.getDate());
            if (parsedDate != null) {
                aspMsg.setDate(parsedDate);
            }
        }

        // Set HTML / Plain Text body
        if (exportBody) {
            String rawBody = msg.getBody();
            if (rawBody != null) {
                String lowerBody = rawBody.toLowerCase();
                if (lowerBody.contains("<html") || lowerBody.contains("<body") || lowerBody.contains("<p") || lowerBody.contains("<br")) {
                    aspMsg.setHtmlBody(rawBody);
                    aspMsg.setBody(OutputHandler.getPlainTextBody(rawBody));
                } else {
                    aspMsg.setBody(OutputHandler.getPlainTextBody(rawBody));
                }
            }
        }

        // Set Message ID
        if (msg.getMessageId() != null && !msg.getMessageId().trim().isEmpty()) {
            aspMsg.setMessageId(msg.getMessageId());
        }

        // Set Importance
        String imp = msg.getImportance();
        if (imp != null) {
            if (imp.equalsIgnoreCase("High")) {
                aspMsg.setPriority(com.aspose.email.MailPriority.High);
            } else if (imp.equalsIgnoreCase("Low")) {
                aspMsg.setPriority(com.aspose.email.MailPriority.Low);
            } else {
                aspMsg.setPriority(com.aspose.email.MailPriority.Normal);
            }
        }

        // Embed attachments if requested
        if (embedAttachments && msg.getAttachmentList() != null && !msg.getAttachmentList().isEmpty()) {
            for (MailMessage.Attachment att : msg.getAttachmentList()) {
                byte[] data = att.getData();
                if (data != null && data.length > 0) {
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                        com.aspose.email.Attachment aspAtt = new com.aspose.email.Attachment(bais, att.getFilename());
                        aspMsg.getAttachments().addItem(aspAtt);
                    } catch (Exception ex) {
                        System.err.println("Failed to embed attachment: " + att.getFilename() + " | Error: " + ex.getMessage());
                    }
                }
            }
        }

        // Convert to MapiMessage
        com.aspose.email.MapiMessage mapiMsg = com.aspose.email.MapiMessage.fromMailMessage(aspMsg);

        // Map read/unread flags
        long flags = 0;
        if (msg.getStatus() != null && msg.getStatus().equalsIgnoreCase("Read")) {
            flags |= com.aspose.email.MapiMessageFlags.MSGFLAG_READ;
        }
        mapiMsg.setMessageFlags(flags);

        // Set Message Class based on type (Mail, Contact, Calendar, Task, Note)
        String itemType = msg.getItemType();
        if (itemType != null) {
            if (itemType.equalsIgnoreCase("Contact")) {
                mapiMsg.setMessageClass("IPM.Contact");
            } else if (itemType.equalsIgnoreCase("Calendar")) {
                mapiMsg.setMessageClass("IPM.Appointment");
            } else if (itemType.equalsIgnoreCase("Task")) {
                mapiMsg.setMessageClass("IPM.Task");
            } else if (itemType.equalsIgnoreCase("Note")) {
                mapiMsg.setMessageClass("IPM.StickyNote");
            } else {
                mapiMsg.setMessageClass("IPM.Note");
            }
        }

        return mapiMsg;
    }

    private void addRecipients(com.aspose.email.system.collections.generic.IGenericCollection<com.aspose.email.MailAddress> collection, String addressStr) {
        if (addressStr == null || addressStr.trim().isEmpty()) return;
        String[] parts = addressStr.split("[,;]");
        for (String part : parts) {
            String clean = part.trim();
            if (!clean.isEmpty()) {
                try {
                    collection.addItem(new com.aspose.email.MailAddress(clean));
                } catch (Exception ex) {
                    // Fallback for malformed email addresses in list
                    try {
                        collection.addItem(new com.aspose.email.MailAddress("recipient@example.com", clean));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private Date parseMailDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return new Date();
        String[] patterns = {
            "EEE, d MMM yyyy HH:mm:ss Z",
            "d MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "EEE MMM dd HH:mm:ss yyyy",
            "EEE MMM d HH:mm:ss yyyy"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat df = new SimpleDateFormat(pattern, Locale.US);
                return df.parse(dateStr);
            } catch (Exception ignored) {}
        }
        return new Date();
    }

    private long parseSplitSize(String sizeStr) {
        if (sizeStr == null || sizeStr.trim().isEmpty()) {
            return 10L * 1024 * 1024 * 1024; // Default 10 GB
        }
        String clean = sizeStr.trim().toUpperCase();
        long multiplier = 1024L * 1024 * 1024; // Default GB
        if (clean.endsWith("MB")) {
            multiplier = 1024L * 1024;
            clean = clean.substring(0, clean.length() - 2).trim();
        } else if (clean.endsWith("GB")) {
            multiplier = 1024L * 1024 * 1024;
            clean = clean.substring(0, clean.length() - 2).trim();
        } else if (clean.endsWith("KB")) {
            multiplier = 1024L;
            clean = clean.substring(0, clean.length() - 2).trim();
        }
        try {
            double val = Double.parseDouble(clean);
            return (long) (val * multiplier);
        } catch (NumberFormatException e) {
            return 10L * 1024 * 1024 * 1024; // Fallback 10 GB
        }
    }

    private File getSplitFile(File directory, String baseName, int part, boolean isSplit) {
        if (!isSplit) {
            return new File(directory, baseName + ".pst");
        }
        return new File(directory, baseName + "_part" + part + ".pst");
    }
}

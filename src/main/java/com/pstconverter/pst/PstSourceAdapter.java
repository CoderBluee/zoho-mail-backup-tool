package com.pstconverter.pst;

import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTContact;
import com.pff.PSTAppointment;
import com.pff.PSTTask;
import com.pff.PSTAttachment;
import com.pstconverter.core.adapter.SourceAdapter;
import com.pstconverter.model.MailboxFolder;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import com.chilkatsoft.CkEmail;
import com.chilkatsoft.CkByteData;


/**
 * SourceAdapter implementation for Outlook PST files.
 * Uses java-libpst to parse the folder structures and extract emails.
 */
public class PstSourceAdapter implements SourceAdapter {

    private static final java.util.Map<String, PSTFile> openPstFiles = new java.util.concurrent.ConcurrentHashMap<>();

    private static synchronized PSTFile getOrCreatePstFile(File file) throws Exception {
        String path = file.getAbsolutePath();
        PSTFile pstFile = openPstFiles.get(path);
        if (pstFile == null) {
            pstFile = new PSTFile(path);
            openPstFiles.put(path, pstFile);
            System.out.println("[INFO] PstSourceAdapter: Opened and cached PST file: " + path);
        }
        return pstFile;
    }

    public static void clearCache() {
        for (String path : new java.util.ArrayList<>(openPstFiles.keySet())) {
            PSTFile pstFile = openPstFiles.remove(path);
            if (pstFile != null) {
                try {
                    pstFile.close();
                    System.out.println("[INFO] PstSourceAdapter: Closed and released PST file lock: " + path);
                } catch (Exception ignored) {}
            }
        }
        openPstFiles.clear();
    }

    @Override
    public boolean canParse(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".pst");
    }

    @Override
    public boolean validateFile(File file) {
        if (file == null || !file.exists()) return false;
        PSTFile pstFile = null;
        try {
            pstFile = new PSTFile(file.getAbsolutePath());
            return pstFile.getRootFolder() != null;
        } catch (Exception e) {
            return false;
        } finally {
            if (pstFile != null) {
                try {
                    pstFile.close();
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public MailboxFolder parseFolderStructure(File file, BiConsumer<String, String> progressCallback) throws Exception {
        PSTFile pstFile = null;
        try {
            pstFile = new PSTFile(file.getAbsolutePath());
            PSTFolder rootFolder = pstFile.getRootFolder();
            if (rootFolder == null) {
                return null;
            }

            MailboxFolder rootNode = new MailboxFolder(file.getName(), 0, false);

            Vector<PSTFolder> rootSubFolders = rootFolder.getSubFolders();
            if (rootSubFolders != null) {
                for (PSTFolder sub : rootSubFolders) {
                    String subName = sub.getDisplayName();
                    if (isUselessSystemFolder(subName)) {
                        continue;
                    }

                    MailboxFolder subNode = new MailboxFolder(subName, sub.getContentCount(), isOutlookSystemFolder(subName));
                    addPstFolderNodes(subNode, sub);
                    rootNode.addChild(subNode);
                }
            }
            if ("true".equals(com.pstconverter.util.SettingsManager.getSetting("ignore_empty_folders", "false"))) {
                pruneEmptyFolders(rootNode);
            }
            return rootNode;
        } finally {
            if (pstFile != null) {
                try {
                    pstFile.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private void addPstFolderNodes(MailboxFolder parentNode, PSTFolder pstFolder) {
        try {
            Vector<PSTFolder> subFolders = pstFolder.getSubFolders();
            if (subFolders != null && !subFolders.isEmpty()) {
                for (PSTFolder subFolder : subFolders) {
                    String subName = subFolder.getDisplayName();
                    if (isUselessSystemFolder(subName)) {
                        continue;
                    }
                    MailboxFolder subFolderNode = new MailboxFolder(subName, subFolder.getContentCount(), isOutlookSystemFolder(subName));
                    addPstFolderNodes(subFolderNode, subFolder);
                    parentNode.addChild(subFolderNode);
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Error in addPstFolderNodes: " + e.getMessage());
        }
    }

    private boolean pruneEmptyFolders(MailboxFolder node) {
        java.util.List<MailboxFolder> kids = new java.util.ArrayList<>(node.getChildren());
        for (MailboxFolder child : kids) {
            boolean isEmpty = pruneEmptyFolders(child);
            if (isEmpty) {
                node.getChildren().remove(child);
            }
        }
        return node.getContentCount() == 0 && node.getChildren().isEmpty();
    }

    @Override
    public List<MailMessage> getEmails(File file, List<String> folderPath) throws Exception {
        return getEmails(file, folderPath, 0, -1);
    }

    @Override
    public List<MailMessage> getEmails(File file, List<String> folderPath, int offset, int limit) throws Exception {
        List<MailMessage> emails = new ArrayList<>();
        streamEmails(file, folderPath, emails::add, offset, limit);
        return emails;
    }

    @Override
    public void streamEmails(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer) throws Exception {
        streamEmails(file, folderPath, consumer, 0, -1);
    }

    @Override
    public void streamEmailsMetadata(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer) throws Exception {
        streamEmailsMetadata(file, folderPath, consumer, 0, -1);
    }

    @Override
    public void streamEmails(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int limit) throws Exception {
        streamEmails(file, folderPath, consumer, 0, limit);
    }

    @Override
    public void streamEmailsMetadata(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int limit) throws Exception {
        streamEmailsMetadata(file, folderPath, consumer, 0, limit);
    }

    @Override
    public void streamEmailsMetadata(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int offset, int limit) throws Exception {
        streamEmailsMetadata(file, folderPath, consumer, offset, limit, null);
    }

    @Override
    public void streamEmailsMetadata(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int offset, int limit, java.util.function.Predicate<String> skipCheck) throws Exception {
        PSTFile pstFile = getOrCreatePstFile(file);
        synchronized (pstFile) {
            PSTFolder folder = pstFile.getRootFolder();

            for (String folderName : folderPath) {
                PSTFolder nextFolder = findSubFolder(folder, folderName);
                if (nextFolder == null) {
                    Vector<PSTFolder> subs = folder.getSubFolders();
                    for (PSTFolder sub : subs) {
                        if (sub.getDisplayName().equalsIgnoreCase("Top of Personal Folders") ||
                                sub.getDisplayName().equalsIgnoreCase("Top of Outlook data file") ||
                                sub.getDisplayName().equalsIgnoreCase("IPM_SUBTREE")) {
                            nextFolder = findSubFolder(sub, folderName);
                            if (nextFolder != null)
                                break;
                        }
                    }
                }
                if (nextFolder != null) {
                    folder = nextFolder;
                } else {
                    folder = null;
                    break;
                }
            }

            if (folder != null && folder.getContentCount() > 0) {
                java.util.Map<String, Integer> seenCounts = new java.util.HashMap<>();
                PSTMessage msg = null;

                // Skip the first 'offset' messages sequentially to advance internal pointer
                int skipped = 0;
                while (skipped < offset) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    try {
                        msg = (PSTMessage) folder.getNextChild();
                        if (msg == null) {
                            break;
                        }
                        getPstMessageUniqueIdentifier(msg, seenCounts);
                    } catch (Exception ex) {
                        System.err.println("Error skipping child message: " + ex.getMessage());
                    }
                    skipped++;
                }

                // If we skipped exactly offset items, check if we need to retrieve the next child
                if (skipped == offset) {
                    try {
                        msg = (PSTMessage) folder.getNextChild();
                    } catch (Exception ex) {
                        System.err.println("Error reading next child message after offset skip: " + ex.getMessage());
                        msg = null;
                    }
                } else {
                    msg = null;
                }

                int count = 0;
                while (msg != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    if (limit > 0 && count >= limit) {
                        break;
                    }
                    try {
                        String uniqueId = getPstMessageUniqueIdentifier(msg, seenCounts);
                        MailMessage mailMsg = parsePstMessageMetadata(msg, uniqueId);
                        if (mailMsg != null) {
                            consumer.accept(mailMsg);
                            count++;
                        }
                    } catch (Exception ex) {
                        System.err.println("Warning: Skipped malformed email metadata: " + ex.getMessage());
                        MailMessage failedMail = new MailMessage("Unknown Sender", "(No Subject)", "Unknown Date", "");
                        consumer.accept(failedMail);
                        count++;
                    }

                    try {
                        msg = (PSTMessage) folder.getNextChild();
                    } catch (Exception ex) {
                        System.err.println("Error reading next child message: " + ex.getMessage());
                        msg = null;
                    }
                }
            }
        }
    }

    @Override
    public void streamEmails(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int offset, int limit) throws Exception {
        streamEmails(file, folderPath, consumer, offset, limit, null);
    }

    @Override
    public void streamEmails(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int offset, int limit, java.util.function.Predicate<String> skipCheck) throws Exception {
        PSTFile pstFile = getOrCreatePstFile(file);
        synchronized (pstFile) {
            PSTFolder folder = pstFile.getRootFolder();

            for (String folderName : folderPath) {
                PSTFolder nextFolder = findSubFolder(folder, folderName);
                if (nextFolder == null) {
                    Vector<PSTFolder> subs = folder.getSubFolders();
                    for (PSTFolder sub : subs) {
                        if (sub.getDisplayName().equalsIgnoreCase("Top of Personal Folders") ||
                                sub.getDisplayName().equalsIgnoreCase("Top of Outlook data file") ||
                                sub.getDisplayName().equalsIgnoreCase("IPM_SUBTREE")) {
                            nextFolder = findSubFolder(sub, folderName);
                            if (nextFolder != null)
                                break;
                        }
                    }
                }
                if (nextFolder != null) {
                    folder = nextFolder;
                } else {
                    folder = null;
                    break;
                }
            }

            if (folder != null && folder.getContentCount() > 0) {
                java.util.Map<String, Integer> seenCounts = new java.util.HashMap<>();
                PSTMessage msg = null;

                // Skip the first 'offset' messages sequentially to advance internal pointer
                int skipped = 0;
                while (skipped < offset) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    try {
                        msg = (PSTMessage) folder.getNextChild();
                        if (msg == null) {
                            break;
                        }
                        getPstMessageUniqueIdentifier(msg, seenCounts);
                    } catch (Exception ex) {
                        System.err.println("Error skipping child message: " + ex.getMessage());
                    }
                    skipped++;
                }

                // If we skipped exactly offset items, check if we need to retrieve the next child
                if (skipped == offset) {
                    try {
                        msg = (PSTMessage) folder.getNextChild();
                    } catch (Exception ex) {
                        System.err.println("Error reading next child message after offset skip: " + ex.getMessage());
                        msg = null;
                    }
                } else {
                    msg = null;
                }

                int count = 0;
                while (msg != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    if (limit > 0 && count >= limit) {
                        break;
                    }
                    try {
                        String uniqueId = getPstMessageUniqueIdentifier(msg, seenCounts);
                        if (skipCheck != null && skipCheck.test(uniqueId)) {
                            // Already migrated: parse lightweight metadata only to save time and memory
                            MailMessage mailMsg = parsePstMessageMetadata(msg, uniqueId);
                            if (mailMsg != null) {
                                consumer.accept(mailMsg);
                                count++;
                            }
                        } else {
                            // Parse full message details
                            MailMessage mailMsg = parsePstMessage(msg, uniqueId);
                            if (mailMsg != null) {
                                consumer.accept(mailMsg);
                                count++;
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Warning: Skipped malformed email: " + ex.getMessage());
                        MailMessage failedMail = new MailMessage("Unknown", "Malformed Email (Failed to parse)", "Unknown", "Malformed Content", "Mail");
                        try {
                            String subject = msg.getSubject();
                            if (subject != null && !subject.isEmpty()) {
                                failedMail = new MailMessage("Unknown", subject, "Unknown", "Malformed Content", "Mail");
                            }
                        } catch (Exception ignored) {}
                        failedMail.addMetadata("Error-Reason", "Parsing exception: " + ex.getMessage());
                        consumer.accept(failedMail);
                        count++;
                    }

                    try {
                        msg = (PSTMessage) folder.getNextChild();
                    } catch (Exception ex) {
                        System.err.println("Error reading next child message: " + ex.getMessage());
                        MailMessage failedMail = new MailMessage("Unknown", "Corrupted PST Item (Failed to read)", "Unknown", "Corrupted PST Item Content", "Mail");
                        failedMail.addMetadata("Error-Reason", "Corrupted PST entry: " + ex.getMessage());
                        consumer.accept(failedMail);
                        count++;
                        msg = null;
                    }
                }
            }
        }
    }

    public String getPstMessageUniqueIdentifier(PSTMessage msg, java.util.Map<String, Integer> seenCounts) {
        String msgId = null;
        try {
            msgId = msg.getInternetMessageId();
        } catch (Exception ignored) {}
        
        if (msgId == null || msgId.trim().isEmpty()) {
            String senderName = null;
            try { senderName = msg.getSenderName(); } catch (Exception ignored) {}
            String senderEmail = null;
            try { senderEmail = msg.getSenderEmailAddress(); } catch (Exception ignored) {}
            String from;
            if (senderName != null && !senderName.isEmpty() && senderEmail != null && !senderEmail.isEmpty() && !senderName.equalsIgnoreCase(senderEmail)) {
                from = senderName + " <" + senderEmail + ">";
            } else if (senderName != null && !senderName.isEmpty()) {
                from = senderName;
            } else if (senderEmail != null && !senderEmail.isEmpty()) {
                from = senderEmail;
            } else {
                from = "Unknown Sender";
            }
            
            String subject = "(No Subject)";
            try {
                String s = msg.getSubject();
                if (s != null && !s.isEmpty()) {
                    subject = s;
                }
            } catch (Exception ignored) {}
            
            String date = "Unknown Date";
            try {
                if (msg.getMessageDeliveryTime() != null) {
                    date = msg.getMessageDeliveryTime().toString();
                }
            } catch (Exception ignored) {}
            
            String messageClass = "";
            try { if (msg.getMessageClass() != null) messageClass = msg.getMessageClass().toLowerCase(); } catch (Exception ignored) {}

            String itemType = "Mail";
            if (msg instanceof PSTContact || messageClass.contains("ipm.contact") || messageClass.contains("contact")) {
                itemType = "Contact";
            } else if (msg instanceof PSTAppointment || messageClass.contains("ipm.appointment") || messageClass.contains("ipm.schedule") || messageClass.contains("appointment")) {
                itemType = "Calendar";
            } else if (msg instanceof PSTTask || messageClass.contains("ipm.task") || messageClass.contains("task")) {
                itemType = "Task";
            } else if (messageClass.contains("stickynote") || messageClass.contains("memo") || messageClass.contains("ipm.stickynote") || messageClass.contains("note")) {
                itemType = "Note";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append(from).append("|").append(subject).append("|").append(date).append("|").append(itemType);
            msgId = "fallback_" + Math.abs(sb.toString().hashCode());
        }
        
        int seenIndex = seenCounts.getOrDefault(msgId, 0);
        seenCounts.put(msgId, seenIndex + 1);
        if (seenIndex > 0) {
            msgId = msgId + "-dup-" + seenIndex;
        }
        return msgId;
    }

    private MailMessage parsePstMessageMetadata(PSTMessage msg, String msgId) throws Exception {
        String senderName = null;
        try { senderName = msg.getSenderName(); } catch (Exception ignored) {}
        
        String senderEmail = null;
        try { senderEmail = msg.getSenderEmailAddress(); } catch (Exception ignored) {}
        
        String from;
        if (senderName != null && !senderName.isEmpty() && senderEmail != null && !senderEmail.isEmpty() && !senderName.equalsIgnoreCase(senderEmail)) {
            from = senderName + " <" + senderEmail + ">";
        } else if (senderName != null && !senderName.isEmpty()) {
            from = senderName;
        } else if (senderEmail != null && !senderEmail.isEmpty()) {
            from = senderEmail;
        } else {
            from = "Unknown Sender";
        }
        
        String subject = "(No Subject)";
        try {
            String s = msg.getSubject();
            if (s != null && !s.isEmpty()) {
                subject = s;
            }
        } catch (Exception ignored) {}
        
        String date = "Unknown Date";
        try {
            if (msg.getMessageDeliveryTime() != null) {
                date = msg.getMessageDeliveryTime().toString();
            }
        } catch (Exception ignored) {}
        
        String body = "";
        String itemType = "Mail";
        
        MailMessage mailMsg = new MailMessage(from, subject, date, body, itemType);
        try {
            String displayTo = msg.getDisplayTo();
            if (displayTo != null && !displayTo.isEmpty()) {
                mailMsg.addMetadata("To", displayTo);
                mailMsg.setTo(displayTo);
            }
        } catch (Exception ignored) {}
        
        try {
            String senderAddress = msg.getSenderEmailAddress();
            if (senderAddress != null && !senderAddress.isEmpty()) {
                mailMsg.addMetadata("Sender-Address", senderAddress);
                mailMsg.setSenderAddress(senderAddress);
            }
        } catch (Exception ignored) {}
        
        mailMsg.addMetadata("Message-ID", msgId);
        mailMsg.setMessageId(msgId);

        return mailMsg;
    }

    private MailMessage parsePstMessage(PSTMessage msg, String msgId) throws Exception {
        return parsePstMessage(msg, msgId, false);
    }

    private MailMessage parsePstMessage(PSTMessage msg, String msgId, boolean isEmbedded) throws Exception {
        String senderName = null;
        try { senderName = msg.getSenderName(); } catch (Exception ignored) {}
        
        String senderEmail = null;
        try { senderEmail = msg.getSenderEmailAddress(); } catch (Exception ignored) {}
        
        String from;
        if (senderName != null && !senderName.isEmpty() && senderEmail != null && !senderEmail.isEmpty() && !senderName.equalsIgnoreCase(senderEmail)) {
            from = senderName + " <" + senderEmail + ">";
        } else if (senderName != null && !senderName.isEmpty()) {
            from = senderName;
        } else if (senderEmail != null && !senderEmail.isEmpty()) {
            from = senderEmail;
        } else {
            from = "Unknown Sender";
        }
        
        String subject = null;
        try { subject = msg.getSubject(); } catch (Exception ignored) {}
        if (subject == null || subject.isEmpty()) {
            subject = "(No Subject)";
        }

        String date = "Unknown Date";
        try {
            if (msg.getMessageDeliveryTime() != null) {
                date = msg.getMessageDeliveryTime().toString();
            }
        } catch (Exception ignored) {}
        
        String body = null;
        try { body = msg.getBodyHTML(); } catch (Exception ignored) {}
        if (body == null || body.trim().isEmpty() || body.replaceAll("<[^>]*>", "").trim().isEmpty()) {
            try { body = msg.getBody(); } catch (Exception ignored) {}
        }
        
        // Handle empty body / attachment-only emails
        if (body == null || body.trim().isEmpty()) {
            int numAtt = 0;
            try { numAtt = msg.getNumberOfAttachments(); } catch (Exception ignored) {}
            if (numAtt > 0) {
                body = "(Attachment-only email. No message body content.)";
            } else {
                body = "(No content)";
            }
        }

        String messageClass = "";
        try { if (msg.getMessageClass() != null) messageClass = msg.getMessageClass().toLowerCase(); } catch (Exception ignored) {}

        String itemType = "Mail";
        if (msg instanceof PSTContact || messageClass.contains("ipm.contact") || messageClass.contains("contact")) {
            itemType = "Contact";
        } else if (msg instanceof PSTAppointment || messageClass.contains("ipm.appointment") || messageClass.contains("ipm.schedule") || messageClass.contains("appointment")) {
            itemType = "Calendar";
        } else if (msg instanceof PSTTask || messageClass.contains("ipm.task") || messageClass.contains("task")) {
            itemType = "Task";
        } else if (messageClass.contains("stickynote") || messageClass.contains("memo") || messageClass.contains("ipm.stickynote") || messageClass.contains("note")) {
            itemType = "Note";
        }

        MailMessage mailMsg = new MailMessage(from, subject, date, body, itemType);
        try {
            String displayTo = msg.getDisplayTo();
            if (displayTo != null && !displayTo.isEmpty()) {
                mailMsg.addMetadata("To", displayTo);
                mailMsg.setTo(displayTo);
            }
        } catch (Exception ignored) {}
        try {
            String senderAddress = msg.getSenderEmailAddress();
            if (senderAddress != null && !senderAddress.isEmpty()) {
                mailMsg.addMetadata("Sender-Address", senderAddress);
                mailMsg.setSenderAddress(senderAddress);
            }
        } catch (Exception ignored) {}
        try {
            String cc = msg.getDisplayCC();
            if (cc != null && !cc.isEmpty()) {
                mailMsg.addMetadata("Cc", cc);
                mailMsg.setCc(cc);
            }
        } catch (Exception ignored) {}
        try {
            String bcc = msg.getDisplayBCC();
            if (bcc != null && !bcc.isEmpty()) {
                mailMsg.addMetadata("Bcc", bcc);
                mailMsg.setBcc(bcc);
            }
        } catch (Exception ignored) {}
        
        mailMsg.addMetadata("Message-ID", msgId);
        mailMsg.setMessageId(msgId);

        try {
            String statusStr = msg.isRead() ? "Read" : "Unread";
            mailMsg.addMetadata("Status", statusStr);
            mailMsg.setStatus(statusStr);
        } catch (Exception ignored) {}
        try {
            int imp = msg.getImportance();
            String importance = (imp == 2) ? "High" : (imp == 0 ? "Low" : "Normal");
            mailMsg.addMetadata("Importance", importance);
            mailMsg.setImportance(importance);
        } catch (Exception ignored) {}
        try {
            List<String> flagsList = new ArrayList<>();
            if (msg.hasReplied()) flagsList.add("Replied");
            if (msg.hasForwarded()) flagsList.add("Forwarded");
            if (msg.isFlagged()) flagsList.add("Flagged");
            String flagsStr = flagsList.isEmpty() ? "None" : String.join(", ", flagsList);
            mailMsg.addMetadata("Message-Flags", flagsStr);
            mailMsg.setMessageFlags(flagsStr);
        } catch (Exception ignored) {}

        // Add attachments
        int attachmentsCount = 0;
        try { attachmentsCount = msg.getNumberOfAttachments(); } catch (Exception ignored) {}
        boolean deepScan = "true".equals(com.pstconverter.util.SettingsManager.getSetting("deep_attachment_scan", "true"));
        if (isEmbedded && !deepScan) {
            attachmentsCount = 0;
        }
        
        for (int i = 0; i < attachmentsCount; i++) {
            try {
                PSTAttachment attach = msg.getAttachment(i);
                if (attach != null) {
                    String filename = attach.getLongFilename();
                    if (filename == null || filename.isEmpty()) {
                        filename = attach.getFilename();
                    }
                    if (filename == null || filename.isEmpty()) {
                        filename = "Attachment_" + (i + 1);
                    }

                    // Check for embedded message (method 5)
                    try {
                        if (attach.getAttachMethod() == 5) {
                            String embSubj = "EmbeddedMessage";
                            try {
                                PSTMessage embeddedMsg = attach.getEmbeddedPSTMessage();
                                if (embeddedMsg != null) {
                                    String subjectVal = embeddedMsg.getSubject();
                                    if (subjectVal != null && !subjectVal.isEmpty()) {
                                        embSubj = subjectVal;
                                    }
                                }
                            } catch (Exception ignored) {}
                            String cleanEmbSubj = embSubj.replaceAll("[\\\\/:*?\"<>|]", "_") + ".eml";

                            // Eagerly load embedded message data to be thread-safe in message-parallel mode
                            byte[] embData = new byte[0];
                            try {
                                PSTMessage embeddedMsg = attach.getEmbeddedPSTMessage();
                                if (embeddedMsg != null) {
                                    String embMsgId = msgId + "-emb-" + i;
                                    MailMessage embeddedMail = parsePstMessage(embeddedMsg, embMsgId, true);
                                    embData = convertToEmlBytes(embeddedMail);
                                }
                            } catch (Exception ex) {
                                System.err.println("Warning: Failed to parse embedded message eagerly: " + ex.getMessage());
                            }
                            mailMsg.addAttachment(cleanEmbSubj, embData);
                            continue;
                        }
                    } catch (Exception ex) {
                        System.err.println("Warning: Failed to parse embedded message check: " + ex.getMessage());
                    }

                    // Check for oversized attachment (limit 50MB)
                    long attachSize = 0;
                    try { attachSize = attach.getSize(); } catch (Exception ignored) {}
                    if (attachSize > 50 * 1024 * 1024) {
                        String txtName = filename + "_OVERSIZED.txt";
                        byte[] warningData = ("The attachment '" + filename + "' was skipped because its size (" + (attachSize / (1024 * 1024)) + " MB) exceeded the 50 MB limit.").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        mailMsg.addAttachment(txtName, warningData);
                        System.out.println("[WARNING] Skipped oversized attachment: " + filename + " (" + (attachSize / (1024 * 1024)) + " MB)");
                        continue;
                    }

                    // Eagerly load attachment data to prevent Stream Closed / cross-thread access errors
                    byte[] attachData = new byte[0];
                    try (java.io.InputStream is = attach.getFileInputStream()) {
                        if (is != null) {
                            attachData = is.readAllBytes();
                        }
                    } catch (Exception ex) {
                        System.err.println("Warning: Could not read attachment stream eagerly: " + ex.getMessage());
                    }
                    mailMsg.addAttachment(filename, attachData);
                }
            } catch (Exception e) {
                System.err.println("Warning: Error reading attachment " + i + ": " + e.getMessage());
            }
        }

        // Add metadata based on item type
        try {
            if (msg instanceof PSTContact) {
                PSTContact contact = (PSTContact) msg;
                String fullName = contact.getDisplayName();
                String company = contact.getCompanyName();
                String jobTitle = contact.getTitle();
                String email = contact.getEmail1EmailAddress();
                String phone = contact.getBusinessTelephoneNumber();
                String mobile = contact.getMobileTelephoneNumber();

                if (fullName != null && !fullName.isEmpty()) {
                    mailMsg.addMetadata("Full Name", fullName);
                }
                if (company != null && !company.isEmpty()) {
                    mailMsg.addMetadata("Company", company);
                }
                if (jobTitle != null && !jobTitle.isEmpty()) {
                    mailMsg.addMetadata("Job Title", jobTitle);
                }
                if (email != null && !email.isEmpty()) {
                    mailMsg.addMetadata("Email Address", email);
                }
                if (phone != null && !phone.isEmpty()) {
                    mailMsg.addMetadata("Business Phone", phone);
                }
                if (mobile != null && !mobile.isEmpty()) {
                    mailMsg.addMetadata("Mobile Phone", mobile);
                }
            } else if (msg instanceof PSTAppointment) {
                PSTAppointment appt = (PSTAppointment) msg;
                java.util.Date startTime = appt.getStartTime();
                java.util.Date endTime = appt.getEndTime();
                String location = appt.getLocation();
                String organizer = appt.getSenderName();
                if (organizer == null || organizer.isEmpty()) {
                    organizer = appt.getNetMeetingOrganizerAlias();
                }
                int statusInt = appt.getMeetingStatus();
                String status = (statusInt == 1) ? "Organizer" : (statusInt == 3 ? "Attendee" : "Appointment");

                if (startTime != null) {
                    mailMsg.addMetadata("Start Time", startTime.toString());
                }
                if (endTime != null) {
                    mailMsg.addMetadata("End Time", endTime.toString());
                }
                if (location != null && !location.isEmpty()) {
                    mailMsg.addMetadata("Location", location);
                }
                if (organizer != null && !organizer.isEmpty()) {
                    mailMsg.addMetadata("Organizer", organizer);
                }
                mailMsg.addMetadata("Status", status);
            } else if (msg instanceof PSTTask) {
                PSTTask task = (PSTTask) msg;
                java.util.Date startDate = task.getTaskStartDate();
                java.util.Date dueDate = task.getTaskDueDate();
                int statusInt = task.getTaskStatus();
                String status = "Not Started";
                switch (statusInt) {
                    case 0: status = "Not Started"; break;
                    case 1: status = "In Progress"; break;
                    case 2: status = "Complete"; break;
                    case 3: status = "Waiting on someone else"; break;
                    case 4: status = "Deferred"; break;
                }
                int priorityInt = task.getImportance();
                String priority = (priorityInt == 2) ? "High" : (priorityInt == 0 ? "Low" : "Normal");

                if (startDate != null) {
                    mailMsg.addMetadata("Start Date", startDate.toString());
                }
                if (dueDate != null) {
                    mailMsg.addMetadata("Due Date", dueDate.toString());
                }
                mailMsg.addMetadata("Status", status);
                mailMsg.addMetadata("Priority", priority);
            }
        } catch (Exception e) {
            System.err.println("Warning: Error extracting metadata for " + itemType + ": " + e.getMessage());
        }

        return mailMsg;
    }

    private byte[] convertToEmlBytes(MailMessage message) {
        com.chilkatsoft.CkEmail ckEmail = null;
        com.chilkatsoft.CkByteData byteData = null;
        try {
            ckEmail = com.pstconverter.core.destination.DestinationAdapter.createCkEmail(message, "sender@example.com");
            byteData = new com.chilkatsoft.CkByteData();
            ckEmail.GetMimeBinary(byteData);
            return byteData.toByteArray();
        } catch (Exception e) {
            System.err.println("Failed to convert embedded message to EML bytes: " + e.getMessage());
            return new byte[0];
        } finally {
            if (byteData != null) {
                byteData.delete();
            }
            if (ckEmail != null) {
                ckEmail.delete();
            }
        }
    }


    @Override
    public String getSourceType() {
        return "PST";
    }

    @Override
    public List<String> getSupportedItemTypes() {
        return List.of("Emails", "Calendar Items", "Contacts", "Tasks", "Notes", "Journal Entries");
    }

    @Override
    public String getDisplayName() {
        return "Outlook PST File";
    }

    @Override
    public void releaseResources(File file) {
        if (file == null) return;
        String path = file.getAbsolutePath();
        PSTFile pstFile = openPstFiles.remove(path);
        if (pstFile != null) {
            try {
                pstFile.close();
                System.out.println("[INFO] PstSourceAdapter: Closed and released PST file lock: " + path);
            } catch (Exception e) {
                System.err.println("[WARNING] Failed to close PST file: " + path + " - " + e.getMessage());
            }
        }
    }

    @Override
    public void releaseAllResources() {
        clearCache();
    }

    @Override
    public boolean isSystemFolder(String folderName) {
        return isOutlookSystemFolder(folderName);
    }

    @Override
    public String getFolderEmoji(String folderName) {
        if (folderName == null) return "📁";
        String clean = folderName.trim().toLowerCase();
        switch (clean) {
            case "inbox": return "📥";
            case "sent items": return "📤";
            case "deleted items": return "🗑️";
            case "drafts": return "📝";
            case "outbox": return "📨";
            case "junk email":
            case "junk e-mail": return "🚫";
            case "calendar": return "📅";
            case "contacts": return "👥";
            case "tasks": return "✅";
            case "notes": return "📓";
            case "journal": return "📒";
            case "sync issues":
            case "conflicts":
            case "local failures":
            case "server failures": return "⚠️";
            case "top of personal folders":
            case "top of outlook data file":
            case "ipm_subtree": return "📂";
            default: return "📁";
        }
    }

    @Override
    public List<File> detectLocalMailboxes() {
        return OutlookDetector.detectPstFiles();
    }


    private PSTFolder findSubFolder(PSTFolder parent, String name) throws Exception {
        Vector<PSTFolder> subFolders = parent.getSubFolders();
        for (PSTFolder sub : subFolders) {
            if (sub.getDisplayName().equalsIgnoreCase(name)) {
                return sub;
            }
        }
        return null;
    }

    private boolean isOutlookSystemFolder(String name) {
        if (name == null) return false;
        String clean = name.trim().toLowerCase();
        return clean.equals("inbox") ||
               clean.equals("sent items") ||
               clean.equals("deleted items") ||
               clean.equals("drafts") ||
               clean.equals("outbox") ||
               clean.equals("junk email") ||
               clean.equals("junk e-mail") ||
               clean.equals("calendar") ||
               clean.equals("contacts") ||
               clean.equals("tasks") ||
               clean.equals("notes") ||
               clean.equals("journal") ||
               clean.equals("sync issues") ||
               clean.equals("conflicts") ||
               clean.equals("local failures") ||
               clean.equals("server failures") ||
               clean.equals("top of personal folders") ||
               clean.equals("top of outlook data file") ||
               clean.equals("ipm_subtree");
    }

    private boolean isUselessSystemFolder(String name) {
        if (name == null) return true;
        String clean = name.trim().toLowerCase();
        return clean.equals("search root") ||
               clean.equals("spam search folder 2") ||
               clean.equals("ipm_views") ||
               clean.equals("ipm_common_views") ||
               clean.equals("reminders") ||
               clean.equals("to-do search") ||
               clean.equals("itemprocsearch") ||
               clean.equals("tracked mail processing") ||
               clean.equals("freebusy data") ||
               clean.equals("conversation action settings") ||
               clean.equals("quick step settings") ||
               clean.equals("suggested contacts") ||
               clean.equals("news feed") ||
               clean.equals("contact search") ||
               clean.equals("contact search 1") ||
               clean.startsWith("ms-olk-bgpooledsearchfolder") ||
               clean.startsWith("ms-olk-fgpooledsearchfolder");
    }
}

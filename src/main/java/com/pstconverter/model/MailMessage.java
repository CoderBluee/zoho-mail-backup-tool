package com.pstconverter.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MailMessage {
    public static class Attachment {
        private final String filename;
        private byte[] data;
        private java.util.function.Supplier<byte[]> dataSupplier;
        private boolean loaded = false;

        public Attachment(String filename, byte[] data) {
            this.filename = filename;
            this.data = data;
            this.loaded = true;
        }

        public Attachment(String filename, java.util.function.Supplier<byte[]> dataSupplier) {
            this.filename = filename;
            this.dataSupplier = dataSupplier;
        }

        public String getFilename() {
            return filename;
        }

        public synchronized byte[] getData() {
            if (!loaded) {
                if (dataSupplier != null) {
                    try {
                        data = dataSupplier.get();
                    } catch (Exception e) {
                        System.err.println("Failed to load attachment data lazily: " + e.getMessage());
                        data = new byte[0];
                    }
                } else {
                    data = new byte[0];
                }
                loaded = true;
                dataSupplier = null; // free up memory reference
            }
            return data;
        }
    }

    private String from;
    private String subject;
    private String date;
    private String body;
    private final String itemType; // "Mail", "Contact", "Calendar", "Task", "Note"
    private final List<String> attachments = new ArrayList<>();
    private final List<Attachment> attachmentList = new ArrayList<>();
    private final Map<String, String> metadata = new LinkedHashMap<>(); // Use LinkedHashMap to preserve insertion order
    private String senderAddress = "";
    private String to = "";
    private String cc = "";
    private String bcc = "";
    private String messageId = "";
    private String status = "Unread";
    private String importance = "Normal";
    private String messageFlags = "None";

    public MailMessage(String from, String subject, String date, String body) {
        this(from, subject, date, body, "Mail");
    }

    public MailMessage(String from, String subject, String date, String body, String itemType) {
        this.from = from;
        this.subject = sanitizeSubject(subject);
        this.date = date;
        this.body = body;
        this.itemType = itemType != null ? itemType : "Mail";
    }

    public static String sanitizeSubject(String input) {
        if (input == null || input.isEmpty()) return "";
        String clean = input
            .replaceAll("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]", "")
            .replaceAll("[\\u2600-\\u27BF]", "")
            .replaceAll("[\\uFE00-\\uFE0F]", "")
            .replaceAll("[\\uFFFD]", "")
            .trim();
        return clean.isEmpty() ? "(No Subject)" : clean;
    }

    public void setSubject(String subject) {
        this.subject = sanitizeSubject(subject);
    }

    public String getFrom() {
        return from;
    }

    public String getSubject() {
        return subject;
    }

    public String getDate() {
        return date;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getItemType() {
        return itemType;
    }

    public boolean isContact() {
        return itemType != null && (itemType.equalsIgnoreCase("Contact") || itemType.toLowerCase().contains("contact"));
    }

    public boolean isCalendar() {
        return itemType != null && (itemType.equalsIgnoreCase("Calendar") || itemType.toLowerCase().contains("calendar") || itemType.toLowerCase().contains("appointment"));
    }

    public boolean isTask() {
        return itemType != null && (itemType.equalsIgnoreCase("Task") || itemType.toLowerCase().contains("task"));
    }

    public boolean isNote() {
        return itemType != null && (itemType.equalsIgnoreCase("Note") || itemType.toLowerCase().contains("note") || itemType.toLowerCase().contains("stickynote"));
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void addAttachment(String filename) {
        addAttachment(filename, new byte[0]);
    }

    public void addAttachment(String filename, byte[] data) {
        if (filename != null) {
            attachments.add(filename);
            attachmentList.add(new Attachment(filename, data != null ? data : new byte[0]));
        }
    }

    public void addAttachment(String filename, java.util.function.Supplier<byte[]> dataSupplier) {
        if (filename != null) {
            attachments.add(filename);
            attachmentList.add(new Attachment(filename, dataSupplier));
        }
    }

    public List<Attachment> getAttachmentList() {
        return attachmentList;
    }

    public void addMetadata(String key, String value) {
        if (key != null && value != null) {
            metadata.put(key, value);
        }
    }

    public String getSenderAddress() {
        return senderAddress != null ? senderAddress : "";
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
    }

    public String getTo() {
        return to != null ? to : "";
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCc() {
        return cc != null ? cc : "";
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public String getBcc() {
        return bcc != null ? bcc : "";
    }

    public void setBcc(String bcc) {
        this.bcc = bcc;
    }

    public String getMessageId() {
        return messageId != null ? messageId : "";
    }

    public String getUniqueIdentifier() {
        if (messageId != null && !messageId.trim().isEmpty()) {
            return messageId;
        }
        // Fallback: deterministic string of metadata
        StringBuilder sb = new StringBuilder();
        sb.append(from != null ? from : "").append("|");
        sb.append(subject != null ? subject : "").append("|");
        sb.append(date != null ? date : "").append("|");
        sb.append(itemType != null ? itemType : "");
        return "fallback_" + Math.abs(sb.toString().hashCode());
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getStatus() {
        return status != null ? status : "Unread";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImportance() {
        return importance != null ? importance : "Normal";
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }

    public String getMessageFlags() {
        return messageFlags != null ? messageFlags : "None";
    }

    public void setMessageFlags(String messageFlags) {
        this.messageFlags = messageFlags;
    }

    public String calculateSha256() {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            if (from != null)
                digest.update(from.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (subject != null)
                digest.update(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (date != null)
                digest.update(date.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (body != null)
                digest.update(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (to != null)
                digest.update(to.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (cc != null)
                digest.update(cc.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (messageId != null)
                digest.update(messageId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                if (entry.getKey() != null)
                    digest.update(entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (entry.getValue() != null)
                    digest.update(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            for (Attachment att : attachmentList) {
                if (att.getFilename() != null) {
                    digest.update(att.getFilename().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                byte[] bytes = att.getData();
                if (bytes != null && bytes.length > 0) {
                    digest.update(bytes);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            System.err.println("Failed to calculate SHA-256 checksum for message: " + e.getMessage());
            return "";
        }
    }

    public void filterFields(boolean incSubject, boolean incFrom, boolean incTo, boolean incCcBcc, boolean incDate,
            boolean incBody) {
        if (!incSubject) {
            this.subject = "";
            this.status = "Unread";
            this.importance = "Normal";
            this.messageFlags = "None";
        }
        if (!incFrom) {
            this.from = "";
            this.senderAddress = "";
        }
        if (!incTo) {
            this.to = "";
        }
        if (!incCcBcc) {
            this.cc = "";
            this.bcc = "";
        }
        if (!incDate) {
            this.date = "";
            this.messageId = "";
        }
        if (!incBody) {
            this.body = "";
        }
    }
}

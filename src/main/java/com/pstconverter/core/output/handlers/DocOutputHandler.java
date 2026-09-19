package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class DocOutputHandler implements OutputHandler {
    @Override
    public void export(File targetFolder, List<MailMessage> emails) throws Exception {
        export(targetFolder, emails, "Keep Attachments in Folder");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling) throws Exception {
        export(targetFolder, emails, attachmentHandling, "Individual File per Email (One file per message)");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling, String exportStructure) throws Exception {
        export(targetFolder, emails, attachmentHandling, exportStructure, "Original Subject");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling, String exportStructure, String namingConvention) throws Exception {
        if (emails == null || emails.isEmpty()) return;

        boolean incSubject = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_subject", "true"));
        boolean incFrom = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_from", "true"));
        boolean incTo = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_to", "true"));
        boolean incCcBcc = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_ccbcc", "true"));
        boolean incDate = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_date", "true"));
        boolean incBody = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_body", "true"));

        if (exportStructure != null && exportStructure.startsWith("Single Combined File")) {
            File file = new File(targetFolder, targetFolder.getName() + ".doc");
            try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println("<!-- [if gte mso 9]>");
                pw.println("<xml><w:WordDocument><w:View>Print</w:View></w:WordDocument></xml>");
                pw.println("<![endif]-->");
                pw.println("<html>");
                pw.println("<head><meta charset=\"UTF-8\"></head>");
                pw.println("<body style=\"font-family: 'Arial', sans-serif; font-size: 11pt; line-height: 1.5;\">");
                int index = 1;
                for (MailMessage msg : emails) {
                    pw.println("<div style=\"page-break-after: always; margin-bottom: 30px;\">");
                    if (incSubject) {
                        pw.println("<h2 style=\"color: #4f46e5;\">#" + index + " - Subject: " + OutputHandler.escapeHtml(msg.getSubject()) + "</h2>");
                    } else {
                        pw.println("<h2 style=\"color: #4f46e5;\">#" + index + "</h2>");
                    }
                    if (incFrom) {
                        pw.println("<p><b>From:</b> " + OutputHandler.escapeHtml(msg.getFrom()) + "</p>");
                        String senderAddr = msg.getSenderAddress();
                        if (senderAddr != null && !senderAddr.isEmpty()) {
                            pw.println("<p><b>Sender Address:</b> " + OutputHandler.escapeHtml(senderAddr) + "</p>");
                        }
                    }
                    if (incTo) {
                        String displayTo = msg.getTo();
                        if (displayTo != null && !displayTo.isEmpty()) {
                            pw.println("<p><b>To:</b> " + OutputHandler.escapeHtml(displayTo) + "</p>");
                        }
                    }
                    if (incCcBcc) {
                        String cc = msg.getCc();
                        if (cc != null && !cc.isEmpty()) {
                            pw.println("<p><b>Cc:</b> " + OutputHandler.escapeHtml(cc) + "</p>");
                        }
                        String bcc = msg.getBcc();
                        if (bcc != null && !bcc.isEmpty()) {
                            pw.println("<p><b>Bcc:</b> " + OutputHandler.escapeHtml(bcc) + "</p>");
                        }
                    }
                    if (incDate) {
                        pw.println("<p><b>Date:</b> " + OutputHandler.escapeHtml(msg.getDate()) + "</p>");
                    }
                    
                    // Technical fields are included only if some headers are checked
                    if (incSubject || incFrom || incTo || incCcBcc || incDate) {
                        String msgId = msg.getMessageId();
                        if (msgId != null && !msgId.isEmpty()) {
                            pw.println("<p><b>Message-ID:</b> " + OutputHandler.escapeHtml(msgId) + "</p>");
                        }
                        String status = msg.getStatus();
                        if (status != null && !status.isEmpty()) {
                            pw.println("<p><b>Status:</b> " + OutputHandler.escapeHtml(status) + "</p>");
                        }
                        String importance = msg.getImportance();
                        if (importance != null && !importance.isEmpty()) {
                            pw.println("<p><b>Importance:</b> " + OutputHandler.escapeHtml(importance) + "</p>");
                        }
                        String flags = msg.getMessageFlags();
                        if (flags != null && !flags.isEmpty() && !flags.equals("None")) {
                            pw.println("<p><b>Flags:</b> " + OutputHandler.escapeHtml(flags) + "</p>");
                        }
                    }
                    pw.println("<hr/>");
                    if (incBody) {
                        String body = msg.getBody();
                        if (body != null && !body.trim().isEmpty()) {
                            String lower = body.toLowerCase();
                            if (lower.contains("<html") || lower.contains("<body") || lower.contains("<div") || lower.contains("<p") || lower.contains("<table") || lower.contains("<style") || lower.contains("<span") || lower.contains("<img") || lower.contains("<br")) {
                                pw.println("<div style='white-space: normal;'>" + body + "</div>");
                            } else {
                                String formatted = OutputHandler.escapeHtml(body).replace("\n", "<br>");
                                pw.println("<div style='white-space: normal;'>" + formatted + "</div>");
                            }
                        }
                    }
                    pw.println("</div>");
                    
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    index++;
                }
                pw.println("</body></html>");
            } catch (Exception e) {
                System.err.println("Error exporting to combined DOC: " + e.getMessage());
                throw e;
            }
        } else {
            int index = 1;
            for (MailMessage msg : emails) {
                String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "doc");
                File file = new File(targetFolder, fileName);
                try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    pw.println("<!-- [if gte mso 9]>");
                    pw.println("<xml><w:WordDocument><w:View>Print</w:View></w:WordDocument></xml>");
                    pw.println("<![endif]-->");
                    pw.println("<html>");
                    pw.println("<head><meta charset=\"UTF-8\"></head>");
                    pw.println("<body style=\"font-family: 'Arial', sans-serif; font-size: 11pt; line-height: 1.5;\">");
                    if (incSubject) {
                        pw.println("<h2 style=\"color: #4f46e5;\">Subject: " + OutputHandler.escapeHtml(msg.getSubject()) + "</h2>");
                    }
                    if (incFrom) {
                        pw.println("<p><b>From:</b> " + OutputHandler.escapeHtml(msg.getFrom()) + "</p>");
                        String senderAddr = msg.getSenderAddress();
                        if (senderAddr != null && !senderAddr.isEmpty()) {
                            pw.println("<p><b>Sender Address:</b> " + OutputHandler.escapeHtml(senderAddr) + "</p>");
                        }
                    }
                    if (incTo) {
                        String displayTo = msg.getTo();
                        if (displayTo != null && !displayTo.isEmpty()) {
                            pw.println("<p><b>To:</b> " + OutputHandler.escapeHtml(displayTo) + "</p>");
                        }
                    }
                    if (incCcBcc) {
                        String cc = msg.getCc();
                        if (cc != null && !cc.isEmpty()) {
                            pw.println("<p><b>Cc:</b> " + OutputHandler.escapeHtml(cc) + "</p>");
                        }
                        String bcc = msg.getBcc();
                        if (bcc != null && !bcc.isEmpty()) {
                            pw.println("<p><b>Bcc:</b> " + OutputHandler.escapeHtml(bcc) + "</p>");
                        }
                    }
                    if (incDate) {
                        pw.println("<p><b>Date:</b> " + OutputHandler.escapeHtml(msg.getDate()) + "</p>");
                    }
                    if (incSubject || incFrom || incTo || incCcBcc || incDate) {
                        String msgId = msg.getMessageId();
                        if (msgId != null && !msgId.isEmpty()) {
                            pw.println("<p><b>Message-ID:</b> " + OutputHandler.escapeHtml(msgId) + "</p>");
                        }
                        String status = msg.getStatus();
                        if (status != null && !status.isEmpty()) {
                            pw.println("<p><b>Status:</b> " + OutputHandler.escapeHtml(status) + "</p>");
                        }
                        String importance = msg.getImportance();
                        if (importance != null && !importance.isEmpty()) {
                            pw.println("<p><b>Importance:</b> " + OutputHandler.escapeHtml(importance) + "</p>");
                        }
                        String flags = msg.getMessageFlags();
                        if (flags != null && !flags.isEmpty() && !flags.equals("None")) {
                            pw.println("<p><b>Flags:</b> " + OutputHandler.escapeHtml(flags) + "</p>");
                        }
                    }
                    pw.println("<hr/>");
                    if (incBody) {
                        String body = msg.getBody();
                        if (body != null && !body.trim().isEmpty()) {
                            String lower = body.toLowerCase();
                            if (lower.contains("<html") || lower.contains("<body") || lower.contains("<div") || lower.contains("<p") || lower.contains("<table") || lower.contains("<style") || lower.contains("<span") || lower.contains("<img") || lower.contains("<br")) {
                                pw.println("<div style='white-space: normal;'>" + body + "</div>");
                            } else {
                                String formatted = OutputHandler.escapeHtml(body).replace("\n", "<br>");
                                pw.println("<div style='white-space: normal;'>" + formatted + "</div>");
                            }
                        }
                    }
                    pw.println("</body></html>");
                    
                    // Save attachment files
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    
                    index++;
                } catch (Exception e) {
                    System.err.println("Error exporting to DOC: " + e.getMessage());
                    throw e;
                }
            }
        }
    }
}

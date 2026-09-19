package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class MhtmlOutputHandler implements OutputHandler {
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
            File file = new File(targetFolder, targetFolder.getName() + ".mhtml");
            String boundary = "----=_NextPart_" + System.currentTimeMillis();
            boolean shouldEmbed = attachmentHandling != null && attachmentHandling.contains("Embed Attachments");
            try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println("From: Combined Export");
                pw.println("Subject: Combined Emails");
                pw.println("MIME-Version: 1.0");
                pw.println("Content-Type: multipart/related; boundary=\"" + boundary + "\"; type=\"text/html\"");
                pw.println();
                
                // Write the main combined HTML part
                pw.println("--" + boundary);
                pw.println("Content-Type: text/html; charset=\"utf-8\"");
                pw.println("Content-Transfer-Encoding: 7bit");
                pw.println();
                pw.println("<html><body>");
                pw.println("<h1>Combined MHTML Export</h1>");
                pw.println("<hr>");
                int index = 1;
                for (MailMessage msg : emails) {
                    pw.println("<div style='border: 1px solid #ccc; padding: 15px; margin: 15px 0; border-radius: 5px;'>");
                    if (incSubject) {
                        pw.println("<h2>#" + index + " - " + OutputHandler.escapeHtml(msg.getSubject()) + "</h2>");
                    } else {
                        pw.println("<h2>#" + index + "</h2>");
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
                    pw.println("<hr>");
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
                    index++;
                }
                pw.println("</body></html>");
                pw.println();
                
                // If embedding, append attachments for all emails
                if (shouldEmbed) {
                    for (MailMessage msg : emails) {
                        List<MailMessage.Attachment> attachments = msg.getAttachmentList();
                        if (attachments != null) {
                            for (MailMessage.Attachment att : attachments) {
                                String filename = OutputHandler.sanitizeFileName(att.getFilename());
                                byte[] data = att.getData();
                                if (data == null) continue;
                                
                                pw.println("--" + boundary);
                                pw.println("Content-Type: application/octet-stream; name=\"" + filename + "\"");
                                pw.println("Content-Disposition: attachment; filename=\"" + filename + "\"");
                                pw.println("Content-Transfer-Encoding: base64");
                                pw.println();
                                
                                String base64 = java.util.Base64.getEncoder().encodeToString(data);
                                int len = base64.length();
                                for (int i = 0; i < len; i += 76) {
                                    pw.println(base64.substring(i, Math.min(i + 76, len)));
                                }
                                pw.println();
                            }
                        }
                    }
                }
                
                pw.println("--" + boundary + "--");
                
                // Save attachments locally if required
                index = 1;
                for (MailMessage msg : emails) {
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    index++;
                }
            } catch (Exception e) {
                System.err.println("Error exporting to combined MHTML: " + e.getMessage());
                throw e;
            }
        } else {
            int index = 1;
            boolean shouldEmbed = attachmentHandling != null && attachmentHandling.contains("Embed Attachments");
            for (MailMessage msg : emails) {
                String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "mhtml");
                File file = new File(targetFolder, fileName);
                String boundary = "----=_NextPart_" + System.currentTimeMillis();
                try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    if (incFrom) {
                        pw.println("From: " + msg.getFrom());
                    }
                    if (incSubject) {
                        pw.println("Subject: " + msg.getSubject());
                    }
                    if (incDate) {
                        pw.println("Date: " + msg.getDate());
                    }
                    if (incTo) {
                        String displayTo = msg.getTo();
                        if (displayTo != null && !displayTo.isEmpty()) {
                            pw.println("To: " + displayTo);
                        }
                    }
                    if (incCcBcc) {
                        String cc = msg.getCc();
                        if (cc != null && !cc.isEmpty()) {
                            pw.println("Cc: " + cc);
                        }
                        String bcc = msg.getBcc();
                        if (bcc != null && !bcc.isEmpty()) {
                            pw.println("Bcc: " + bcc);
                        }
                    }
                    if (incSubject || incFrom || incTo || incCcBcc || incDate) {
                        String msgId = msg.getMessageId();
                        if (msgId != null && !msgId.isEmpty()) {
                            pw.println("Message-ID: " + msgId);
                        }
                        String importance = msg.getImportance();
                        if (importance != null && !importance.isEmpty()) {
                            pw.println("Importance: " + importance);
                            if (importance.equalsIgnoreCase("High")) {
                                pw.println("X-Priority: 1");
                            } else if (importance.equalsIgnoreCase("Low")) {
                                pw.println("X-Priority: 5");
                            } else {
                                pw.println("X-Priority: 3");
                            }
                        }
                        String status = msg.getStatus();
                        if (status != null && !status.isEmpty()) {
                            pw.println("Status: " + (status.equalsIgnoreCase("Read") ? "R" : "U"));
                        }
                    }
                    pw.println("MIME-Version: 1.0");
                    pw.println("Content-Type: multipart/related; boundary=\"" + boundary + "\"; type=\"text/html\"");
                    pw.println();
                    pw.println("--" + boundary);
                    pw.println("Content-Type: text/html; charset=\"utf-8\"");
                    pw.println("Content-Transfer-Encoding: 7bit");
                    pw.println();
                    pw.println("<html><body>");
                    if (incSubject) {
                        pw.println("<h2>Subject: " + OutputHandler.escapeHtml(msg.getSubject()) + "</h2>");
                    }
                    if (incFrom) {
                        pw.println("<p><b>From:</b> " + OutputHandler.escapeHtml(msg.getFrom()) + "</p>");
                        String senderAddr = msg.getSenderAddress();
                        if (senderAddr != null && !senderAddr.isEmpty()) {
                            pw.println("<p><b>Sender Address:</b> " + OutputHandler.escapeHtml(senderAddr) + "</p>");
                        }
                    }
                    if (incTo) {
                        String htmlTo = msg.getTo();
                        if (htmlTo != null && !htmlTo.isEmpty()) {
                            pw.println("<p><b>To:</b> " + OutputHandler.escapeHtml(htmlTo) + "</p>");
                        }
                    }
                    if (incCcBcc) {
                        String htmlCc = msg.getCc();
                        if (htmlCc != null && !htmlCc.isEmpty()) {
                            pw.println("<p><b>Cc:</b> " + OutputHandler.escapeHtml(htmlCc) + "</p>");
                        }
                        String htmlBcc = msg.getBcc();
                        if (htmlBcc != null && !htmlBcc.isEmpty()) {
                            pw.println("<p><b>Bcc:</b> " + OutputHandler.escapeHtml(htmlBcc) + "</p>");
                        }
                    }
                    if (incDate) {
                        pw.println("<p><b>Date:</b> " + OutputHandler.escapeHtml(msg.getDate()) + "</p>");
                    }
                    if (incSubject || incFrom || incTo || incCcBcc || incDate) {
                        String htmlMsgId = msg.getMessageId();
                        if (htmlMsgId != null && !htmlMsgId.isEmpty()) {
                            pw.println("<p><b>Message-ID:</b> " + OutputHandler.escapeHtml(htmlMsgId) + "</p>");
                        }
                        String htmlStatus = msg.getStatus();
                        if (htmlStatus != null && !htmlStatus.isEmpty()) {
                            pw.println("<p><b>Status:</b> " + OutputHandler.escapeHtml(htmlStatus) + "</p>");
                        }
                        String htmlImportance = msg.getImportance();
                        if (htmlImportance != null && !htmlImportance.isEmpty()) {
                            pw.println("<p><b>Importance:</b> " + OutputHandler.escapeHtml(htmlImportance) + "</p>");
                        }
                        String htmlFlags = msg.getMessageFlags();
                        if (htmlFlags != null && !htmlFlags.isEmpty() && !htmlFlags.equals("None")) {
                            pw.println("<p><b>Flags:</b> " + OutputHandler.escapeHtml(htmlFlags) + "</p>");
                        }
                    }
                    pw.println("<hr>");
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
                    pw.println();
                    
                    if (shouldEmbed) {
                        List<MailMessage.Attachment> attachments = msg.getAttachmentList();
                        if (attachments != null) {
                            for (MailMessage.Attachment att : attachments) {
                                String filename = OutputHandler.sanitizeFileName(att.getFilename());
                                byte[] data = att.getData();
                                if (data == null) continue;
                                
                                pw.println("--" + boundary);
                                pw.println("Content-Type: application/octet-stream; name=\"" + filename + "\"");
                                pw.println("Content-Disposition: attachment; filename=\"" + filename + "\"");
                                pw.println("Content-Transfer-Encoding: base64");
                                pw.println();
                                
                                String base64 = java.util.Base64.getEncoder().encodeToString(data);
                                int len = base64.length();
                                for (int i = 0; i < len; i += 76) {
                                    pw.println(base64.substring(i, Math.min(i + 76, len)));
                                }
                                pw.println();
                            }
                        }
                    }
                    
                    pw.println("--" + boundary + "--");
                    
                    // Save attachment files
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    
                    index++;
                } catch (Exception e) {
                    System.err.println("Error exporting to MHTML: " + e.getMessage());
                    throw e;
                }
            }
        }
    }
}

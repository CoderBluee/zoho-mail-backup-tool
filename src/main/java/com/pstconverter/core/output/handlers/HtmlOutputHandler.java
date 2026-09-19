package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class HtmlOutputHandler implements OutputHandler {
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
            File file = new File(targetFolder, targetFolder.getName() + ".html");
            try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println("<!DOCTYPE html>");
                pw.println("<html>");
                pw.println("<head>");
                pw.println("<meta charset=\"UTF-8\">");
                pw.println("<title>Combined Emails</title>");
                pw.println("<style>");
                pw.println("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.5; padding: 20px; color: #1e293b; background-color: #f8fafc; }");
                pw.println(".container { max-width: 800px; margin: 20px auto; background: #ffffff; padding: 30px; border-radius: 8px; border: 1px solid #e2e8f0; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
                pw.println(".header { border-bottom: 2px solid #e2e8f0; padding-bottom: 15px; margin-bottom: 20px; }");
                pw.println(".header h1 { margin: 0 0 10px 0; font-size: 22px; color: #4f46e5; }");
                pw.println(".meta { font-size: 13px; color: #64748b; margin: 4px 0; }");
                pw.println(".meta strong { color: #475569; }");
                pw.println(".body { white-space: pre-wrap; word-break: break-word; font-size: 14px; margin-top: 20px; }");
                pw.println("</style>");
                pw.println("</head>");
                pw.println("<body>");
                int index = 1;
                for (MailMessage msg : emails) {
                    pw.println("<div class='container'>");
                    pw.println("<div class='header'>");
                    if (incSubject) {
                        pw.println("<h1>#" + index + " - Subject: " + OutputHandler.escapeHtml(msg.getSubject()) + "</h1>");
                    } else {
                        pw.println("<h1>#" + index + "</h1>");
                    }
                    if (incFrom) {
                        pw.println("<div class='meta'><strong>From:</strong> " + OutputHandler.escapeHtml(msg.getFrom()) + "</div>");
                        String senderAddr = msg.getSenderAddress();
                        if (!senderAddr.isEmpty()) {
                            pw.println("<div class='meta'><strong>Sender Address:</strong> " + OutputHandler.escapeHtml(senderAddr) + "</div>");
                        }
                    }
                    if (incTo) {
                        String displayTo = msg.getTo();
                        if (!displayTo.isEmpty()) {
                            pw.println("<div class='meta'><strong>To:</strong> " + OutputHandler.escapeHtml(displayTo) + "</div>");
                        }
                    }
                    if (incCcBcc) {
                        String cc = msg.getCc();
                        if (!cc.isEmpty()) {
                            pw.println("<div class='meta'><strong>Cc:</strong> " + OutputHandler.escapeHtml(cc) + "</div>");
                        }
                        String bcc = msg.getBcc();
                        if (!bcc.isEmpty()) {
                            pw.println("<div class='meta'><strong>Bcc:</strong> " + OutputHandler.escapeHtml(bcc) + "</div>");
                        }
                    }
                    if (incDate) {
                        pw.println("<div class='meta'><strong>Date:</strong> " + OutputHandler.escapeHtml(msg.getDate()) + "</div>");
                        String msgId = msg.getMessageId();
                        if (!msgId.isEmpty()) {
                            pw.println("<div class='meta'><strong>Message-ID:</strong> " + OutputHandler.escapeHtml(msgId) + "</div>");
                        }
                    }
                    if (incSubject) {
                        String status = msg.getStatus();
                        if (!status.isEmpty()) {
                            pw.println("<div class='meta'><strong>Status:</strong> " + OutputHandler.escapeHtml(status) + "</div>");
                        }
                        String importance = msg.getImportance();
                        if (!importance.isEmpty()) {
                            pw.println("<div class='meta'><strong>Importance:</strong> " + OutputHandler.escapeHtml(importance) + "</div>");
                        }
                        String flags = msg.getMessageFlags();
                        if (!flags.isEmpty() && !flags.equals("None")) {
                            pw.println("<div class='meta'><strong>Flags:</strong> " + OutputHandler.escapeHtml(flags) + "</div>");
                        }
                    }
                    pw.println("</div>");
                    if (incBody) {
                        String body = msg.getBody();
                        if (body != null && !body.trim().isEmpty()) {
                            String lower = body.toLowerCase();
                            if (lower.contains("<html") || lower.contains("<body") || lower.contains("<div") || lower.contains("<p") || lower.contains("<table") || lower.contains("<style") || lower.contains("<!doctype") || lower.contains("<span") || lower.contains("<img") || lower.contains("<br")) {
                                pw.println("<div class='body' style='white-space: normal; overflow-x: auto;'>" + body + "</div>");
                            } else {
                                String formatted = OutputHandler.escapeHtml(body).replace("\n", "<br>");
                                pw.println("<div class='body'>" + formatted + "</div>");
                            }
                        }
                    }
                    pw.println("</div>");
                    
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    index++;
                }
                pw.println("</body>");
                pw.println("</html>");
            } catch (Exception e) {
                System.err.println("Error exporting to combined HTML: " + e.getMessage());
                throw e;
            }
        } else {
            int index = 1;
            for (MailMessage msg : emails) {
                String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "html");
                File file = new File(targetFolder, fileName);
                try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    pw.println("<!DOCTYPE html>");
                    pw.println("<html>");
                    pw.println("<head>");
                    pw.println("<meta charset=\"UTF-8\">");
                    if (incSubject) {
                        pw.println("<title>" + OutputHandler.escapeHtml(msg.getSubject()) + "</title>");
                    } else {
                        pw.println("<title>Email #" + index + "</title>");
                    }
                    pw.println("<style>");
                    pw.println("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.5; padding: 20px; color: #1e293b; background-color: #f8fafc; }");
                    pw.println(".container { max-width: 800px; margin: 0 auto; background: #ffffff; padding: 30px; border-radius: 8px; border: 1px solid #e2e8f0; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }");
                    pw.println(".header { border-bottom: 2px solid #e2e8f0; padding-bottom: 15px; margin-bottom: 20px; }");
                    pw.println(".header h1 { margin: 0 0 10px 0; font-size: 22px; color: #4f46e5; }");
                    pw.println(".meta { font-size: 13px; color: #64748b; margin: 4px 0; }");
                    pw.println(".meta strong { color: #475569; }");
                    pw.println(".body { white-space: pre-wrap; word-break: break-word; font-size: 14px; margin-top: 20px; }");
                    pw.println("</style>");
                    pw.println("</head>");
                    pw.println("<body>");
                    pw.println("<div class='container'>");
                    pw.println("<div class='header'>");
                    if (incSubject) {
                        pw.println("<h1>Subject: " + OutputHandler.escapeHtml(msg.getSubject()) + "</h1>");
                    } else {
                        pw.println("<h1>Email #" + index + "</h1>");
                    }
                    if (incFrom) {
                        pw.println("<div class='meta'><strong>From:</strong> " + OutputHandler.escapeHtml(msg.getFrom()) + "</div>");
                        String senderAddr = msg.getSenderAddress();
                        if (!senderAddr.isEmpty()) {
                            pw.println("<div class='meta'><strong>Sender Address:</strong> " + OutputHandler.escapeHtml(senderAddr) + "</div>");
                        }
                    }
                    if (incTo) {
                        String displayTo = msg.getTo();
                        if (!displayTo.isEmpty()) {
                            pw.println("<div class='meta'><strong>To:</strong> " + OutputHandler.escapeHtml(displayTo) + "</div>");
                        }
                    }
                    if (incCcBcc) {
                        String cc = msg.getCc();
                        if (!cc.isEmpty()) {
                            pw.println("<div class='meta'><strong>Cc:</strong> " + OutputHandler.escapeHtml(cc) + "</div>");
                        }
                        String bcc = msg.getBcc();
                        if (!bcc.isEmpty()) {
                            pw.println("<div class='meta'><strong>Bcc:</strong> " + OutputHandler.escapeHtml(bcc) + "</div>");
                        }
                    }
                    if (incDate) {
                        pw.println("<div class='meta'><strong>Date:</strong> " + OutputHandler.escapeHtml(msg.getDate()) + "</div>");
                        String msgId = msg.getMessageId();
                        if (!msgId.isEmpty()) {
                            pw.println("<div class='meta'><strong>Message-ID:</strong> " + OutputHandler.escapeHtml(msgId) + "</div>");
                        }
                    }
                    if (incSubject) {
                        String status = msg.getStatus();
                        if (!status.isEmpty()) {
                            pw.println("<div class='meta'><strong>Status:</strong> " + OutputHandler.escapeHtml(status) + "</div>");
                        }
                        String importance = msg.getImportance();
                        if (!importance.isEmpty()) {
                            pw.println("<div class='meta'><strong>Importance:</strong> " + OutputHandler.escapeHtml(importance) + "</div>");
                        }
                        String flags = msg.getMessageFlags();
                        if (!flags.isEmpty() && !flags.equals("None")) {
                            pw.println("<div class='meta'><strong>Flags:</strong> " + OutputHandler.escapeHtml(flags) + "</div>");
                        }
                    }
                    pw.println("</div>");
                    if (incBody) {
                        String body = msg.getBody();
                        if (body != null && !body.trim().isEmpty()) {
                            String lower = body.toLowerCase();
                            if (lower.contains("<html") || lower.contains("<body") || lower.contains("<div") || lower.contains("<p") || lower.contains("<table") || lower.contains("<style") || lower.contains("<!doctype") || lower.contains("<span") || lower.contains("<img") || lower.contains("<br")) {
                                pw.println("<div class='body' style='white-space: normal; overflow-x: auto;'>" + body + "</div>");
                            } else {
                                String formatted = OutputHandler.escapeHtml(body).replace("\n", "<br>");
                                pw.println("<div class='body'>" + formatted + "</div>");
                            }
                        }
                    }
                    pw.println("</div>");
                    pw.println("</body>");
                    pw.println("</html>");
                    
                    // Save attachment files
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    
                    index++;
                } catch (Exception e) {
                    System.err.println("Error exporting to HTML: " + e.getMessage());
                    throw e;
                }
            }
        }
    }
}

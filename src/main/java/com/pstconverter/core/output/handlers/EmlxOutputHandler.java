package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class EmlxOutputHandler implements OutputHandler {
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
        int index = 1;
        boolean shouldEmbed = attachmentHandling != null && attachmentHandling.contains("Embed Attachments");
        for (MailMessage msg : emails) {
            String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "emlx");
            File file = new File(targetFolder, fileName);

            StringBuilder emlSb = new StringBuilder();
            emlSb.append("From: ").append(msg.getFrom()).append("\n");
            emlSb.append("Subject: ").append(msg.getSubject()).append("\n");
            emlSb.append("Date: ").append(msg.getDate()).append("\n");
            String displayTo = msg.getTo();
            if (!displayTo.isEmpty()) {
                emlSb.append("To: ").append(displayTo).append("\n");
            }
            String cc = msg.getCc();
            if (!cc.isEmpty()) {
                emlSb.append("Cc: ").append(cc).append("\n");
            }
            String bcc = msg.getBcc();
            if (!bcc.isEmpty()) {
                emlSb.append("Bcc: ").append(bcc).append("\n");
            }
            String msgId = msg.getMessageId();
            if (!msgId.isEmpty()) {
                emlSb.append("Message-ID: ").append(msgId).append("\n");
            }
            String importance = msg.getImportance();
            if (!importance.isEmpty()) {
                emlSb.append("Importance: ").append(importance).append("\n");
                if (importance.equalsIgnoreCase("High")) {
                    emlSb.append("X-Priority: 1\n");
                } else if (importance.equalsIgnoreCase("Low")) {
                    emlSb.append("X-Priority: 5\n");
                } else {
                    emlSb.append("X-Priority: 3\n");
                }
            }
            String status = msg.getStatus();
            if (!status.isEmpty()) {
                emlSb.append("Status: ").append(status.equalsIgnoreCase("Read") ? "R" : "U").append("\n");
            }
            if (shouldEmbed) {
                emlSb.append(OutputHandler.buildMimeMessageString(msg));
            } else {
                emlSb.append("MIME-Version: 1.0\n");
                emlSb.append("Content-Type: text/plain; charset=\"utf-8\"\n\n");
                emlSb.append(OutputHandler.getPlainTextBody(msg.getBody())).append("\n");
            }

            String emlContent = emlSb.toString();
            int emlLength = emlContent.getBytes(StandardCharsets.UTF_8).length;

            try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.print(emlLength);
                pw.print("\n");
                pw.print(emlContent);
                pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                pw.println("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">");
                pw.println("<plist version=\"1.0\">");
                pw.println("<dict>");
                pw.println("  <key>subject</key>");
                pw.println("  <string>" + OutputHandler.escapeXml(msg.getSubject()) + "</string>");
                pw.println("  <key>sender</key>");
                pw.println("  <string>" + OutputHandler.escapeXml(msg.getFrom()) + "</string>");
                String plistSenderAddr = msg.getSenderAddress();
                if (!plistSenderAddr.isEmpty()) {
                    pw.println("  <key>sender_address</key>");
                    pw.println("  <string>" + OutputHandler.escapeXml(plistSenderAddr) + "</string>");
                }
                String plistTo = msg.getTo();
                if (!plistTo.isEmpty()) {
                    pw.println("  <key>to</key>");
                    pw.println("  <string>" + OutputHandler.escapeXml(plistTo) + "</string>");
                }
                String plistCc = msg.getCc();
                if (!plistCc.isEmpty()) {
                    pw.println("  <key>cc</key>");
                    pw.println("  <string>" + OutputHandler.escapeXml(plistCc) + "</string>");
                }
                String plistBcc = msg.getBcc();
                if (!plistBcc.isEmpty()) {
                    pw.println("  <key>bcc</key>");
                    pw.println("  <string>" + OutputHandler.escapeXml(plistBcc) + "</string>");
                }
                String plistMsgId = msg.getMessageId();
                if (!plistMsgId.isEmpty()) {
                    pw.println("  <key>message_id</key>");
                    pw.println("  <string>" + OutputHandler.escapeXml(plistMsgId) + "</string>");
                }
                String plistStatus = msg.getStatus();
                if (!plistStatus.isEmpty()) {
                    pw.println("  <key>status</key>");
                    pw.println("  <string>" + OutputHandler.escapeXml(plistStatus) + "</string>");
                }
                String plistImportance = msg.getImportance();
                if (!plistImportance.isEmpty()) {
                    pw.println("  <key>importance</key>");
                    pw.println("  <string>" + OutputHandler.escapeXml(plistImportance) + "</string>");
                }
                String plistFlags = msg.getMessageFlags();
                if (!plistFlags.isEmpty()) {
                    pw.println("  <key>message_flags</key>");
                    pw.println("  <string>" + OutputHandler.escapeXml(plistFlags) + "</string>");
                }
                pw.println("</dict>");
                pw.println("</plist>");
                
                // Save attachment files
                String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                
                index++;
            } catch (Exception e) {
                System.err.println("Error exporting to EMLX: " + e.getMessage());
                throw e;
            }
        }
    }
}

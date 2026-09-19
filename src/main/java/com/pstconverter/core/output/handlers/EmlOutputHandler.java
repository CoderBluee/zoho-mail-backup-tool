package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class EmlOutputHandler implements OutputHandler {
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
            String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "eml");
            File file = new File(targetFolder, fileName);
            try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println("From: " + msg.getFrom());
                pw.println("Subject: " + msg.getSubject());
                pw.println("Date: " + msg.getDate());
                String displayTo = msg.getTo();
                if (!displayTo.isEmpty()) {
                    pw.println("To: " + displayTo);
                }
                String cc = msg.getCc();
                if (!cc.isEmpty()) {
                    pw.println("Cc: " + cc);
                }
                String bcc = msg.getBcc();
                if (!bcc.isEmpty()) {
                    pw.println("Bcc: " + bcc);
                }
                String msgId = msg.getMessageId();
                if (!msgId.isEmpty()) {
                    pw.println("Message-ID: " + msgId);
                }
                String importance = msg.getImportance();
                if (!importance.isEmpty()) {
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
                if (!status.isEmpty()) {
                    pw.println("Status: " + (status.equalsIgnoreCase("Read") ? "R" : "U"));
                }
                if (shouldEmbed) {
                    pw.print(OutputHandler.buildMimeMessageString(msg));
                } else {
                    pw.println("MIME-Version: 1.0");
                    pw.println("Content-Type: text/plain; charset=\"" + OutputHandler.getFallbackEncoding() + "\"");
                    pw.println();
                    pw.println(OutputHandler.getPlainTextBody(msg.getBody()));
                }
                
                // Save attachment files
                String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                
                index++;
            } catch (Exception e) {
                System.err.println("Error exporting to EML: " + e.getMessage());
                throw e;
            }
        }
    }
}

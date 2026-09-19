package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class TxtOutputHandler implements OutputHandler {
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
            File combinedFile = new File(targetFolder, targetFolder.getName() + ".txt");
            try (PrintWriter pw = new PrintWriter(combinedFile, java.nio.charset.StandardCharsets.UTF_8)) {
                int index = 1;
                for (MailMessage msg : emails) {
                    pw.println("==================== Email #" + index + " ====================");
                    writeEmailToTxt(msg, pw, incSubject, incFrom, incTo, incCcBcc, incDate, incBody);
                    pw.println("\n" + "=".repeat(80) + "\n");
                    
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    index++;
                }
            } catch (Exception e) {
                System.err.println("Error exporting to combined TXT: " + e.getMessage());
                throw e;
            }
        } else {
            int index = 1;
            for (MailMessage msg : emails) {
                String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "txt");
                File individualFile = new File(targetFolder, fileName);
                try (PrintWriter pw = new PrintWriter(individualFile, java.nio.charset.StandardCharsets.UTF_8)) {
                    writeEmailToTxt(msg, pw, incSubject, incFrom, incTo, incCcBcc, incDate, incBody);
                }
                String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                index++;
            }
        }
    }

    private void writeEmailToTxt(MailMessage msg, PrintWriter pw, boolean incSubject, boolean incFrom, boolean incTo, boolean incCcBcc, boolean incDate, boolean incBody) {
        if (incFrom) {
            pw.println("From: " + msg.getFrom());
        }
        if (incSubject) {
            pw.println("Subject: " + msg.getSubject());
        }
        if (incDate) {
            pw.println("Date: " + msg.getDate());
        }
        if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
            for (Map.Entry<String, String> entry : msg.getMetadata().entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.contains("from") && !incFrom) continue;
                if (key.contains("subject") && !incSubject) continue;
                if (key.contains("to") && !incTo) continue;
                if (key.contains("date") && !incDate) continue;
                if ((key.contains("cc") || key.contains("bcc")) && !incCcBcc) continue;
                pw.println(entry.getKey() + ": " + entry.getValue());
            }
        }
        if (msg.getAttachments() != null && !msg.getAttachments().isEmpty()) {
            pw.println("Attachments: " + String.join(", ", msg.getAttachments()));
        }
        pw.println();
        if (incBody) {
            pw.println(OutputHandler.getPlainTextBody(msg.getBody()));
        }
    }
}

package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class RtfOutputHandler implements OutputHandler {
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
            File file = new File(targetFolder, targetFolder.getName() + ".rtf");
            try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println("{\\rtf1\\ansi\\deff0");
                pw.println("{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}");
                pw.println("\\viewkind4\\uc1\\pard\\lang1033\\f0\\fs24");
                int index = 1;
                for (MailMessage msg : emails) {
                    pw.println("{\\b Email #" + index + "}\\par");
                    if (incSubject) {
                        pw.println("{\\b Subject:} " + OutputHandler.escapeRtf(msg.getSubject()) + "\\par");
                    }
                    if (incFrom) {
                        pw.println("{\\b From:} " + OutputHandler.escapeRtf(msg.getFrom()) + "\\par");
                        String senderAddr = msg.getSenderAddress();
                        if (senderAddr != null && !senderAddr.isEmpty()) {
                            pw.println("{\\b Sender Address:} " + OutputHandler.escapeRtf(senderAddr) + "\\par");
                        }
                    }
                    if (incTo) {
                        String displayTo = msg.getTo();
                        if (displayTo != null && !displayTo.isEmpty()) {
                            pw.println("{\\b To:} " + OutputHandler.escapeRtf(displayTo) + "\\par");
                        }
                    }
                    if (incCcBcc) {
                        String cc = msg.getCc();
                        if (cc != null && !cc.isEmpty()) {
                            pw.println("{\\b Cc:} " + OutputHandler.escapeRtf(cc) + "\\par");
                        }
                        String bcc = msg.getBcc();
                        if (bcc != null && !bcc.isEmpty()) {
                            pw.println("{\\b Bcc:} " + OutputHandler.escapeRtf(bcc) + "\\par");
                        }
                    }
                    if (incDate) {
                        pw.println("{\\b Date:} " + OutputHandler.escapeRtf(msg.getDate()) + "\\par");
                    }
                    if (incSubject || incFrom || incTo || incCcBcc || incDate) {
                        String msgId = msg.getMessageId();
                        if (msgId != null && !msgId.isEmpty()) {
                            pw.println("{\\b Message-ID:} " + OutputHandler.escapeRtf(msgId) + "\\par");
                        }
                        String status = msg.getStatus();
                        if (status != null && !status.isEmpty()) {
                            pw.println("{\\b Status:} " + OutputHandler.escapeRtf(status) + "\\par");
                        }
                        String importance = msg.getImportance();
                        if (importance != null && !importance.isEmpty()) {
                            pw.println("{\\b Importance:} " + OutputHandler.escapeRtf(importance) + "\\par");
                        }
                        String flags = msg.getMessageFlags();
                        if (flags != null && !flags.isEmpty() && !flags.equals("None")) {
                            pw.println("{\\b Flags:} " + OutputHandler.escapeRtf(flags) + "\\par");
                        }
                    }
                    pw.println("\\pard\\brdrb\\brdrs\\brdrw10\\brsp20\\par");
                    pw.println("\\pard\\fs20\\par");
                    if (incBody) {
                        String body = OutputHandler.getPlainTextBody(msg.getBody());
                        String[] lines = body.split("\\r?\\n");
                        for (String line : lines) {
                            pw.println(OutputHandler.escapeRtf(line) + "\\par");
                        }
                    }
                    pw.println("\\page\\par"); // Page break
                    
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    index++;
                }
                pw.println("}");
            } catch (Exception e) {
                System.err.println("Error exporting to combined RTF: " + e.getMessage());
                throw e;
            }
        } else {
            int index = 1;
            for (MailMessage msg : emails) {
                String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "rtf");
                File file = new File(targetFolder, fileName);
                try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    pw.println("{\\rtf1\\ansi\\deff0");
                    pw.println("{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}");
                    pw.println("\\viewkind4\\uc1\\pard\\lang1033\\f0\\fs24");
                    if (incSubject) {
                        pw.println("{\\b Subject:} " + OutputHandler.escapeRtf(msg.getSubject()) + "\\par");
                    }
                    if (incFrom) {
                        pw.println("{\\b From:} " + OutputHandler.escapeRtf(msg.getFrom()) + "\\par");
                        String senderAddr = msg.getSenderAddress();
                        if (senderAddr != null && !senderAddr.isEmpty()) {
                            pw.println("{\\b Sender Address:} " + OutputHandler.escapeRtf(senderAddr) + "\\par");
                        }
                    }
                    if (incTo) {
                        String displayTo = msg.getTo();
                        if (displayTo != null && !displayTo.isEmpty()) {
                            pw.println("{\\b To:} " + OutputHandler.escapeRtf(displayTo) + "\\par");
                        }
                    }
                    if (incCcBcc) {
                        String cc = msg.getCc();
                        if (cc != null && !cc.isEmpty()) {
                            pw.println("{\\b Cc:} " + OutputHandler.escapeRtf(cc) + "\\par");
                        }
                        String bcc = msg.getBcc();
                        if (bcc != null && !bcc.isEmpty()) {
                            pw.println("{\\b Bcc:} " + OutputHandler.escapeRtf(bcc) + "\\par");
                        }
                    }
                    if (incDate) {
                        pw.println("{\\b Date:} " + OutputHandler.escapeRtf(msg.getDate()) + "\\par");
                    }
                    if (incSubject || incFrom || incTo || incCcBcc || incDate) {
                        String msgId = msg.getMessageId();
                        if (msgId != null && !msgId.isEmpty()) {
                            pw.println("{\\b Message-ID:} " + OutputHandler.escapeRtf(msgId) + "\\par");
                        }
                        String status = msg.getStatus();
                        if (status != null && !status.isEmpty()) {
                            pw.println("{\\b Status:} " + OutputHandler.escapeRtf(status) + "\\par");
                        }
                        String importance = msg.getImportance();
                        if (importance != null && !importance.isEmpty()) {
                            pw.println("{\\b Importance:} " + OutputHandler.escapeRtf(importance) + "\\par");
                        }
                        String flags = msg.getMessageFlags();
                        if (flags != null && !flags.isEmpty() && !flags.equals("None")) {
                            pw.println("{\\b Flags:} " + OutputHandler.escapeRtf(flags) + "\\par");
                        }
                    }
                    pw.println("\\pard\\brdrb\\brdrs\\brdrw10\\brsp20\\par");
                    pw.println("\\pard\\fs20\\par");
                    if (incBody) {
                        String body = OutputHandler.getPlainTextBody(msg.getBody());
                        String[] lines = body.split("\\r?\\n");
                        for (String line : lines) {
                            pw.println(OutputHandler.escapeRtf(line) + "\\par");
                        }
                    }
                    pw.println("}");
                }
                
                // Save attachment files
                String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                
                index++;
            }
        }
    }
}

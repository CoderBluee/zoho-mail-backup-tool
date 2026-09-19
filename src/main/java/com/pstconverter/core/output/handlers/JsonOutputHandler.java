package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class JsonOutputHandler implements OutputHandler {
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
            File file = new File(targetFolder, targetFolder.getName() + ".json");
            try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println("[");
                int index = 1;
                for (int idx = 0; idx < emails.size(); idx++) {
                    MailMessage msg = emails.get(idx);
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    List<String> attachmentPaths = OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    
                    pw.println("  {");
                    java.util.List<String> parts = new java.util.ArrayList<>();
                    if (incSubject) {
                        parts.add("\"subject\": \"" + OutputHandler.escapeJson(msg.getSubject()) + "\"");
                    }
                    if (incFrom) {
                        parts.add("\"from\": \"" + OutputHandler.escapeJson(msg.getFrom()) + "\"");
                        parts.add("\"sender_address\": \"" + OutputHandler.escapeJson(msg.getSenderAddress()) + "\"");
                    }
                    if (incTo) {
                        parts.add("\"to\": \"" + OutputHandler.escapeJson(msg.getTo()) + "\"");
                    }
                    if (incCcBcc) {
                        parts.add("\"cc\": \"" + OutputHandler.escapeJson(msg.getCc()) + "\"");
                        parts.add("\"bcc\": \"" + OutputHandler.escapeJson(msg.getBcc()) + "\"");
                    }
                    if (incDate) {
                        parts.add("\"date\": \"" + OutputHandler.escapeJson(msg.getDate()) + "\"");
                        parts.add("\"message_id\": \"" + OutputHandler.escapeJson(msg.getMessageId()) + "\"");
                    }
                    if (incSubject) {
                        parts.add("\"status\": \"" + OutputHandler.escapeJson(msg.getStatus()) + "\"");
                        parts.add("\"importance\": \"" + OutputHandler.escapeJson(msg.getImportance()) + "\"");
                        parts.add("\"message_flags\": \"" + OutputHandler.escapeJson(msg.getMessageFlags()) + "\"");
                    }
                    if (incBody) {
                        parts.add("\"body\": \"" + OutputHandler.escapeJson(OutputHandler.getPlainTextBody(msg.getBody())) + "\"");
                    }
                    
                    java.lang.StringBuilder attBuilder = new java.lang.StringBuilder();
                    attBuilder.append("\"attachments\": [");
                    for (int i = 0; i < attachmentPaths.size(); i++) {
                        attBuilder.append("\"").append(OutputHandler.escapeJson(attachmentPaths.get(i))).append("\"");
                        if (i < attachmentPaths.size() - 1) {
                            attBuilder.append(", ");
                        }
                    }
                    attBuilder.append("]");
                    parts.add(attBuilder.toString());
                    
                    pw.println("    " + String.join(",\n    ", parts));
                    pw.print("  }");
                    if (idx < emails.size() - 1) {
                        pw.println(",");
                    } else {
                        pw.println();
                    }
                    index++;
                }
                pw.println("]");
            } catch (Exception e) {
                System.err.println("Error exporting to combined JSON: " + e.getMessage());
                throw e;
            }
        } else {
            int index = 1;
            for (MailMessage msg : emails) {
                String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "json");
                File file = new File(targetFolder, fileName);
                
                String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                List<String> attachmentPaths = OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                
                try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    pw.println("{");
                    java.util.List<String> parts = new java.util.ArrayList<>();
                    if (incSubject) {
                        parts.add("\"subject\": \"" + OutputHandler.escapeJson(msg.getSubject()) + "\"");
                    }
                    if (incFrom) {
                        parts.add("\"from\": \"" + OutputHandler.escapeJson(msg.getFrom()) + "\"");
                        parts.add("\"sender_address\": \"" + OutputHandler.escapeJson(msg.getSenderAddress()) + "\"");
                    }
                    if (incTo) {
                        parts.add("\"to\": \"" + OutputHandler.escapeJson(msg.getTo()) + "\"");
                    }
                    if (incCcBcc) {
                        parts.add("\"cc\": \"" + OutputHandler.escapeJson(msg.getCc()) + "\"");
                        parts.add("\"bcc\": \"" + OutputHandler.escapeJson(msg.getBcc()) + "\"");
                    }
                    if (incDate) {
                        parts.add("\"date\": \"" + OutputHandler.escapeJson(msg.getDate()) + "\"");
                        parts.add("\"message_id\": \"" + OutputHandler.escapeJson(msg.getMessageId()) + "\"");
                    }
                    if (incSubject) {
                        parts.add("\"status\": \"" + OutputHandler.escapeJson(msg.getStatus()) + "\"");
                        parts.add("\"importance\": \"" + OutputHandler.escapeJson(msg.getImportance()) + "\"");
                        parts.add("\"message_flags\": \"" + OutputHandler.escapeJson(msg.getMessageFlags()) + "\"");
                    }
                    if (incBody) {
                        parts.add("\"body\": \"" + OutputHandler.escapeJson(OutputHandler.getPlainTextBody(msg.getBody())) + "\"");
                    }
                    
                    java.lang.StringBuilder attBuilder = new java.lang.StringBuilder();
                    attBuilder.append("\"attachments\": [");
                    for (int i = 0; i < attachmentPaths.size(); i++) {
                        attBuilder.append("\"").append(OutputHandler.escapeJson(attachmentPaths.get(i))).append("\"");
                        if (i < attachmentPaths.size() - 1) {
                            attBuilder.append(", ");
                        }
                    }
                    attBuilder.append("]");
                    parts.add(attBuilder.toString());
                    
                    pw.println("  " + String.join(",\n  ", parts));
                    pw.println("}");
                    index++;
                } catch (Exception e) {
                    System.err.println("Error exporting to JSON: " + e.getMessage());
                    throw e;
                }
            }
        }
    }

    @Override
    public OutputHandler.Session openSession(File targetFolder, String attachmentHandling, String exportStructure, String namingConvention) throws Exception {
        boolean isCombined = exportStructure != null && exportStructure.startsWith("Single Combined File");
        
        boolean incSubject = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_subject", "true"));
        boolean incFrom = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_from", "true"));
        boolean incTo = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_to", "true"));
        boolean incCcBcc = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_ccbcc", "true"));
        boolean incDate = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_date", "true"));
        boolean incBody = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_body", "true"));

        if (isCombined) {
            File file = new File(targetFolder, targetFolder.getName() + ".json");
            PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8);
            pw.println("[");
            return new OutputHandler.Session() {
                private int index = 1;
                @Override
                public void writeMessage(MailMessage msg) throws Exception {
                    if (index > 1) {
                        pw.println(",");
                    }
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    List<String> attachmentPaths = OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    
                    pw.println("  {");
                    java.util.List<String> parts = new java.util.ArrayList<>();
                    if (incSubject) {
                        parts.add("\"subject\": \"" + OutputHandler.escapeJson(msg.getSubject()) + "\"");
                    }
                    if (incFrom) {
                        parts.add("\"from\": \"" + OutputHandler.escapeJson(msg.getFrom()) + "\"");
                        parts.add("\"sender_address\": \"" + OutputHandler.escapeJson(msg.getSenderAddress()) + "\"");
                    }
                    if (incTo) {
                        parts.add("\"to\": \"" + OutputHandler.escapeJson(msg.getTo()) + "\"");
                    }
                    if (incCcBcc) {
                        parts.add("\"cc\": \"" + OutputHandler.escapeJson(msg.getCc()) + "\"");
                        parts.add("\"bcc\": \"" + OutputHandler.escapeJson(msg.getBcc()) + "\"");
                    }
                    if (incDate) {
                        parts.add("\"date\": \"" + OutputHandler.escapeJson(msg.getDate()) + "\"");
                        parts.add("\"message_id\": \"" + OutputHandler.escapeJson(msg.getMessageId()) + "\"");
                    }
                    if (incSubject) {
                        parts.add("\"status\": \"" + OutputHandler.escapeJson(msg.getStatus()) + "\"");
                        parts.add("\"importance\": \"" + OutputHandler.escapeJson(msg.getImportance()) + "\"");
                        parts.add("\"message_flags\": \"" + OutputHandler.escapeJson(msg.getMessageFlags()) + "\"");
                    }
                    if (incBody) {
                        parts.add("\"body\": \"" + OutputHandler.escapeJson(OutputHandler.getPlainTextBody(msg.getBody())) + "\"");
                    }
                    
                    java.lang.StringBuilder attBuilder = new java.lang.StringBuilder();
                    attBuilder.append("\"attachments\": [");
                    for (int i = 0; i < attachmentPaths.size(); i++) {
                        attBuilder.append("\"").append(OutputHandler.escapeJson(attachmentPaths.get(i))).append("\"");
                        if (i < attachmentPaths.size() - 1) {
                            attBuilder.append(", ");
                        }
                    }
                    attBuilder.append("]");
                    parts.add(attBuilder.toString());
                    
                    pw.println("    " + String.join(",\n    ", parts));
                    pw.print("  }");
                    index++;
                }

                @Override
                public void close() throws Exception {
                    pw.println();
                    pw.println("]");
                    pw.close();
                }
            };
        } else {
            return new OutputHandler.Session() {
                private int index = 1;
                @Override
                public void writeMessage(MailMessage msg) throws Exception {
                    String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "json");
                    File file = new File(targetFolder, fileName);
                    
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    List<String> attachmentPaths = OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    
                    try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                        pw.println("{");
                        java.util.List<String> parts = new java.util.ArrayList<>();
                        if (incSubject) {
                            parts.add("\"subject\": \"" + OutputHandler.escapeJson(msg.getSubject()) + "\"");
                        }
                        if (incFrom) {
                            parts.add("\"from\": \"" + OutputHandler.escapeJson(msg.getFrom()) + "\"");
                            parts.add("\"sender_address\": \"" + OutputHandler.escapeJson(msg.getSenderAddress()) + "\"");
                        }
                        if (incTo) {
                            parts.add("\"to\": \"" + OutputHandler.escapeJson(msg.getTo()) + "\"");
                        }
                        if (incCcBcc) {
                            parts.add("\"cc\": \"" + OutputHandler.escapeJson(msg.getCc()) + "\"");
                            parts.add("\"bcc\": \"" + OutputHandler.escapeJson(msg.getBcc()) + "\"");
                        }
                        if (incDate) {
                            parts.add("\"date\": \"" + OutputHandler.escapeJson(msg.getDate()) + "\"");
                            parts.add("\"message_id\": \"" + OutputHandler.escapeJson(msg.getMessageId()) + "\"");
                        }
                        if (incSubject) {
                            parts.add("\"status\": \"" + OutputHandler.escapeJson(msg.getStatus()) + "\"");
                            parts.add("\"importance\": \"" + OutputHandler.escapeJson(msg.getImportance()) + "\"");
                            parts.add("\"message_flags\": \"" + OutputHandler.escapeJson(msg.getMessageFlags()) + "\"");
                        }
                        if (incBody) {
                            parts.add("\"body\": \"" + OutputHandler.escapeJson(OutputHandler.getPlainTextBody(msg.getBody())) + "\"");
                        }
                        
                        java.lang.StringBuilder attBuilder = new java.lang.StringBuilder();
                        attBuilder.append("\"attachments\": [");
                        for (int i = 0; i < attachmentPaths.size(); i++) {
                            attBuilder.append("\"").append(OutputHandler.escapeJson(attachmentPaths.get(i))).append("\"");
                            if (i < attachmentPaths.size() - 1) {
                                attBuilder.append(", ");
                            }
                        }
                        attBuilder.append("]");
                        parts.add(attBuilder.toString());
                        
                        pw.println("  " + String.join(",\n  ", parts));
                        pw.println("}");
                    }
                    index++;
                }

                @Override
                public void close() throws Exception {
                }
            };
        }
    }
}

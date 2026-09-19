package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class CsvOutputHandler implements OutputHandler {
    @Override
    public void export(File targetFolder, List<MailMessage> emails) throws Exception {
        export(targetFolder, emails, "Keep Attachments in Folder");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling) throws Exception {
        export(targetFolder, emails, attachmentHandling, "Single Combined File per Folder (All emails in one file)");
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
            File file = new File(targetFolder, targetFolder.getName() + ".csv");
            try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println(getHeaderLine(incSubject, incFrom, incTo, incCcBcc, incDate, incBody));
                int index = 1;
                for (MailMessage msg : emails) {
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    List<String> attachmentPaths = OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    String attachmentsStr = String.join(";", attachmentPaths);

                    pw.println(getRowLine(msg, attachmentsStr, incSubject, incFrom, incTo, incCcBcc, incDate, incBody));
                    index++;
                }
            } catch (Exception e) {
                System.err.println("Error exporting to CSV: " + e.getMessage());
                throw e;
            }
        } else {
            int index = 1;
            for (MailMessage msg : emails) {
                String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "csv");
                File file = new File(targetFolder, fileName);
                
                String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                List<String> attachmentPaths = OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                String attachmentsStr = String.join(";", attachmentPaths);

                try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    pw.println(getHeaderLine(incSubject, incFrom, incTo, incCcBcc, incDate, incBody));
                    pw.println(getRowLine(msg, attachmentsStr, incSubject, incFrom, incTo, incCcBcc, incDate, incBody));
                    index++;
                } catch (Exception e) {
                    System.err.println("Error exporting to CSV: " + e.getMessage());
                    throw e;
                }
            }
        }
    }

    private String getHeaderLine(boolean incSubject, boolean incFrom, boolean incTo, boolean incCcBcc, boolean incDate, boolean incBody) {
        java.util.List<String> headers = new java.util.ArrayList<>();
        if (incSubject) {
            headers.add("\"Subject\"");
        }
        if (incFrom) {
            headers.add("\"From\"");
            headers.add("\"Sender Address\"");
        }
        if (incTo) {
            headers.add("\"To\"");
        }
        if (incCcBcc) {
            headers.add("\"Cc\"");
            headers.add("\"Bcc\"");
        }
        if (incDate) {
            headers.add("\"Date\"");
            headers.add("\"Message-ID\"");
        }
        if (incSubject) {
            headers.add("\"Status\"");
            headers.add("\"Importance\"");
            headers.add("\"Flags\"");
        }
        if (incBody) {
            headers.add("\"Body\"");
        }
        headers.add("\"Attachments Path\"");
        return String.join(",", headers);
    }

    private String getRowLine(MailMessage msg, String attachmentsStr, boolean incSubject, boolean incFrom, boolean incTo, boolean incCcBcc, boolean incDate, boolean incBody) {
        java.util.List<String> values = new java.util.ArrayList<>();
        if (incSubject) {
            values.add("\"" + OutputHandler.escapeCsv(msg.getSubject()) + "\"");
        }
        if (incFrom) {
            values.add("\"" + OutputHandler.escapeCsv(msg.getFrom()) + "\"");
            values.add("\"" + OutputHandler.escapeCsv(msg.getSenderAddress()) + "\"");
        }
        if (incTo) {
            values.add("\"" + OutputHandler.escapeCsv(msg.getTo()) + "\"");
        }
        if (incCcBcc) {
            values.add("\"" + OutputHandler.escapeCsv(msg.getCc()) + "\"");
            values.add("\"" + OutputHandler.escapeCsv(msg.getBcc()) + "\"");
        }
        if (incDate) {
            values.add("\"" + OutputHandler.escapeCsv(msg.getDate()) + "\"");
            values.add("\"" + OutputHandler.escapeCsv(msg.getMessageId()) + "\"");
        }
        if (incSubject) {
            values.add("\"" + OutputHandler.escapeCsv(msg.getStatus()) + "\"");
            values.add("\"" + OutputHandler.escapeCsv(msg.getImportance()) + "\"");
            values.add("\"" + OutputHandler.escapeCsv(msg.getMessageFlags()) + "\"");
        }
        if (incBody) {
            values.add("\"" + OutputHandler.escapeCsv(OutputHandler.getPlainTextBody(msg.getBody())) + "\"");
        }
        values.add("\"" + OutputHandler.escapeCsv(attachmentsStr) + "\"");
        return String.join(",", values);
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
            File file = new File(targetFolder, targetFolder.getName() + ".csv");
            PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8);
            pw.println(getHeaderLine(incSubject, incFrom, incTo, incCcBcc, incDate, incBody));
            return new OutputHandler.Session() {
                private int index = 1;
                @Override
                public void writeMessage(MailMessage msg) throws Exception {
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    List<String> attachmentPaths = OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    String attachmentsStr = String.join(";", attachmentPaths);

                    pw.println(getRowLine(msg, attachmentsStr, incSubject, incFrom, incTo, incCcBcc, incDate, incBody));
                    index++;
                }

                @Override
                public void close() throws Exception {
                    pw.close();
                }
            };
        } else {
            return new OutputHandler.Session() {
                private int index = 1;
                @Override
                public void writeMessage(MailMessage msg) throws Exception {
                    String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "csv");
                    File file = new File(targetFolder, fileName);
                    
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    List<String> attachmentPaths = OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling);
                    String attachmentsStr = String.join(";", attachmentPaths);

                    try (PrintWriter pw = new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                        pw.println(getHeaderLine(incSubject, incFrom, incTo, incCcBcc, incDate, incBody));
                        pw.println(getRowLine(msg, attachmentsStr, incSubject, incFrom, incTo, incCcBcc, incDate, incBody));
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

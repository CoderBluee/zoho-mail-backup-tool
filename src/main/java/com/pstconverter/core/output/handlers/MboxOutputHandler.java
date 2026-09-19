package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class MboxOutputHandler implements OutputHandler {
    @Override
    public void export(File targetFolder, List<MailMessage> emails) throws Exception {
        export(targetFolder, emails, "Keep Attachments in Folder");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling) throws Exception {
        export(targetFolder, emails, attachmentHandling, "Generic \"messages.mbox\" inside Folder Subdirectory (Default)");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling, String exportStructure) throws Exception {
        export(targetFolder, emails, attachmentHandling, exportStructure, "Original Subject");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling, String exportStructure, String namingConvention) throws Exception {
        if (emails == null || emails.isEmpty()) return;
        boolean shouldEmbed = attachmentHandling != null && attachmentHandling.contains("Embed Attachments");
        
        File file;
        if (exportStructure != null && exportStructure.equalsIgnoreCase("Single Monolithic MBOX for Entire Migration")) {
            String rootPath = System.getProperty("active_export_output_path");
            File rootDir = (rootPath != null) ? new File(rootPath) : targetFolder;
            if (!rootDir.exists()) {
                rootDir.mkdirs();
            }
            file = new File(rootDir, "migration.mbox");
        } else if (exportStructure != null && exportStructure.contains("FolderName.mbox")) {
            File parentDir = targetFolder.getParentFile();
            if (parentDir == null) {
                parentDir = targetFolder;
            }
            file = new File(parentDir, targetFolder.getName() + ".mbox");
        } else {
            file = new File(targetFolder, "messages.mbox");
        }

        boolean append = exportStructure != null && exportStructure.equalsIgnoreCase("Single Monolithic MBOX for Entire Migration");

        try (PrintWriter pw = new PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(file, append), java.nio.charset.StandardCharsets.UTF_8))) {
            for (MailMessage msg : emails) {
                String fromEmail = OutputHandler.extractEmail(msg.getFrom());
                pw.println("From " + fromEmail + " " + OutputHandler.convertToAscTime(msg.getDate()));
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
                    String body = OutputHandler.getPlainTextBody(msg.getBody());
                    String[] bodyLines = body.split("\\r?\\n");
                    for (String line : bodyLines) {
                        if (line.startsWith("From ")) {
                            pw.println(">" + line);
                        } else {
                            pw.println(line);
                        }
                    }
                }
                pw.println();
            }
        } catch (Exception e) {
            System.err.println("Error exporting to MBOX: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public OutputHandler.Session openSession(File targetFolder, String attachmentHandling, String exportStructure, String namingConvention) throws Exception {
        boolean shouldEmbed = attachmentHandling != null && attachmentHandling.contains("Embed Attachments");
        
        File file;
        boolean appendMode = false;
        if (exportStructure != null && exportStructure.equalsIgnoreCase("Single Monolithic MBOX for Entire Migration")) {
            String rootPath = System.getProperty("active_export_output_path");
            File rootDir = (rootPath != null) ? new File(rootPath) : targetFolder;
            if (!rootDir.exists()) {
                rootDir.mkdirs();
            }
            file = new File(rootDir, "migration.mbox");
            appendMode = true;
        } else if (exportStructure != null && exportStructure.contains("FolderName.mbox")) {
            File parentDir = targetFolder.getParentFile();
            if (parentDir == null) {
                parentDir = targetFolder;
            }
            file = new File(parentDir, targetFolder.getName() + ".mbox");
        } else {
            file = new File(targetFolder, "messages.mbox");
        }

        PrintWriter pw = new PrintWriter(new java.io.OutputStreamWriter(
            new java.io.FileOutputStream(file, appendMode), java.nio.charset.StandardCharsets.UTF_8));

        return new OutputHandler.Session() {
            @Override
            public void writeMessage(MailMessage msg) throws Exception {
                String fromEmail = OutputHandler.extractEmail(msg.getFrom());
                pw.println("From " + fromEmail + " " + OutputHandler.convertToAscTime(msg.getDate()));
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
                    String body = OutputHandler.getPlainTextBody(msg.getBody());
                    String[] bodyLines = body.split("\\r?\\n");
                    for (String line : bodyLines) {
                        if (line.startsWith("From ")) {
                            pw.println(">" + line);
                        } else {
                            pw.println(line);
                        }
                    }
                }
                pw.println();
            }

            @Override
            public void close() throws Exception {
                pw.close();
            }
        };
    }

    @Override
    public void cleanMonolithicFiles(File targetFolder, String exportStructure) {
        boolean isMonolithic = exportStructure != null && exportStructure.equalsIgnoreCase("Single Monolithic MBOX for Entire Migration");
        if (isMonolithic) {
            String rootPath = System.getProperty("active_export_output_path");
            File rootDir = (rootPath != null) ? new File(rootPath) : targetFolder;
            File file = new File(rootDir, "migration.mbox");
            if (file.exists()) {
                file.delete();
            }
        }
    }
}


package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;

import java.io.File;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;

public class CloudOutputHandler implements OutputHandler {
    private final String cloudFormatName;

    public CloudOutputHandler(String cloudFormatName) {
        this.cloudFormatName = cloudFormatName;
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails) throws Exception {
        if (emails == null || emails.isEmpty()) return;
        
        int retryLimit = 3;
        try {
            retryLimit = Integer.parseInt(com.pstconverter.util.SettingsManager.getSetting("retry_count", "3"));
        } catch (Exception ignored) {}

        int timeoutSec = 30;
        try {
            timeoutSec = Integer.parseInt(com.pstconverter.util.SettingsManager.getSetting("connection_timeout", "30"));
        } catch (Exception ignored) {}

        String throttleSetting = com.pstconverter.util.SettingsManager.getSetting("network_throttle", "Unlimited");

        System.out.println("[INFO] CloudOutputHandler: Initializing mock upload to " + cloudFormatName);
        System.out.println("[INFO] CloudOutputHandler: Connection Timeout = " + timeoutSec + "s, Retry Limit = " + retryLimit + ", Throttle = " + throttleSetting);

        // Simulate connection latency
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {}

        File infoFile = new File(targetFolder, "cloud_migration_instructions.txt");
        try (PrintWriter pw = new PrintWriter(infoFile, java.nio.charset.StandardCharsets.UTF_8)) {
            pw.println("Cloud Migration target: " + cloudFormatName);
            pw.println("Status: Mock upload initialized");
            pw.println("Timestamp: " + new Date());
            pw.println("Connection Timeout (sec): " + timeoutSec);
            pw.println("Max Connection Retries: " + retryLimit);
            pw.println("Network Throttle Rule: " + throttleSetting);
            pw.println("Number of messages synced: " + emails.size());
            pw.println("\n--- Messages uploaded to server ---");
            
            for (MailMessage msg : emails) {
                // Determine message size
                long msgSize = msg.getBody() != null ? msg.getBody().length() : 0;
                if (msg.getAttachmentList() != null) {
                    for (var att : msg.getAttachmentList()) {
                        if (att.getData() != null) {
                            msgSize += att.getData().length;
                        }
                    }
                }
                
                // Emulate throttling delay
                if (!throttleSetting.equalsIgnoreCase("Unlimited") && msgSize > 0) {
                    long bytesPerSec = 1024 * 1024;
                    if (throttleSetting.contains("512 KB")) bytesPerSec = 512 * 1024;
                    else if (throttleSetting.contains("5 MB")) bytesPerSec = 5 * 1024 * 1024;
                    else if (throttleSetting.contains("10 MB")) bytesPerSec = 10 * 1024 * 1024;
                    
                    double secs = (double) msgSize / bytesPerSec;
                    long ms = (long) (secs * 1000.0);
                    if (ms > 0) {
                        try {
                            Thread.sleep(Math.min(ms, 500)); // cap mock delay to keep execution snappy but realistic
                        } catch (InterruptedException ignored) {}
                    }
                }

                pw.println("Uploaded: " + msg.getSubject() + " (From: " + msg.getFrom() + ") [" + msgSize + " bytes]");
                System.out.println("[INFO] CloudOutputHandler: Mock uploaded \"" + msg.getSubject() + "\" (" + msgSize + " bytes)");
            }
        } catch (Exception e) {
            System.err.println("Error creating cloud migration file: " + e.getMessage());
            throw e;
        }
    }
}

package com.pstconverter.util;

import com.pstconverter.model.MailMessage;
import com.pstconverter.core.output.handlers.PstOutputHandler;
import com.pstconverter.core.output.OutputHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ValidatePstSplit {
    public static void main(String[] args) {
        System.out.println("=== Starting PST Split Validation ===");
        try {
            File targetFolder = new File("target/validation_pst_split");
            if (targetFolder.exists()) {
                deleteDir(targetFolder);
            }
            targetFolder.mkdirs();

            // 1. Create a large list of mock emails with sizable bodies to trigger split
            List<MailMessage> emails = new ArrayList<>();
            for (int i = 1; i <= 20; i++) {
                String body = "This is body line of message #" + i + " to add weight. " +
                        "Repeating content to ensure we exceed split boundary easily. " +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(10);
                MailMessage msg = new MailMessage("sender@example.com", "Test Email Subject #" + i, "Thu, 11 Jun 2026 12:00:00 +0530", body);
                msg.addMetadata("Message-ID", "<msg-" + i + "@example.com>");
                msg.setMessageId("<msg-" + i + "@example.com>");
                emails.add(msg);
            }

            // Set split properties
            System.setProperty("active_export_split_pst", "true");
            System.setProperty("active_export_split_size", "2 KB"); // Low limit to force splitting
            System.setProperty("active_export_output_path", targetFolder.getAbsolutePath());

            boolean allPassed = true;

            try {
                // TEST A: Monolithic Export Split
                System.out.println("Testing Monolithic PST split...");
                PstOutputHandler handler = new PstOutputHandler();
                handler.export(targetFolder, emails, "Keep Attachments in Folder", "Single Monolithic PST File (Entire Migration - Default)", "Original Subject");

                // Verify parts were created
                File part1 = new File(targetFolder, "PST_Export_part1.pst");
                File part2 = new File(targetFolder, "PST_Export_part2.pst");

                if (part1.exists() && part1.length() > 0 && part2.exists() && part2.length() > 0) {
                    System.out.println("[PASS] Monolithic split created part1 and part2 successfully!");
                } else {
                    System.out.println("[FAIL] Monolithic split failed to create multiple parts! Part1 exists: " + part1.exists() + ", Part2 exists: " + part2.exists());
                    allPassed = false;
                }

                // TEST B: Clean monolithic files
                System.out.println("Testing Monolithic clean-up...");
                handler.cleanMonolithicFiles(targetFolder, "Single Monolithic PST File (Entire Migration - Default)");
                if (!part1.exists() && !part2.exists()) {
                    System.out.println("[PASS] Monolithic split clean-up successfully deleted parts!");
                } else {
                    System.out.println("[FAIL] Monolithic split clean-up did not delete all parts!");
                    allPassed = false;
                }

                // TEST C: Folder PST Session Split
                System.out.println("Testing Folder PST Session split...");
                File subFolder = new File(targetFolder, "Inbox");
                subFolder.mkdirs();

                OutputHandler.Session session = handler.openSession(subFolder, "Keep Attachments in Folder", "PST per Folder", "Original Subject");
                for (MailMessage msg : emails) {
                    session.writeMessage(msg);
                }
                session.close();

                File folderPart1 = new File(subFolder, "Inbox_part1.pst");
                File folderPart2 = new File(subFolder, "Inbox_part2.pst");

                if (folderPart1.exists() && folderPart1.length() > 0 && folderPart2.exists() && folderPart2.length() > 0) {
                    System.out.println("[PASS] Folder PST Session split created multiple parts successfully!");
                } else {
                    System.out.println("[FAIL] Folder PST Session split failed! Part1 exists: " + folderPart1.exists() + ", Part2 exists: " + folderPart2.exists());
                    allPassed = false;
                }

                // TEST D: Clean folder-based files
                System.out.println("Testing Folder PST clean-up...");
                handler.cleanMonolithicFiles(targetFolder, "PST per Folder");
                if (!folderPart1.exists() && !folderPart2.exists()) {
                    System.out.println("[PASS] Folder PST clean-up deleted recursive .pst files successfully!");
                } else {
                    System.out.println("[FAIL] Folder PST clean-up did not delete recursive .pst files!");
                    allPassed = false;
                }

            } finally {
                System.clearProperty("active_export_split_pst");
                System.clearProperty("active_export_split_size");
                System.clearProperty("active_export_output_path");
            }

            if (allPassed) {
                System.out.println("=== PST SPLIT VALIDATION SUCCESS ===");
            } else {
                System.out.println("=== PST SPLIT VALIDATION FAILURE ===");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("PST Split Validation failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }
}

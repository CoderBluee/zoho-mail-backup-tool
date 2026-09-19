package com.pstconverter.util;

import com.pstconverter.model.MailMessage;
import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.core.output.handlers.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ValidateMetadata {
    public static void main(String[] args) {
        System.out.println("=== Starting Metadata Migration Validation ===");
        try {
            // 1. Create a validation target folder
            File validationFolder = new File("target/validation_output");
            if (validationFolder.exists()) {
                deleteDir(validationFolder);
            }
            validationFolder.mkdirs();

            // 2. Build a mock MailMessage with all metadata fields populated
            MailMessage msg = new MailMessage("sender-name <sender-address@example.com>", "Validation Test Email", "Thu, 11 Jun 2026 12:00:00 +0530", "This is the body text of the validation email.");
            msg.addMetadata("Sender-Address", "sender-address@example.com");
            msg.addMetadata("To", "to-recipient@example.com");
            msg.addMetadata("Cc", "cc-recipient@example.com");
            msg.addMetadata("Bcc", "bcc-recipient@example.com");
            msg.addMetadata("Message-ID", "<unique-msg-id-12345@example.com>");
            msg.addMetadata("Status", "Read");
            msg.addMetadata("Importance", "High");
            msg.addMetadata("Message-Flags", "Replied, Flagged");
            msg.setSenderAddress("sender-address@example.com");
            msg.setTo("to-recipient@example.com");
            msg.setCc("cc-recipient@example.com");
            msg.setBcc("bcc-recipient@example.com");
            msg.setMessageId("<unique-msg-id-12345@example.com>");
            msg.setStatus("Read");
            msg.setImportance("High");
            msg.setMessageFlags("Replied, Flagged");

            List<MailMessage> list = new ArrayList<>();
            list.add(msg);

            // 3. Export using all 13 handlers
            System.out.println("Running exporters...");
            new PdfOutputHandler().export(validationFolder, list);
            new HtmlOutputHandler().export(validationFolder, list);
            new JsonOutputHandler().export(validationFolder, list);
            new CsvOutputHandler().export(validationFolder, list);
            new EmlOutputHandler().export(validationFolder, list);
            new MboxOutputHandler().export(validationFolder, list);
            new EmlxOutputHandler().export(validationFolder, list);
            new MsgOutputHandler().export(validationFolder, list);
            new MhtmlOutputHandler().export(validationFolder, list);
            new PstOutputHandler().export(validationFolder, list);
            new DocOutputHandler().export(validationFolder, list);
            new RtfOutputHandler().export(validationFolder, list);
            new TxtOutputHandler().export(validationFolder, list);
            new CloudOutputHandler("Office 365").export(validationFolder, list);
            new CloudOutputHandler("Gmail").export(validationFolder, list);
            new CloudOutputHandler("IMAP Server").export(validationFolder, list);
            new CloudOutputHandler("Yahoo Mail").export(validationFolder, list);
            System.out.println("Export completed successfully!");

            // 4. Verify presence of metadata fields in key files
            boolean allPassed = true;

            // Check JSON
            File jsonFile = new File(validationFolder, "1. Validation_Test_Email.json");
            if (verifyFileContains(jsonFile, "\"sender_address\": \"sender-address@example.com\"")
                    && verifyFileContains(jsonFile, "\"to\": \"to-recipient@example.com\"")
                    && verifyFileContains(jsonFile, "\"cc\": \"cc-recipient@example.com\"")
                    && verifyFileContains(jsonFile, "\"bcc\": \"bcc-recipient@example.com\"")
                    && verifyFileContains(jsonFile, "\"message_id\": \"<unique-msg-id-12345@example.com>\"")
                    && verifyFileContains(jsonFile, "\"status\": \"Read\"")
                    && verifyFileContains(jsonFile, "\"importance\": \"High\"")
                    && verifyFileContains(jsonFile, "\"message_flags\": \"Replied, Flagged\"")) {
                System.out.println("[PASS] JSON Output validation passed!");
            } else {
                System.out.println("[FAIL] JSON Output validation failed!");
                allPassed = false;
            }

            // Check HTML
            File htmlFile = new File(validationFolder, "1. Validation_Test_Email.html");
            if (verifyFileContains(htmlFile, "sender-address@example.com")
                    && verifyFileContains(htmlFile, "to-recipient@example.com")
                    && verifyFileContains(htmlFile, "cc-recipient@example.com")
                    && verifyFileContains(htmlFile, "bcc-recipient@example.com")
                    && verifyFileContains(htmlFile, "&lt;unique-msg-id-12345@example.com&gt;")
                    && verifyFileContains(htmlFile, "Read")
                    && verifyFileContains(htmlFile, "High")
                    && verifyFileContains(htmlFile, "Replied, Flagged")) {
                System.out.println("[PASS] HTML Output validation passed!");
            } else {
                System.out.println("[FAIL] HTML Output validation failed!");
                allPassed = false;
            }

            // Check CSV
            File csvFile = new File(validationFolder, "validation_output.csv");
            if (verifyFileContains(csvFile, "sender-address@example.com")
                    && verifyFileContains(csvFile, "to-recipient@example.com")
                    && verifyFileContains(csvFile, "cc-recipient@example.com")
                    && verifyFileContains(csvFile, "bcc-recipient@example.com")
                    && verifyFileContains(csvFile, "<unique-msg-id-12345@example.com>")
                    && verifyFileContains(csvFile, "Read")
                    && verifyFileContains(csvFile, "High")
                    && verifyFileContains(csvFile, "Replied, Flagged")) {
                System.out.println("[PASS] CSV Output validation passed!");
            } else {
                System.out.println("[FAIL] CSV Output validation failed!");
                allPassed = false;
            }

            // Check EML MIME
            File emlFile = new File(validationFolder, "1. Validation_Test_Email.eml");
            if (verifyFileContains(emlFile, "To: to-recipient@example.com")
                    && verifyFileContains(emlFile, "Cc: cc-recipient@example.com")
                    && verifyFileContains(emlFile, "Bcc: bcc-recipient@example.com")
                    && verifyFileContains(emlFile, "Message-ID: <unique-msg-id-12345@example.com>")
                    && verifyFileContains(emlFile, "Importance: High")
                    && verifyFileContains(emlFile, "X-Priority: 1")
                    && verifyFileContains(emlFile, "Status: R")) {
                System.out.println("[PASS] EML Output validation passed!");
            } else {
                System.out.println("[FAIL] EML Output validation failed!");
                allPassed = false;
            }

            // Check TXT Output
            File txtFile = new File(validationFolder, "1. Validation_Test_Email.txt");
            if (verifyFileContains(txtFile, "Sender-Address: sender-address@example.com")
                    && verifyFileContains(txtFile, "To: to-recipient@example.com")
                    && verifyFileContains(txtFile, "Cc: cc-recipient@example.com")
                    && verifyFileContains(txtFile, "Bcc: bcc-recipient@example.com")
                    && verifyFileContains(txtFile, "Message-ID: <unique-msg-id-12345@example.com>")
                    && verifyFileContains(txtFile, "Status: Read")
                    && verifyFileContains(txtFile, "Importance: High")
                    && verifyFileContains(txtFile, "Message-Flags: Replied, Flagged")) {
                System.out.println("[PASS] TXT Output validation passed!");
            } else {
                System.out.println("[FAIL] TXT Output validation failed!");
                allPassed = false;
            }

            // Check DOC Output
            File docFile = new File(validationFolder, "1. Validation_Test_Email.doc");
            if (verifyFileContains(docFile, "sender-address@example.com")
                    && verifyFileContains(docFile, "to-recipient@example.com")
                    && verifyFileContains(docFile, "cc-recipient@example.com")
                    && verifyFileContains(docFile, "bcc-recipient@example.com")
                    && verifyFileContains(docFile, "&lt;unique-msg-id-12345@example.com&gt;")
                    && verifyFileContains(docFile, "Read")
                    && verifyFileContains(docFile, "High")
                    && verifyFileContains(docFile, "Replied, Flagged")) {
                System.out.println("[PASS] DOC Output validation passed!");
            } else {
                System.out.println("[FAIL] DOC Output validation failed!");
                allPassed = false;
            }

            // Check RTF Output
            File rtfFile = new File(validationFolder, "1. Validation_Test_Email.rtf");
            if (verifyFileContains(rtfFile, "sender-address@example.com")
                    && verifyFileContains(rtfFile, "to-recipient@example.com")
                    && verifyFileContains(rtfFile, "cc-recipient@example.com")
                    && verifyFileContains(rtfFile, "bcc-recipient@example.com")
                    && verifyFileContains(rtfFile, "<unique-msg-id-12345@example.com>")
                    && verifyFileContains(rtfFile, "Read")
                    && verifyFileContains(rtfFile, "High")
                    && verifyFileContains(rtfFile, "Replied, Flagged")) {
                System.out.println("[PASS] RTF Output validation passed!");
            } else {
                System.out.println("[FAIL] RTF Output validation failed!");
                allPassed = false;
            }

            // Check MHTML Output
            File mhtmlFile = new File(validationFolder, "1. Validation_Test_Email.mhtml");
            if (verifyFileContains(mhtmlFile, "sender-address@example.com")
                    && verifyFileContains(mhtmlFile, "to-recipient@example.com")
                    && verifyFileContains(mhtmlFile, "cc-recipient@example.com")
                    && verifyFileContains(mhtmlFile, "bcc-recipient@example.com")
                    && verifyFileContains(mhtmlFile, "<unique-msg-id-12345@example.com>")
                    && verifyFileContains(mhtmlFile, "Read")
                    && verifyFileContains(mhtmlFile, "High")
                    && verifyFileContains(mhtmlFile, "Replied, Flagged")) {
                System.out.println("[PASS] MHTML Output validation passed!");
            } else {
                System.out.println("[FAIL] MHTML Output validation failed!");
                allPassed = false;
            }

            // Check Cloud Migration
            File cloudFile = new File(validationFolder, "cloud_migration_instructions.txt");
            if (verifyFileContains(cloudFile, "Cloud Migration target: Yahoo Mail")
                    && verifyFileContains(cloudFile, "Number of messages synced: 1")
                    && verifyFileContains(cloudFile, "Uploaded: Validation Test Email")) {
                System.out.println("[PASS] Cloud Migration validation passed!");
            } else {
                System.out.println("[FAIL] Cloud Migration validation failed!");
                allPassed = false;
            }

            // 5. Verify Naming Conventions
            System.out.println("Verifying naming conventions...");
            File namingFolder = new File("target/validation_naming");
            if (namingFolder.exists()) {
                deleteDir(namingFolder);
            }
            namingFolder.mkdirs();

            EmlOutputHandler emlHandler = new EmlOutputHandler();
            String[] conventions = {
                "Date + Subject",
                "From + Subject",
                "Date + From + Subject",
                "Subject + Date",
                "Original Subject"
            };

            for (String conv : conventions) {
                emlHandler.export(namingFolder, list, "Keep Attachments in Folder", "Individual File per Email (One file per message)", conv);
                String expectedName = OutputHandler.getFormattedFileName(msg, conv, 1, "eml");
                File checkFile = new File(namingFolder, expectedName);
                if (checkFile.exists() && checkFile.length() > 0) {
                    System.out.println("[PASS] Naming convention verified for: " + conv + " -> " + expectedName);
                } else {
                    System.out.println("[FAIL] Naming convention failed for: " + conv + ". Expected file: " + expectedName);
                    allPassed = false;
                }
            }

            // 6. Verify Monolithic Structure Output (MBOX / PST)
            System.out.println("Verifying monolithic output structure...");
            File monolithicFolder = new File("target/validation_monolithic");
            if (monolithicFolder.exists()) {
                deleteDir(monolithicFolder);
            }
            monolithicFolder.mkdirs();

            System.setProperty("active_export_output_path", monolithicFolder.getAbsolutePath());
            try {
                // Export using monolithic MBOX
                MboxOutputHandler mboxHandler = new MboxOutputHandler();
                mboxHandler.export(monolithicFolder, list, "Keep Attachments in Folder", "Single Monolithic MBOX for Entire Migration", "Original Subject");

                File expectedMbox = new File(monolithicFolder, "migration.mbox");
                if (expectedMbox.exists() && expectedMbox.length() > 0) {
                    System.out.println("[PASS] Monolithic MBOX file created at root successfully!");
                } else {
                    System.out.println("[FAIL] Monolithic MBOX file not found at root!");
                    allPassed = false;
                }

                // Export using monolithic PST
                PstOutputHandler pstHandler = new PstOutputHandler();
                pstHandler.export(monolithicFolder, list, "Keep Attachments in Folder", "Single Monolithic PST File (Entire Migration - Default)", "Original Subject");

                File expectedPst = new File(monolithicFolder, "PST_Export.pst");
                if (expectedPst.exists() && expectedPst.length() > 0) {
                    System.out.println("[PASS] Monolithic PST file created at root successfully!");
                } else {
                    System.out.println("[FAIL] Monolithic PST file not found at root!");
                    allPassed = false;
                }
            } finally {
                System.clearProperty("active_export_output_path");
            }

            if (allPassed) {
                System.out.println("=== METADATA MIGRATION VALIDATION SUCCESS ===");
            } else {
                System.out.println("=== METADATA MIGRATION VALIDATION FAILURE ===");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Validation failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean verifyFileContains(File file, String searchString) {
        try {
            if (!file.exists()) {
                System.out.println("File does not exist: " + file.getPath());
                return false;
            }
            String content = Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            boolean contains = content.contains(searchString);
            if (!contains) {
                System.out.println("Search string not found in " + file.getName() + ": " + searchString);
            }
            return contains;
        } catch (Exception e) {
            System.out.println("Error reading " + file.getPath() + ": " + e.getMessage());
            return false;
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

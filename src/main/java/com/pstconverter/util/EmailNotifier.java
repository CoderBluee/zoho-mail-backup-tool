package com.pstconverter.util;

import com.pstconverter.config.BrandConfig;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailNotifier {

    public static void sendEmailNotification(
        String recipientEmail,
        long successCount,
        long skippedCount,
        long failedCount,
        String elapsedDuration,
        String sizeStr,
        String outputPath
    ) throws Exception {
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            recipientEmail = "trial_user@example.com";
        }

        // SMTP settings from BrandConfig
        String host = BrandConfig.SMTP_HOST;
        String port = BrandConfig.SMTP_PORT;
        String username = BrandConfig.SMTP_USER;
        String password = BrandConfig.SMTP_PASSWORD;
        String sender = BrandConfig.COMPANY_EMAIL_SENDER;

        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);
        // Timeout configurations to prevent blocking indefinitely
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(sender, BrandConfig.COMPANY_NAME + " Notifications"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
        message.setSubject(BrandConfig.TOOL_NAME + " - Migration Completion Summary");

        String htmlContent = "<h3>Migration Job Completed Successfully</h3>" +
                "<p>Your mailbox conversion job has finished. Here is the execution summary report:</p>" +
                "<table border='1' cellpadding='8' cellspacing='0' style='border-collapse: collapse; font-family: sans-serif;'>" +
                "  <tr style='background-color: #f1f5f9;'>" +
                "    <th>Metric</th>" +
                "    <th>Value</th>" +
                "  </tr>" +
                "  <tr>" +
                "    <td><b>Successfully Converted</b></td>" +
                "    <td style='color: #10b981; font-weight: bold;'>" + successCount + "</td>" +
                "  </tr>" +
                "  <tr>" +
                "    <td><b>Skipped Items (Exclusions)</b></td>" +
                "    <td style='color: #64748b; font-weight: bold;'>" + skippedCount + "</td>" +
                "  </tr>" +
                "  <tr>" +
                "    <td><b>Failed Items</b></td>" +
                "    <td style='color: #ef4444; font-weight: bold;'>" + failedCount + "</td>" +
                "  </tr>" +
                "  <tr>" +
                "    <td><b>Elapsed Duration</b></td>" +
                "    <td>" + elapsedDuration + "</td>" +
                "  </tr>" +
                "  <tr>" +
                "    <td><b>Exported Data Size</b></td>" +
                "    <td>" + sizeStr + "</td>" +
                "  </tr>" +
                "  <tr>" +
                "    <td><b>Destination Output</b></td>" +
                "    <td>" + (outputPath != null ? outputPath : "Cloud Storage") + "</td>" +
                "  </tr>" +
                "</table>" +
                "<p><br/>Thank you for using <b>" + BrandConfig.COMPANY_NAME + "</b> services.</p>";

        message.setContent(htmlContent, "text/html; charset=utf-8");

        Transport.send(message);
        System.out.println("[INFO] Email notification sent successfully to: " + recipientEmail);
    }
}

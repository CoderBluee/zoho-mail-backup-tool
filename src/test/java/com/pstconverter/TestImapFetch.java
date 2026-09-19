package com.pstconverter;

import com.pstconverter.imap.ImapSourceAdapter;
import com.pstconverter.model.MailMessage;
import com.pstconverter.model.MailboxFolder;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.util.SettingsManager.ImapAccountRecord;

import java.io.File;
import java.util.List;

public class TestImapFetch {
    public static void main(String[] args) {
        try {
            System.out.println("--- Testing DB Accounts ---");
            List<ImapAccountRecord> accounts = SettingsManager.getSavedImapSourceAccounts();
            System.out.println("Saved accounts count: " + accounts.size());
            for (ImapAccountRecord rec : accounts) {
                System.out.println("Account: " + rec.email() + " | Host: " + rec.host() + ":" + rec.port() + " | SSL: " + rec.ssl() + " | Pass length: " + (rec.password() != null ? rec.password().length() : 0));
            }

            if (!accounts.isEmpty()) {
                ImapAccountRecord first = accounts.get(0);
                File fakeFile = new File(System.getProperty("user.home"), first.email() + ".imap");

                ImapSourceAdapter adapter = new ImapSourceAdapter();
                adapter.setConnectedAccount(first.email());

                System.out.println("\n--- Parsing Folder Structure ---");
                MailboxFolder root = adapter.parseFolderStructure(fakeFile, (msg, pct) -> {
                    System.out.println("PROGRESS: " + pct + " - " + msg);
                });

                System.out.println("\nRoot Folder Name: " + root.getName());
                for (MailboxFolder sub : root.getChildren()) {
                    System.out.println("Sub: " + sub.getName() + " (count=" + sub.getContentCount() + ")");
                    for (MailboxFolder f : sub.getChildren()) {
                        System.out.println("  Folder: " + f.getName() + " (count=" + f.getContentCount() + ")");
                    }
                }

                System.out.println("\n--- Fetching Emails for INBOX ---");
                List<MailMessage> emails = adapter.getEmails(fakeFile, List.of("INBOX"), 0, 10);
                System.out.println("Fetched INBOX emails count: " + emails.size());
                for (MailMessage m : emails) {
                    System.out.println(" - [" + m.getDate() + "] From: " + m.getFrom() + " | Subject: " + m.getSubject());
                }
            } else {
                System.out.println("No saved accounts found in DB!");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

package com.pstconverter.imap;

import com.pstconverter.core.adapter.SourceAdapter;
import com.pstconverter.model.MailMessage;
import com.pstconverter.model.MailboxFolder;
import com.pstconverter.util.ChilkatLibraryLoader;
import com.pstconverter.util.DiagnosticLogger;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.util.SettingsManager.ImapAccountRecord;

import com.chilkatsoft.CkByteData;
import com.chilkatsoft.CkEmail;
import com.chilkatsoft.CkEmailBundle;
import com.chilkatsoft.CkImap;
import com.chilkatsoft.CkMailboxes;
import com.chilkatsoft.CkMessageSet;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * High-performance IMAP Source Adapter.
 * Connects directly to any standard IMAP server, parses folder hierarchy,
 * provides fast header streaming for Explorer table preview, and streams complete
 * full emails (with inline images resolved as Base64 Data URIs, rich HTML body, and attachments)
 * during active migration.
 */
public class ImapSourceAdapter implements SourceAdapter {

    private String connectedAccount = "user@imap.com";

    public void setConnectedAccount(String account) {
        if (account != null && !account.trim().isEmpty()) {
            this.connectedAccount = account.trim();
        }
    }

    public String getConnectedAccount() {
        return connectedAccount;
    }

    private ImapAccountRecord resolveAccount(File file) {
        String emailToMatch = connectedAccount;
        if (file != null) {
            String name = file.getName();
            if (name.endsWith(".imap")) {
                emailToMatch = name.substring(0, name.length() - 5).trim();
            } else if (name.contains("@")) {
                emailToMatch = name.trim();
            }
        }
        
        String cleanEmail = emailToMatch.trim().toLowerCase();
        List<ImapAccountRecord> saved = SettingsManager.getSavedImapSourceAccounts();
        for (ImapAccountRecord rec : saved) {
            if (rec.email().equalsIgnoreCase(cleanEmail)) {
                this.connectedAccount = rec.email();
                return rec;
            }
        }
        
        if (!saved.isEmpty()) {
            this.connectedAccount = saved.get(0).email();
            return saved.get(0);
        }
        
        return null;
    }

    private CkImap connectImap(ImapAccountRecord record) throws Exception {
        ChilkatLibraryLoader.loadAndUnlock();
        CkImap imap = new CkImap();

        int timeoutSec = 30;
        try {
            timeoutSec = Integer.parseInt(SettingsManager.getSetting("connection_timeout", "30"));
        } catch (Exception ignored) {}

        ImapAuthHelper.configureImapClient(imap, record.port(), record.ssl(), timeoutSec);

        boolean connected = imap.Connect(record.host().trim());
        if (!connected) {
            String err = imap.lastErrorText();
            throw new Exception("Failed to connect to IMAP server (" + record.host() + ":" + record.port() + "): " + err);
        }

        java.util.List<String> candidates = ImapAuthHelper.buildPasswordCandidates(record.password());
        boolean loggedIn = false;
        String lastErr = "";
        for (String candidate : candidates) {
            loggedIn = imap.Login(record.email().trim(), candidate);
            if (loggedIn) break;
            lastErr = imap.lastErrorText();
        }

        if (!loggedIn && record.email().contains("@")) {
            String username = record.email().substring(0, record.email().indexOf("@"));
            for (String candidate : candidates) {
                loggedIn = imap.Login(username, candidate);
                if (loggedIn) break;
            }
        }

        if (!loggedIn) {
            imap.Disconnect();
            throw new Exception("IMAP Login failed for " + record.email() + ": " + lastErr);
        }

        return imap;
    }

    private String cleanBoxName(String box) {
        if (box == null) return "INBOX";
        String s = box.trim();
        int p = s.lastIndexOf(" (");
        if (p > 0 && s.endsWith(")")) {
            s = s.substring(0, p).trim();
        }
        return s;
    }

    private String mapTargetBoxToImapMailbox(CkImap imap, String targetBox) throws Exception {
        String clean = cleanBoxName(targetBox);
        if (clean.isEmpty() || clean.equalsIgnoreCase("Emails")) {
            return "INBOX";
        }

        if (imap.SelectMailbox(clean)) {
            return clean;
        }

        CkMailboxes mboxes = imap.ListMailboxes("", "*");
        if (mboxes != null) {
            int count = mboxes.get_Count();
            for (int i = 0; i < count; i++) {
                String mb = mboxes.getName(i);
                if (mb == null || mb.isEmpty()) continue;
                if (mb.equalsIgnoreCase(clean) || mb.toLowerCase().endsWith("/" + clean.toLowerCase())) {
                    if (imap.SelectMailbox(mb)) {
                        return mb;
                    }
                }
            }

            for (int i = 0; i < count; i++) {
                String mb = mboxes.getName(i);
                if (mb == null || mb.isEmpty()) continue;
                String lowerMb = mb.toLowerCase();
                String lowerClean = clean.toLowerCase();

                boolean match = (lowerClean.contains("sent") && lowerMb.contains("sent")) ||
                                (lowerClean.contains("draft") && lowerMb.contains("draft")) ||
                                (lowerClean.contains("trash") && lowerMb.contains("trash")) ||
                                (lowerClean.contains("junk") && (lowerMb.contains("junk") || lowerMb.contains("spam"))) ||
                                (lowerClean.contains("inbox") && lowerMb.contains("inbox"));

                if (match) {
                    if (imap.SelectMailbox(mb)) {
                        return mb;
                    }
                }
            }
        }

        return clean;
    }

    @Override
    public boolean canParse(File file) {
        return true;
    }

    @Override
    public boolean validateFile(File file) {
        return true;
    }

    @Override
    public String getSourceType() {
        return "IMAP";
    }

    @Override
    public String getDisplayName() {
        return "IMAP Mailbox Account";
    }

    @Override
    public MailboxFolder parseFolderStructure(File file, BiConsumer<String, String> progressCallback) throws Exception {
        ImapAccountRecord record = resolveAccount(file);
        String accountLabel = (record != null) ? record.email() : connectedAccount;

        if (progressCallback != null) {
            progressCallback.accept("Connecting to IMAP Server for " + accountLabel + "...", "5%");
        }

        MailboxFolder root = new MailboxFolder("IMAP Server (" + accountLabel + ")", 0, true);

        if (record == null) {
            MailboxFolder emailsNode = new MailboxFolder("Emails", 0, false);
            emailsNode.addChild(new MailboxFolder("INBOX", 0, false));
            emailsNode.addChild(new MailboxFolder("Sent Mail", 0, false));
            emailsNode.addChild(new MailboxFolder("Trash", 0, false));
            root.addChild(emailsNode);
            return root;
        }

        CkImap imap = null;
        try {
            imap = connectImap(record);
            if (progressCallback != null) {
                progressCallback.accept("Fetching Mailbox Folders from " + record.host() + "...", "25%");
            }

            CkMailboxes mboxes = imap.ListMailboxes("", "*");
            MailboxFolder emailsNode = new MailboxFolder("Emails", 0, false);

            if (mboxes != null) {
                int count = mboxes.get_Count();
                for (int i = 0; i < count; i++) {
                    String boxName = mboxes.getName(i);
                    if (boxName == null || boxName.isEmpty()) continue;

                    int msgCount = 0;
                    boolean selected = imap.SelectMailbox(boxName);
                    if (selected) {
                        msgCount = imap.get_NumMessages();
                    }

                    String displayName = boxName;
                    if (displayName.startsWith("[Gmail]/")) {
                        displayName = displayName.substring(8);
                    } else if (displayName.contains("/")) {
                        displayName = displayName.substring(displayName.lastIndexOf("/") + 1);
                    }

                    if (displayName.trim().isEmpty()) {
                        displayName = boxName;
                    }

                    MailboxFolder folder = new MailboxFolder(displayName, msgCount, false);
                    emailsNode.addChild(folder);

                    if (progressCallback != null) {
                        int pct = 25 + (int) (((double) i / count) * 60);
                        progressCallback.accept("Parsed Folder: " + displayName + " (" + msgCount + " emails)", pct + "%");
                    }
                }
            }

            if (emailsNode.getChildren().isEmpty()) {
                emailsNode.addChild(new MailboxFolder("INBOX", 0, false));
                emailsNode.addChild(new MailboxFolder("Sent Mail", 0, false));
                emailsNode.addChild(new MailboxFolder("Drafts", 0, false));
                emailsNode.addChild(new MailboxFolder("Trash", 0, false));
            }

            root.addChild(emailsNode);

            if (progressCallback != null) {
                progressCallback.accept("Mailbox structure successfully parsed for " + record.email(), "100%");
            }

        } catch (Exception ex) {
            DiagnosticLogger.error("Failed to parse IMAP structure for " + record.email() + ": " + ex.getMessage(), ex);
            System.err.println("Failed to parse IMAP folder structure for " + record.email() + ": " + ex.getMessage());
            throw ex;
        } finally {
            if (imap != null) {
                try { imap.Disconnect(); } catch (Exception ignored) {}
            }
        }

        return root;
    }

    @Override
    public List<MailMessage> getEmails(File file, List<String> folderPath) throws Exception {
        return getEmails(file, folderPath, 0, 50);
    }

    /**
     * Fast header-only fetch for Step 2 Explorer TableView pagination (0.1s).
     */
    @Override
    public List<MailMessage> getEmails(File file, List<String> folderPath, int offset, int limit) throws Exception {
        List<MailMessage> list = new ArrayList<>();
        streamEmailHeaders(file, folderPath, list::add, offset, limit);
        return list;
    }

    /**
     * Fast header streaming for UI Explorer table and metadata queries.
     */
    public void streamEmailHeaders(File file, List<String> folderPath, Consumer<MailMessage> consumer, int offset, int limit) throws Exception {
        ImapAccountRecord record = resolveAccount(file);
        if (record == null) return;

        String rawTarget = "INBOX";
        if (folderPath != null && !folderPath.isEmpty()) {
            rawTarget = folderPath.get(folderPath.size() - 1);
        }

        CkImap imap = null;
        try {
            imap = connectImap(record);
            String actualMailbox = mapTargetBoxToImapMailbox(imap, rawTarget);

            int totalMsgs = imap.get_NumMessages();
            if (totalMsgs == 0) return;

            CkMessageSet msgSet = imap.Search("ALL", true);
            if (msgSet == null) return;

            int countInSet = msgSet.get_Count();
            if (countInSet == 0) return;

            int startIdx = countInSet - 1 - (offset > 0 ? offset : 0);
            if (startIdx < 0) return;

            int endIdx = 0;
            if (limit > 0) {
                endIdx = Math.max(0, startIdx - limit + 1);
            }

            CkMessageSet pageMsgSet = new CkMessageSet();
            for (int i = startIdx; i >= endIdx; i--) {
                int uid = msgSet.GetId(i);
                pageMsgSet.InsertId(uid);
            }

            if (pageMsgSet.get_Count() == 0) return;

            CkEmailBundle bundle = imap.FetchHeaders(pageMsgSet);

            if (bundle != null) {
                int bundleCount = bundle.get_MessageCount();
                for (int b = 0; b < bundleCount; b++) {
                    CkEmail email = bundle.GetEmail(b);
                    if (email != null) {
                        String from = email.fromAddress();
                        if (from == null || from.isEmpty()) from = email.getHeaderField("From");
                        String subject = email.subject();
                        if (subject == null || subject.isEmpty()) subject = email.getHeaderField("Subject");
                        String date = email.getHeaderField("Date");

                        int msgId = (b < pageMsgSet.get_Count()) ? pageMsgSet.GetId(b) : (b + 1);
                        String uniqueId = record.email() + "_" + actualMailbox + "_" + msgId;

                        MailMessage msg = new MailMessage(
                            from != null ? from : "Unknown",
                            subject != null ? subject : "(No Subject)",
                            date != null ? date : "",
                            ""
                        );
                        msg.setMessageId(uniqueId);
                        String toStr = email.getHeaderField("To");
                        if (toStr != null) msg.setTo(toStr);
                        String ccStr = email.getHeaderField("Cc");
                        if (ccStr != null) msg.setCc(ccStr);

                        int numAttach = email.get_NumAttachments();
                        if (numAttach > 0) {
                            for (int a = 0; a < numAttach; a++) {
                                String attName = email.getAttachmentFilename(a);
                                if (attName != null && !attName.isEmpty()) {
                                    msg.addAttachment(attName);
                                }
                            }
                        }

                        consumer.accept(msg);
                    }
                }
            }

        } catch (Exception ex) {
            DiagnosticLogger.error("Error streaming IMAP email headers: " + ex.getMessage(), ex);
        } finally {
            if (imap != null) {
                try { imap.Disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void streamEmailsMetadata(File file, List<String> folderPath, Consumer<MailMessage> consumer, int offset, int limit, Predicate<String> skipCheck) throws Exception {
        streamEmailHeaders(file, folderPath, consumer, offset, limit);
    }

    /**
     * Extracts rich HTML body and resolves inline images (cid: references) to Base64 data URIs
     * so that the email displays standalone with 100% fidelity in any viewer or export format.
     */
    private String extractBestHtmlBody(CkEmail email) {
        if (email == null) return "";

        // 1. Let Chilkat automatically convert inline cid: images to Base64 data URIs
        try {
            email.ConvertInlineImages();
        } catch (Exception ignored) {}

        String htmlBody = email.getHtmlBody();
        String plainBody = email.getPlainTextBody();
        String rawBody = email.body();

        String body = "";
        if (htmlBody != null && !htmlBody.trim().isEmpty()) {
            body = htmlBody;
        } else if (rawBody != null && !rawBody.trim().isEmpty()) {
            body = rawBody;
        } else if (plainBody != null && !plainBody.trim().isEmpty()) {
            body = plainBody;
        }

        if (body.isEmpty()) return "";

        // 2. Fallback: resolve any remaining inline cid: images from Related Items
        try {
            int numRelated = email.get_NumRelatedItems();
            for (int r = 0; r < numRelated; r++) {
                String cid = email.getRelatedContentID(r);
                String contentType = email.getRelatedContentType(r);
                String relFilename = email.getRelatedFilename(r);

                if (contentType == null || contentType.trim().isEmpty()) {
                    if (relFilename != null && relFilename.toLowerCase().endsWith(".png")) contentType = "image/png";
                    else if (relFilename != null && (relFilename.toLowerCase().endsWith(".jpg") || relFilename.toLowerCase().endsWith(".jpeg"))) contentType = "image/jpeg";
                    else if (relFilename != null && relFilename.toLowerCase().endsWith(".gif")) contentType = "image/gif";
                    else if (relFilename != null && relFilename.toLowerCase().endsWith(".svg")) contentType = "image/svg+xml";
                    else contentType = "image/png";
                }

                CkByteData byteData = new CkByteData();
                if (email.GetRelatedData(r, byteData)) {
                    byte[] bytes = byteData.toByteArray();
                    byteData.delete();
                    if (bytes != null && bytes.length > 0) {
                        String base64 = Base64.getEncoder().encodeToString(bytes);
                        String dataUri = "data:" + contentType + ";base64," + base64;

                        if (cid != null && !cid.isEmpty()) {
                            String cleanCid = cid.replaceAll("^<|>$", "").trim();
                            body = body.replace("cid:" + cleanCid, dataUri);
                            body = body.replace("cid:<" + cleanCid + ">", dataUri);
                            body = body.replace("cid:" + cid, dataUri);
                        }
                        if (relFilename != null && !relFilename.isEmpty()) {
                            body = body.replace("cid:" + relFilename, dataUri);
                            body = body.replace("cid:<" + relFilename + ">", dataUri);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Fallback: also resolve any inline images stored as regular attachments with Content-ID
        try {
            int numAttach = email.get_NumAttachments();
            for (int a = 0; a < numAttach; a++) {
                String cid = email.getAttachmentContentID(a);
                String attName = email.getAttachmentFilename(a);
                String contentType = email.getAttachmentContentType(a);

                if (contentType == null || contentType.trim().isEmpty()) {
                    if (attName != null && attName.toLowerCase().endsWith(".png")) contentType = "image/png";
                    else if (attName != null && (attName.toLowerCase().endsWith(".jpg") || attName.toLowerCase().endsWith(".jpeg"))) contentType = "image/jpeg";
                    else if (attName != null && attName.toLowerCase().endsWith(".gif")) contentType = "image/gif";
                    else if (attName != null && attName.toLowerCase().endsWith(".svg")) contentType = "image/svg+xml";
                    else contentType = "image/png";
                }

                CkByteData byteData = new CkByteData();
                if (email.GetAttachmentData(a, byteData)) {
                    byte[] bytes = byteData.toByteArray();
                    byteData.delete();
                    if (bytes != null && bytes.length > 0) {
                        String base64 = Base64.getEncoder().encodeToString(bytes);
                        String dataUri = "data:" + contentType + ";base64," + base64;

                        if (cid != null && !cid.isEmpty()) {
                            String cleanCid = cid.replaceAll("^<|>$", "").trim();
                            body = body.replace("cid:" + cleanCid, dataUri);
                            body = body.replace("cid:<" + cleanCid + ">", dataUri);
                            body = body.replace("cid:" + cid, dataUri);
                        }
                        if (attName != null && !attName.isEmpty()) {
                            body = body.replace("cid:" + attName, dataUri);
                            body = body.replace("cid:<" + attName + ">", dataUri);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return body;
    }

    /**
     * Active migration streaming for Step 5 Conversion.
     * Fetches COMPLETE messages with full HTML body, inline images resolved, headers, and attachment binary data.
     */
    @Override
    public void streamEmails(File file, List<String> folderPath, Consumer<MailMessage> consumer, int offset, int limit, Predicate<String> skipCheck) throws Exception {
        ImapAccountRecord record = resolveAccount(file);
        if (record == null) return;

        String rawTarget = "INBOX";
        if (folderPath != null && !folderPath.isEmpty()) {
            rawTarget = folderPath.get(folderPath.size() - 1);
        }

        CkImap imap = null;
        try {
            imap = connectImap(record);
            String actualMailbox = mapTargetBoxToImapMailbox(imap, rawTarget);

            int totalMsgs = imap.get_NumMessages();
            if (totalMsgs == 0) return;

            CkMessageSet msgSet = imap.Search("ALL", true);
            if (msgSet == null) return;

            int countInSet = msgSet.get_Count();
            if (countInSet == 0) return;

            int startIdx = countInSet - 1 - (offset > 0 ? offset : 0);
            if (startIdx < 0) return;

            int endIdx = 0;
            if (limit > 0) {
                endIdx = Math.max(0, startIdx - limit + 1);
            }

            int processedCount = 0;
            for (int i = startIdx; i >= endIdx; i--) {
                int uid = msgSet.GetId(i);
                String uniqueId = record.email() + "_" + actualMailbox + "_" + uid;

                if (skipCheck != null && skipCheck.test(uniqueId)) {
                    continue;
                }

                // Fetch FULL email including body & HTML content
                CkEmail email = imap.FetchSingle(uid, true);
                if (email == null) {
                    email = imap.FetchSingleHeader(uid, true);
                }

                if (email != null) {
                    String from = email.fromAddress();
                    if (from == null || from.isEmpty()) from = email.getHeaderField("From");
                    String subject = email.subject();
                    if (subject == null || subject.isEmpty()) subject = email.getHeaderField("Subject");
                    String date = email.getHeaderField("Date");

                    String fullBody = extractBestHtmlBody(email);

                    MailMessage msg = new MailMessage(
                        from != null ? from : "Unknown",
                        subject != null ? subject : "(No Subject)",
                        date != null ? date : "",
                        fullBody != null ? fullBody : ""
                    );
                    msg.setMessageId(uniqueId);
                    String toStr = email.getHeaderField("To");
                    if (toStr != null) msg.setTo(toStr);
                    String ccStr = email.getHeaderField("Cc");
                    if (ccStr != null) msg.setCc(ccStr);
                    String bccStr = email.getHeaderField("Bcc");
                    if (bccStr != null) msg.setBcc(bccStr);

                    // Extract all attachments with binary data
                    int numAttach = email.get_NumAttachments();
                    if (numAttach > 0) {
                        for (int a = 0; a < numAttach; a++) {
                            String attName = email.getAttachmentFilename(a);
                            String contentType = email.getAttachmentContentType(a);
                            if (attName == null || attName.trim().isEmpty()) {
                                String ext = ".dat";
                                if (contentType != null) {
                                    if (contentType.contains("pdf")) ext = ".pdf";
                                    else if (contentType.contains("image/png")) ext = ".png";
                                    else if (contentType.contains("image/jpeg")) ext = ".jpg";
                                    else if (contentType.contains("word") || contentType.contains("msword")) ext = ".docx";
                                    else if (contentType.contains("excel") || contentType.contains("spreadsheet")) ext = ".xlsx";
                                    else if (contentType.contains("zip")) ext = ".zip";
                                    else if (contentType.contains("text/plain")) ext = ".txt";
                                }
                                attName = "attachment_" + (a + 1) + ext;
                            }

                            byte[] attBytes = new byte[0];
                            try {
                                CkByteData byteData = new CkByteData();
                                boolean ok = email.GetAttachmentData(a, byteData);
                                if (ok) {
                                    attBytes = byteData.toByteArray();
                                }
                                byteData.delete();
                            } catch (Exception ignored) {}
                            msg.addAttachment(attName, attBytes);
                        }
                    }

                    consumer.accept(msg);
                    processedCount++;
                }
            }

        } catch (Exception ex) {
            DiagnosticLogger.error("Error streaming full IMAP emails during conversion: " + ex.getMessage(), ex);
        } finally {
            if (imap != null) {
                try { imap.Disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public String getFullEmailBody(File file, List<String> folderPath, MailMessage msg) throws Exception {
        if (msg == null) return "";
        if (msg.getBody() != null && !msg.getBody().trim().isEmpty()) {
            return msg.getBody();
        }

        ImapAccountRecord record = resolveAccount(file);
        if (record == null) return "";

        String messageIdStr = msg.getMessageId();
        int msgId = 0;
        if (messageIdStr != null && messageIdStr.contains("_")) {
            String[] parts = messageIdStr.split("_");
            try {
                msgId = Integer.parseInt(parts[parts.length - 1]);
            } catch (Exception ignored) {}
        }

        String rawTarget = "INBOX";
        if (folderPath != null && !folderPath.isEmpty()) {
            rawTarget = folderPath.get(folderPath.size() - 1);
        }

        CkImap imap = null;
        try {
            imap = connectImap(record);
            mapTargetBoxToImapMailbox(imap, rawTarget);

            CkEmail email = null;
            if (msgId > 0) {
                email = imap.FetchSingle(msgId, true);
            }

            if (email == null) {
                String subj = msg.getSubject();
                if (subj != null && !subj.isEmpty()) {
                    CkMessageSet foundSet = imap.Search("SUBJECT \"" + subj + "\"", true);
                    if (foundSet != null && foundSet.get_Count() > 0) {
                        int foundId = foundSet.GetId(0);
                        email = imap.FetchSingle(foundId, true);
                    }
                }
            }

            if (email != null) {
                String fullBody = extractBestHtmlBody(email);
                if (!fullBody.trim().isEmpty()) {
                    msg.setBody(fullBody);
                    return fullBody;
                }
            }
        } catch (Exception ex) {
            DiagnosticLogger.error("Failed to fetch full email body for " + (msg != null ? msg.getSubject() : "") + ": " + ex.getMessage(), ex);
        } finally {
            if (imap != null) {
                try { imap.Disconnect(); } catch (Exception ignored) {}
            }
        }

        return "";
    }
}

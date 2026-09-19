package com.pstconverter.core.destination;

import com.pstconverter.model.MailMessage;
import com.chilkatsoft.CkEmail;
import com.chilkatsoft.CkByteData;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Unified interface contract for cloud email destination adapters.
 * Encapsulates connection lifecycle and upload operations.
 *
 * All sanitization, JSON escaping, and MIME construction is centralised here so
 * that a single fix automatically applies to every concrete adapter (IMAP, Gmail,
 * Office 365, Yahoo Mail) without duplication.
 */
public interface DestinationAdapter {

    /**
     * Validates configuration settings, authenticates, and connects to the remote service.
     */
    void connect(EmailDestinationConfig config) throws Exception;

    /**
     * Performs a dry-run connection and credentials validation, throwing an exception on failure.
     */
    void testConnection(EmailDestinationConfig config) throws Exception;

    /**
     * Uploads a single email message to the specified target mailbox folder.
     */
    void uploadMessage(MailMessage message, String targetFolder, EmailDestinationConfig config) throws Exception;

    /**
     * Uploads a single email message, overriding the target user email for Admin Impersonation.
     */
    default void uploadMessage(MailMessage message, String targetFolder, EmailDestinationConfig config, String overrideTargetUserEmail) throws Exception {
        uploadMessage(message, targetFolder, config);
    }

    /**
     * Gracefully closes connections and releases resources.
     */
    void disconnect() throws Exception;

    /**
     * Checks if a message already exists in the target folder on the remote service.
     */
    default boolean isMessageAlreadyOnServer(MailMessage message, String targetFolder, EmailDestinationConfig config) throws Exception {
        return false;
    }

    // ── Shared MIME construction ─────────────────────────────────────────────

    /**
     * Populates a CkEmail from a MailMessage, applying all sanitizers and
     * dynamic content-type detection. Called by Gmail (Modern Auth) and IMAP handlers.
     */
    static CkEmail createCkEmail(MailMessage message, String defaultSender) throws Exception {
        com.pstconverter.util.ChilkatLibraryLoader.loadAndUnlock();
        CkEmail ckEmail = new CkEmail();

        // ── From ──────────────────────────────────────────────────────────────
        String fromVal = message.getFrom() != null && !message.getFrom().isEmpty() ? message.getFrom() : defaultSender;
        ckEmail.put_From(fromVal);

        // ── Subject ───────────────────────────────────────────────────────────
        String rawSubject = message.getSubject() != null ? message.getSubject() : "(No Subject)";
        ckEmail.put_Subject(cleanSubject(rawSubject));

        // ── Sent date — multi-format safe parsing ─────────────────────────────
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        ckEmail.AddHeaderField("Date", sdf.format(parseSentDate(message.getDate())));

        // ── Recipients (with listserv filtering) ───────────────────────────────
        String toRecipients  = cleanRecipients(message.getTo());
        String ccRecipients  = cleanRecipients(message.getCc());
        String bccRecipients = cleanRecipients(message.getBcc());

        if (!toRecipients.isEmpty()) {
            ckEmail.AddTo("", toRecipients);
        }
        if (!ccRecipients.isEmpty()) {
            ckEmail.AddCC("", ccRecipients);
        }
        if (!bccRecipients.isEmpty()) {
            ckEmail.AddBcc("", bccRecipients);
        }
        if (message.getMessageId() != null && !message.getMessageId().isEmpty()) {
            ckEmail.AddHeaderField("Message-ID", message.getMessageId());
        }

        // ── Body — sanitize then detect HTML vs plain text ────────────────────
        String cleanedBody = cleanBody(message.getBody() != null ? message.getBody() : "");
        String lowerBody   = cleanedBody.toLowerCase(Locale.ROOT);
        boolean isHtml = lowerBody.contains("<html")
                      || lowerBody.contains("<body")
                      || lowerBody.contains("<p>")
                      || lowerBody.contains("<br")
                      || lowerBody.contains("<div");
        String charset = com.pstconverter.core.output.OutputHandler.getFallbackEncoding();
        ckEmail.put_Charset(charset);

        if (isHtml) {
            ckEmail.SetHtmlBody(cleanedBody);
            ckEmail.AddPlainTextAlternativeBody(com.pstconverter.core.output.OutputHandler.getPlainTextBody(cleanedBody));
        } else {
            ckEmail.put_Body(cleanedBody);
        }

        // ── Attachments ───────────────────────────────────────────────────────
        List<MailMessage.Attachment> attachments = message.getAttachmentList();
        if (attachments != null && !attachments.isEmpty()) {
            for (MailMessage.Attachment att : attachments) {
                String filename = att.getFilename();
                byte[] data = att.getData();
                if (data != null && data.length > 0) {
                    com.chilkatsoft.CkByteData byteData = new com.chilkatsoft.CkByteData();
                    try {
                        byteData.appendByteArray(data);
                        ckEmail.AddDataAttachment2(filename, byteData, "application/octet-stream");
                    } finally {
                        byteData.delete();
                    }
                }
            }
        }
        return ckEmail;
    }

    // ── Sanitizers (shared by ALL destination adapters) ─────────────────────

    /**
     * Removes any leading bracketed listserv / mailing-list tags from the subject.
     * Examples: "[ITANA]", "[EDUCAUSE]", "[RE: ANNOUNCE]", "[FWD:LIST]"
     * The removal is greedy so stacked tags like "[A] [B] Actual subject" are fully stripped.
     * A generic RE:/FWD: prefix that happens to include brackets is also handled.
     */
    static String cleanSubject(String subject) {
        if (subject == null) return "(No Subject)";
        // Strip one or more consecutive bracketed tags (any alphanumeric/punctuation content)
        String cleaned = subject.replaceAll("(?i)(\\[[A-Z0-9:_\\-\\s.]+\\]\\s*)+", "").trim();
        // If the entire subject was a tag, preserve a readable fallback
        return cleaned.isEmpty() ? "(No Subject)" : cleaned;
    }

    /**
     * Filters a comma/semicolon-separated recipient list, removing listserv relay addresses.
     * Catches patterns such as:
     *   - listserv.educause.edu, listserv.acm.org, any "listserv." subdomain
     *   - role addresses: -bounces@, -request@, -owner@, majordomo@, sympa@, mailman-
     *   - explicitly named listserv accounts: listserv@*, listproc@*
     */
    static String cleanRecipients(String recipients) {
        if (recipients == null || recipients.trim().isEmpty()) return "";
        String[] parts = recipients.split("[,;]");
        java.util.List<String> cleaned = new java.util.ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            // Extract bare email from "Display Name <email>" format
            String email = trimmed;
            if (trimmed.contains("<") && trimmed.contains(">")) {
                email = trimmed.substring(trimmed.indexOf('<') + 1, trimmed.indexOf('>')).trim();
            }
            String lowerEmail = email.toLowerCase(Locale.ROOT);
            if (isListservAddress(lowerEmail)) continue;
            cleaned.add(trimmed);
        }
        return String.join(", ", cleaned);
    }

    /**
     * Returns true if the given lowercase email address looks like a listserv relay.
     */
    private static boolean isListservAddress(String lowerEmail) {
        // Listserv subdomains (e.g. listserv.educause.edu, lists.acm.org)
        if (lowerEmail.contains("listserv.") || lowerEmail.contains(".listserv")) return true;
        // Common listserv / mailing list role accounts
        if (lowerEmail.startsWith("listserv@"))  return true;
        if (lowerEmail.startsWith("listproc@"))  return true;
        if (lowerEmail.startsWith("majordomo@")) return true;
        if (lowerEmail.startsWith("sympa@"))     return true;
        if (lowerEmail.startsWith("mailman@"))   return true;
        // Mailman / Majordomo automated addresses: -bounces@, -request@, -owner@
        if (lowerEmail.contains("-bounces@")) return true;
        if (lowerEmail.contains("-request@")) return true;
        if (lowerEmail.contains("-owner@"))   return true;
        if (lowerEmail.contains("-admin@"))   return true;
        return false;
    }

    /**
     * Sanitizes an email body by removing mailing-list footer/unsubscribe blocks.
     * Handles both HTML and plain-text bodies for any listserv (not just ITANA).
     *
     * Rules (applied in order):
     *  1. HTML block: removes <p|div|span> containing an unsubscribe or listserv notice.
     *  2. Plain text: removes "To unsubscribe from ..." lines up to the next blank line.
     *  3. Listserv dividers: removes Mailman/Listserv signature lines (_____ header rows).
     *  4. Cleanup: trims trailing whitespace / multiple blank lines left after removal.
     */
    static String cleanBody(String body) {
        if (body == null) return "";
        String cleaned = body;

        // 1. HTML block — removes <p|div|span> whose text starts with an unsubscribe notice
        //    Non-greedy .*? stops at the matching closing tag, never eating surrounding HTML.
        cleaned = cleaned.replaceAll(
            "(?is)<(p|div|span)[^>]*>\\s*To unsubscribe from .*? list.*?</\\1>", "");

        // 2. Plain text footer — "To unsubscribe from the XYZ list ..." up to next blank line
        cleaned = cleaned.replaceAll(
            "(?is)To unsubscribe from .*? list.*?(?:\\r?\\n\\r?\\n|$)", "");

        // 3. Generic listserv / Mailman divider lines followed by list admin info
        //    Matches 10+ underscores or dashes then anything (up to 1000 characters) until it hits a listserv keyword
        cleaned = cleaned.replaceAll(
            "(?is)[_\\-]{10,}.{0,1000}?(?:mailing list|unsubscribe|listserv|list-archive|list-post)", "");

        // 4. Remove trailing blank lines left by the above removals
        cleaned = cleaned.replaceAll("(?m)(\\r?\\n){3,}", "\n\n").trim();

        return cleaned;
    }

    // ── Shared JSON helpers ─────────────────────────────────────────────────
    // These are declared here so every handler uses one canonical implementation.
    // Concrete handlers MUST NOT re-declare private copies.

    /**
     * Escapes a string for safe embedding inside a JSON string value.
     */
    static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Unescapes a JSON string value (converts \\/ → /, \\n → newline, etc.).
     * Used when parsing API responses that contain escaped paths.
     */
    static String unescapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\/", "/")
                  .replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .replace("\\b", "\b")
                  .replace("\\f", "\f")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t");
    }

    // ── Date parsing helper ──────────────────────────────────────────────────

    /** RFC 822 / RFC 2822 / Java Date.toString() formats used in PST and MIME messages. */
    String[] DATE_FORMATS = {
        "EEE MMM dd HH:mm:ss z yyyy",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "dd MMM yyyy HH:mm:ss z",
        "EEE MMM dd HH:mm:ss Z yyyy",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "MM/dd/yyyy HH:mm:ss",
        "MM/dd/yyyy"
    };

    /**
     * Parses a date string into a {@link java.util.Date} using multiple well-known formats.
     * Falls back to "now" if parsing fails, to avoid silently setting epoch (1970) timestamps.
     */
    static Date parseSentDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return new Date();
        String trimmed = dateStr.trim();

        // Try epoch millis first (java-libpst sometimes stores dates as ms strings)
        try {
            return new Date(Long.parseLong(trimmed));
        } catch (NumberFormatException ignored) {}

        // Try common date string formats
        String[] formats = {
            "EEE MMM dd HH:mm:ss z yyyy",     // Java Date.toString() format — java-libpst returns this for IST/PST/EST dates
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "dd MMM yyyy HH:mm:ss z",
            "EEE MMM dd HH:mm:ss Z yyyy",     // Variant with numeric offset
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy"
        };
        for (String fmt : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.ENGLISH);
                sdf.setLenient(false);
                return sdf.parse(trimmed);
            } catch (ParseException ignored) {}
        }

        System.err.println("[WARN] DestinationAdapter: Could not parse date string: \"" + trimmed + "\". Using current time.");
        return new Date();
    }

    static void applyThrottle(long byteCount) {
        String throttleStr = com.pstconverter.util.SettingsManager.getSetting("network_throttle", "Unlimited");
        if (throttleStr == null || throttleStr.equalsIgnoreCase("Unlimited") || byteCount <= 0) {
            return;
        }
        
        long bytesPerSecond = 0;
        if (throttleStr.contains("512 KB")) {
            bytesPerSecond = 512 * 1024;
        } else if (throttleStr.contains("1 MB")) {
            bytesPerSecond = 1024 * 1024;
        } else if (throttleStr.contains("5 MB")) {
            bytesPerSecond = 5 * 1024 * 1024;
        } else if (throttleStr.contains("10 MB")) {
            bytesPerSecond = 10 * 1024 * 1024;
        }
        
        if (bytesPerSecond > 0) {
            double requiredSeconds = (double) byteCount / bytesPerSecond;
            long requiredMs = (long) (requiredSeconds * 1000.0);
            if (requiredMs > 0) {
                try {
                    long sleepTime = Math.min(requiredMs, 10000);
                    System.out.println("[INFO] Throttling upload: Sleeping " + sleepTime + " ms for " + byteCount + " bytes (" + throttleStr + ")");
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    static long estimateMessageSize(MailMessage message) {
        if (message == null) return 0;
        long size = 0;
        if (message.getBody() != null) {
            size += message.getBody().length();
        }
        if (message.getSubject() != null) {
            size += message.getSubject().length();
        }
        java.util.List<MailMessage.Attachment> attachments = message.getAttachmentList();
        if (attachments != null) {
            for (MailMessage.Attachment att : attachments) {
                if (att.getData() != null) {
                    size += att.getData().length;
                }
            }
        }
        return size;
    }

    /**
     * Resolves PST / Outlook well-known system folder names to standard normalized system tokens (INBOX, SENT, DRAFT, TRASH, SPAM).
     * Returns null if the folder name is a custom folder.
     */
    static String mapSystemFolder(String folderName) {
        if (folderName == null) return null;
        String clean = folderName.replace('\\', '/').trim();
        if (clean.lastIndexOf('/') != -1) {
            clean = clean.substring(clean.lastIndexOf('/') + 1).trim();
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.equals("inbox")) return "INBOX";
        if (lower.equals("sent items") || lower.equals("sent") || lower.equals("sent mail") || lower.equals("outbox")) return "SENT";
        if (lower.equals("drafts") || lower.equals("draft")) return "DRAFT";
        if (lower.equals("deleted items") || lower.equals("trash") || lower.equals("deleted")) return "TRASH";
        if (lower.equals("junk email") || lower.equals("junk") || lower.equals("spam")) return "SPAM";
        return null;
    }
}

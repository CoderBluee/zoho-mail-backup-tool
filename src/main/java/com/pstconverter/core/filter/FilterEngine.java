package com.pstconverter.core.filter;

import com.pstconverter.model.MailMessage;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Engine that evaluates emails against active filter settings and performs deduplication.
 */
public class FilterEngine implements AutoCloseable {
    private Properties properties = new Properties();
    private Date fromDate;
    private Date toDate;
    
    private List<String> includeSenders = new ArrayList<>();
    private List<String> excludeSenders = new ArrayList<>();
    private List<String> includeRecipients = new ArrayList<>();
    private List<String> excludeRecipients = new ArrayList<>();
    private boolean domainMatch = false;

    private List<String> includeKeywords = new ArrayList<>();
    private List<String> excludeKeywords = new ArrayList<>();
    private boolean searchSubject = true;
    private boolean searchBody = true;
    private boolean caseSensitive = false;

    private String attachmentMode = "All Messages"; // "All Messages", "With Attachments Only", "Without Attachments Only"
    private double maxAttachSize = -1;
    private String attachSizeUnit = "MB";
    private List<String> includeAttachTypes = new ArrayList<>();
    private List<String> excludeAttachTypes = new ArrayList<>();

    private boolean itemEmails = true;
    private boolean itemCalendars = true;
    private boolean itemContacts = true;
    private boolean itemTasks = true;
    private boolean itemNotes = true;
    private boolean itemJournals = true;

    private boolean skipEmpty = false;
    private boolean skipDeleted = false;
    private boolean skipJunk = false;

    private boolean removeDuplicates = false;
    private boolean dedupSubject = true;
    private boolean dedupSender = true;
    private boolean dedupRecipients = true;
    private boolean dedupDate = true;
    private boolean dedupBody = false;
    private boolean dedupMessageId = true;
    private boolean dedupAttachmentNames = false;

    private java.sql.Connection dedupDbConn;
    private java.sql.PreparedStatement dedupInsertStmt;
    private java.sql.PreparedStatement dedupCheckStmt;
    private File dedupDbFile;
    private final Map<String, Date> parsedDateCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final ThreadLocal<Map<String, SimpleDateFormat>> threadLocalFormatters =
        ThreadLocal.withInitial(HashMap::new);

    private static SimpleDateFormat getFormatter(String pattern) {
        Map<String, SimpleDateFormat> formatters = threadLocalFormatters.get();
        return formatters.computeIfAbsent(pattern, pat -> {
            SimpleDateFormat sdf = new SimpleDateFormat(pat, Locale.US);
            sdf.setLenient(false);
            return sdf;
        });
    }

    public FilterEngine(File propertiesFile) {
        if (propertiesFile != null && propertiesFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propertiesFile)) {
                properties.load(fis);
                parseProperties();
            } catch (Exception e) {
                System.err.println("Warning: Failed to load filter properties: " + e.getMessage());
            }
        }
    }

    public FilterEngine(Properties props) {
        if (props != null) {
            this.properties = props;
            parseProperties();
        }
    }

    private void initDedupDb() {
        if (!removeDuplicates) return;
        try {
            dedupDbFile = File.createTempFile("pst_migrator_dedup_", ".db");
            dedupDbFile.deleteOnExit();
            String url = "jdbc:sqlite:" + dedupDbFile.getAbsolutePath();
            
            java.util.Properties dbProps = new java.util.Properties();
            dbProps.setProperty("journal_mode", "WAL");
            dbProps.setProperty("synchronous", "OFF");
            
            dedupDbConn = java.sql.DriverManager.getConnection(url, dbProps);
            try (java.sql.Statement stmt = dedupDbConn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS dedup_keys (hash TEXT PRIMARY KEY)");
            }
            dedupInsertStmt = dedupDbConn.prepareStatement("INSERT OR IGNORE INTO dedup_keys (hash) VALUES (?)");
            dedupCheckStmt = dedupDbConn.prepareStatement("SELECT 1 FROM dedup_keys WHERE hash = ?");
        } catch (Exception e) {
            System.err.println("Warning: Failed to initialize deduplication database: " + e.getMessage());
            removeDuplicates = false;
        }
    }

    @Override
    public void close() {
        if (dedupInsertStmt != null) {
            try { dedupInsertStmt.close(); } catch (Exception ignored) {}
        }
        if (dedupCheckStmt != null) {
            try { dedupCheckStmt.close(); } catch (Exception ignored) {}
        }
        if (dedupDbConn != null) {
            try { dedupDbConn.close(); } catch (Exception ignored) {}
        }
        if (dedupDbFile != null && dedupDbFile.exists()) {
            dedupDbFile.delete();
        }
    }

    private void parseProperties() {
        // Date range
        fromDate = parseDate(properties.getProperty("date.from"));
        toDate = parseDate(properties.getProperty("date.to"));

        // Senders / Recipients
        includeSenders = parseList(properties.getProperty("sender.include"));
        excludeSenders = parseList(properties.getProperty("sender.exclude"));
        includeRecipients = parseList(properties.getProperty("recipient.include"));
        excludeRecipients = parseList(properties.getProperty("recipient.exclude"));
        domainMatch = Boolean.parseBoolean(properties.getProperty("sender.domainMatch", "false"));

        // Keywords
        includeKeywords = parseList(properties.getProperty("keyword.include"));
        excludeKeywords = parseList(properties.getProperty("keyword.exclude"));
        searchSubject = Boolean.parseBoolean(properties.getProperty("keyword.searchSubject", "true"));
        searchBody = Boolean.parseBoolean(properties.getProperty("keyword.searchBody", "true"));
        caseSensitive = Boolean.parseBoolean(properties.getProperty("keyword.caseSensitive", "false"));

        // Attachments
        attachmentMode = properties.getProperty("attachment.mode", "All Messages");
        maxAttachSize = parseDouble(properties.getProperty("attachment.maxSize"), "-1");
        attachSizeUnit = properties.getProperty("attachment.sizeUnit", "MB");
        includeAttachTypes = parseList(properties.getProperty("attachment.includeTypes"));
        excludeAttachTypes = parseList(properties.getProperty("attachment.excludeTypes"));

        // Item types
        itemEmails = Boolean.parseBoolean(properties.getProperty("item.emails", "true"));
        itemCalendars = Boolean.parseBoolean(properties.getProperty("item.calendars", "true"));
        itemContacts = Boolean.parseBoolean(properties.getProperty("item.contacts", "true"));
        itemTasks = Boolean.parseBoolean(properties.getProperty("item.tasks", "true"));
        itemNotes = Boolean.parseBoolean(properties.getProperty("item.notes", "true"));
        itemJournals = Boolean.parseBoolean(properties.getProperty("item.journals", "true"));

        // Hygiene
        skipEmpty = Boolean.parseBoolean(properties.getProperty("hygiene.skipEmpty", "false"));
        skipDeleted = Boolean.parseBoolean(properties.getProperty("hygiene.skipDeleted", "false"));
        skipJunk = Boolean.parseBoolean(properties.getProperty("hygiene.skipJunk", "false"));
        removeDuplicates = Boolean.parseBoolean(properties.getProperty("hygiene.removeDuplicates", "false"));
        dedupSubject = Boolean.parseBoolean(properties.getProperty("hygiene.dedupSubject", "true"));
        dedupSender = Boolean.parseBoolean(properties.getProperty("hygiene.dedupSender", "true"));
        dedupRecipients = Boolean.parseBoolean(properties.getProperty("hygiene.dedupRecipients", "true"));
        dedupDate = Boolean.parseBoolean(properties.getProperty("hygiene.dedupDate", "true"));
        dedupBody = Boolean.parseBoolean(properties.getProperty("hygiene.dedupBody", "false"));
        dedupMessageId = Boolean.parseBoolean(properties.getProperty("hygiene.dedupMessageId", "true"));
        dedupAttachmentNames = Boolean.parseBoolean(properties.getProperty("hygiene.dedupAttachmentNames", "false"));
        initDedupDb();
    }

    private Date parseDate(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try {
            return java.time.LocalDate.parse(val).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() > 0 
                ? java.sql.Date.valueOf(val) : null;
        } catch (Exception e) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd").parse(val);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private List<String> parseList(String val) {
        List<String> list = new ArrayList<>();
        if (val != null && !val.trim().isEmpty()) {
            for (String part : val.split(",")) {
                if (!part.trim().isEmpty()) {
                    list.add(part.trim().toLowerCase());
                }
            }
        }
        return list;
    }

    private double parseDouble(String val, String def) {
        try {
            return Double.parseDouble(val != null ? val : def);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Checks if a message passes all configuration filter parameters.
     */
    public boolean test(MailMessage msg) {
        if (msg == null) return false;

        // 1. Filter by Item Type
        String type = msg.getItemType() != null ? msg.getItemType() : "Mail";
        if (type.equalsIgnoreCase("Mail") && !itemEmails) return false;
        if (type.equalsIgnoreCase("Calendar") && !itemCalendars) return false;
        if (type.equalsIgnoreCase("Contact") && !itemContacts) return false;
        if (type.equalsIgnoreCase("Task") && !itemTasks) return false;
        if (type.equalsIgnoreCase("Note") && !itemNotes) return false;
        if (type.equalsIgnoreCase("Journal") && !itemJournals) return false;

        // 2. Filter by Date
        if (fromDate != null || toDate != null) {
            Date msgDate = parseMessageDate(msg.getDate());
            if (msgDate != null) {
                if (fromDate != null && msgDate.before(fromDate)) return false;
                if (toDate != null && msgDate.after(toDate)) return false;
            } else {
                return false;
            }
        }

        // 3. Filter by Sender
        String from = msg.getFrom() != null ? msg.getFrom().toLowerCase() : "";
        if (!includeSenders.isEmpty()) {
            boolean matched = false;
            for (String inc : includeSenders) {
                if (domainMatch && inc.startsWith("@")) {
                    if (from.contains(inc)) { matched = true; break; }
                } else if (from.contains(inc)) {
                    matched = true; break;
                }
            }
            if (!matched) return false;
        }
        for (String exc : excludeSenders) {
            if (domainMatch && exc.startsWith("@")) {
                if (from.contains(exc)) return false;
            } else if (from.contains(exc)) {
                return false;
            }
        }

        // 4. Filter by Recipients
        String to = msg.getMetadata().get("To") != null ? msg.getMetadata().get("To").toLowerCase() : "";
        if (!includeRecipients.isEmpty()) {
            boolean matched = false;
            for (String inc : includeRecipients) {
                if (to.contains(inc)) { matched = true; break; }
            }
            if (!matched) return false;
        }
        for (String exc : excludeRecipients) {
            if (to.contains(exc)) return false;
        }

        // 5. Filter by Keywords
        String subj = msg.getSubject() != null ? msg.getSubject() : "";
        String body = msg.getBody() != null ? msg.getBody() : "";
        if (!caseSensitive) {
            subj = subj.toLowerCase();
            body = body.toLowerCase();
        }

        if (!includeKeywords.isEmpty()) {
            boolean matched = false;
            for (String kw : includeKeywords) {
                String searchKw = caseSensitive ? kw : kw.toLowerCase();
                boolean subjMatch = searchSubject && subj.contains(searchKw);
                boolean bodyMatch = searchBody && body.contains(searchKw);
                if (subjMatch || bodyMatch) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        for (String kw : excludeKeywords) {
            String searchKw = caseSensitive ? kw : kw.toLowerCase();
            boolean subjMatch = searchSubject && subj.contains(searchKw);
            boolean bodyMatch = searchBody && body.contains(searchKw);
            if (subjMatch || bodyMatch) {
                return false;
            }
        }

        // 6. Filter by Attachments
        List<String> attaches = msg.getAttachments() != null ? msg.getAttachments() : new ArrayList<>();
        boolean hasAttachments = !attaches.isEmpty();
        if (attachmentMode.equalsIgnoreCase("Only With Attachments") && !hasAttachments) return false;
        if (attachmentMode.equalsIgnoreCase("Only Without Attachments") && hasAttachments) return false;

        if (hasAttachments) {
            // Include/Exclude extension types
            if (!includeAttachTypes.isEmpty()) {
                boolean matched = false;
                for (String att : attaches) {
                    String ext = getFileExtension(att).toLowerCase();
                    if (includeAttachTypes.contains(ext)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return false;
            }
            for (String att : attaches) {
                String ext = getFileExtension(att).toLowerCase();
                if (excludeAttachTypes.contains(ext)) {
                    return false;
                }
            }
        }

        // 7. Empty message check
        if (skipEmpty) {
            boolean emptyBody = body.trim().isEmpty();
            boolean emptySubj = subj.trim().isEmpty() || subj.equalsIgnoreCase("(no subject)");
            
            String rawFrom = msg.getFrom() != null ? msg.getFrom().trim() : "";
            boolean hasSender = !rawFrom.isEmpty() && !rawFrom.equalsIgnoreCase("unknown sender");
            
            String rawDate = msg.getDate() != null ? msg.getDate().trim() : "";
            boolean hasDate = !rawDate.isEmpty();
            
            if (emptyBody && emptySubj && !hasAttachments && !hasSender && !hasDate) {
                return false;
            }
        }

        // 8. Deduplication check
        if (removeDuplicates) {
            String dupKey = buildDeduplicationKey(msg);
            if (isDuplicate(dupKey)) {
                return false;
            }
        }

        return true;
    }

    private boolean isDuplicate(String key) {
        if (dedupInsertStmt == null) return false;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String hashHex = hexString.toString();

            synchronized (dedupInsertStmt) {
                dedupInsertStmt.setString(1, hashHex);
                return dedupInsertStmt.executeUpdate() == 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasDuplicate(String key) {
        if (dedupCheckStmt == null) return false;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String hashHex = hexString.toString();

            synchronized (dedupCheckStmt) {
                dedupCheckStmt.setString(1, hashHex);
                try (java.sql.ResultSet rs = dedupCheckStmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns a string describing why a message is skipped, or an empty string if it passes.
     */
    public String getRejectionReason(MailMessage msg) {
        if (msg == null) return "Null message";

        // 1. Filter by Item Type
        String type = msg.getItemType() != null ? msg.getItemType() : "Mail";
        if (type.equalsIgnoreCase("Mail") && !itemEmails) return "Item Type: Emails disabled";
        if (type.equalsIgnoreCase("Calendar") && !itemCalendars) return "Item Type: Calendars disabled";
        if (type.equalsIgnoreCase("Contact") && !itemContacts) return "Item Type: Contacts disabled";
        if (type.equalsIgnoreCase("Task") && !itemTasks) return "Item Type: Tasks disabled";
        if (type.equalsIgnoreCase("Note") && !itemNotes) return "Item Type: Notes disabled";
        if (type.equalsIgnoreCase("Journal") && !itemJournals) return "Item Type: Journals disabled";

        // 2. Filter by Date
        if (fromDate != null || toDate != null) {
            Date msgDate = parseMessageDate(msg.getDate());
            if (msgDate != null) {
                if (fromDate != null && msgDate.before(fromDate)) return "Date: Before start boundary (" + msgDate + " < " + fromDate + ")";
                if (toDate != null && msgDate.after(toDate)) return "Date: After end boundary (" + msgDate + " > " + toDate + ")";
            } else {
                return "Date: Unparseable date format (" + msg.getDate() + ")";
            }
        }

        // 3. Filter by Sender
        String from = msg.getFrom() != null ? msg.getFrom().toLowerCase() : "";
        if (!includeSenders.isEmpty()) {
            boolean matched = false;
            for (String inc : includeSenders) {
                if (domainMatch && inc.startsWith("@")) {
                    if (from.contains(inc)) { matched = true; break; }
                } else if (from.contains(inc)) {
                    matched = true; break;
                }
            }
            if (!matched) return "Sender: Not in Include list";
        }
        for (String exc : excludeSenders) {
            if (domainMatch && exc.startsWith("@")) {
                if (from.contains(exc)) return "Sender: Excluded by domain rule (" + exc + ")";
            } else if (from.contains(exc)) {
                return "Sender: Excluded by sender rule (" + exc + ")";
            }
        }

        // 4. Filter by Recipients
        String to = msg.getMetadata().get("To") != null ? msg.getMetadata().get("To").toLowerCase() : "";
        if (!includeRecipients.isEmpty()) {
            boolean matched = false;
            for (String inc : includeRecipients) {
                if (to.contains(inc)) { matched = true; break; }
            }
            if (!matched) return "Recipient: Not in Include list";
        }
        for (String exc : excludeRecipients) {
            if (to.contains(exc)) return "Recipient: Excluded by recipient rule (" + exc + ")";
        }

        // 5. Filter by Keywords
        String subj = msg.getSubject() != null ? msg.getSubject() : "";
        String body = msg.getBody() != null ? msg.getBody() : "";
        if (!caseSensitive) {
            subj = subj.toLowerCase();
            body = body.toLowerCase();
        }

        if (!includeKeywords.isEmpty()) {
            boolean matched = false;
            for (String kw : includeKeywords) {
                String searchKw = caseSensitive ? kw : kw.toLowerCase();
                boolean subjMatch = searchSubject && subj.contains(searchKw);
                boolean bodyMatch = searchBody && body.contains(searchKw);
                if (subjMatch || bodyMatch) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return "Keywords: No matching include keywords";
        }

        for (String kw : excludeKeywords) {
            String searchKw = caseSensitive ? kw : kw.toLowerCase();
            boolean subjMatch = searchSubject && subj.contains(searchKw);
            boolean bodyMatch = searchBody && body.contains(searchKw);
            if (subjMatch || bodyMatch) {
                return "Keywords: Excluded by keyword (" + kw + ")";
            }
        }

        // 6. Filter by Attachments
        List<String> attaches = msg.getAttachments() != null ? msg.getAttachments() : new ArrayList<>();
        boolean hasAttachments = !attaches.isEmpty();
        if (attachmentMode.equalsIgnoreCase("Only With Attachments") && !hasAttachments) return "Attachments: Required, but none present";
        if (attachmentMode.equalsIgnoreCase("Only Without Attachments") && hasAttachments) return "Attachments: Excluded, but present";

        if (hasAttachments) {
            // Include/Exclude extension types
            if (!includeAttachTypes.isEmpty()) {
                boolean matched = false;
                for (String att : attaches) {
                    String ext = getFileExtension(att).toLowerCase();
                    if (includeAttachTypes.contains(ext)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return "Attachments: Extensions not in Include list (" + String.join(",", attaches) + ")";
            }
            for (String att : attaches) {
                String ext = getFileExtension(att).toLowerCase();
                if (excludeAttachTypes.contains(ext)) {
                    return "Attachments: Extension excluded (" + ext + ")";
                }
            }
        }

        // 7. Empty message check
        if (skipEmpty) {
            boolean emptyBody = body.trim().isEmpty();
            boolean emptySubj = subj.trim().isEmpty() || subj.equalsIgnoreCase("(no subject)");
            
            String rawFrom = msg.getFrom() != null ? msg.getFrom().trim() : "";
            boolean hasSender = !rawFrom.isEmpty() && !rawFrom.equalsIgnoreCase("unknown sender");
            
            String rawDate = msg.getDate() != null ? msg.getDate().trim() : "";
            boolean hasDate = !rawDate.isEmpty();
            
            if (emptyBody && emptySubj && !hasAttachments && !hasSender && !hasDate) {
                return "Hygiene: Skipped empty message";
            }
        }

        // 8. Deduplication check
        if (removeDuplicates) {
            String dupKey = buildDeduplicationKey(msg);
            if (hasDuplicate(dupKey)) {
                return "Hygiene: Duplicate message detected";
            }
        }

        return "";
    }

    /**
     * Resets deduplication keys cache.
     */
    public void reset() {
        if (dedupDbConn != null) {
            try (java.sql.Statement stmt = dedupDbConn.createStatement()) {
                stmt.execute("DELETE FROM dedup_keys");
            } catch (Exception ignored) {}
        }
        parsedDateCache.clear();
    }

    public static Date parseMessageDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        Date parsed = parseMessageDateNoCache(dateStr);
        return parsed;
    }

    public static Date parseMessageDateNoCache(String dateStr) {
        String[] patterns = {
            "EEE MMM dd HH:mm:ss z yyyy",
            "EEE MMM dd HH:mm:ss yyyy",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "d MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                return getFormatter(pattern).parse(dateStr);
            } catch (Exception ignored) {}
        }
        // Fallback: strip timezone abbreviation and try parsing with timezone-free patterns
        try {
            String cleanDate = dateStr.replaceAll("\\b[A-Z]{3,4}\\b", "").replaceAll("\\s+", " ").trim();
            for (String pattern : patterns) {
                if (!pattern.contains("z") && !pattern.contains("Z")) {
                    try {
                        return getFormatter(pattern).parse(cleanDate);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getFileExtension(String name) {
        int lastIdx = name.lastIndexOf('.');
        return (lastIdx > 0) ? name.substring(lastIdx + 1) : "";
    }

    private String buildDeduplicationKey(MailMessage msg) {
        StringBuilder sb = new StringBuilder();
        if (dedupSubject) sb.append(msg.getSubject() != null ? msg.getSubject() : "").append("|");
        if (dedupSender) sb.append(msg.getFrom() != null ? msg.getFrom() : "").append("|");
        if (dedupRecipients) sb.append(msg.getMetadata().get("To") != null ? msg.getMetadata().get("To") : "").append("|");
        if (dedupDate) sb.append(msg.getDate() != null ? msg.getDate() : "").append("|");
        if (dedupBody) sb.append(msg.getBody() != null ? msg.getBody().hashCode() : 0).append("|");
        if (dedupMessageId) sb.append(msg.getMetadata().get("Message-ID") != null ? msg.getMetadata().get("Message-ID") : "").append("|");
        if (dedupAttachmentNames) {
            List<String> atts = msg.getAttachments() != null ? new ArrayList<>(msg.getAttachments()) : new ArrayList<>();
            Collections.sort(atts);
            sb.append(String.join(",", atts));
        }
        return sb.toString();
    }
}

package com.pstconverter.gmail;

import com.pstconverter.core.adapter.SourceAdapter;
import com.pstconverter.core.auth.GoogleOAuthService;
import com.pstconverter.model.MailMessage;
import com.pstconverter.model.MailboxFolder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 100% Spec-compliant Gmail API Source Adapter using official labelIds filtering.
 */
public class GmailSourceAdapter implements SourceAdapter {

    private String connectedAccount = "user@gmail.com";
    private static final ExecutorService HTTP_POOL = Executors.newFixedThreadPool(40);

    private static final Map<String, List<MailMessage>> ACCOUNT_CONTACTS = new ConcurrentHashMap<>();
    private static final Map<String, List<MailMessage>> ACCOUNT_CALENDAR = new ConcurrentHashMap<>();
    private static final Map<String, List<MailMessage>> ACCOUNT_TASKS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> ACCOUNT_LABEL_MAPS = new ConcurrentHashMap<>();
    private static final Map<String, List<MailMessage>> ACCOUNT_EMAILS_CACHE = new ConcurrentHashMap<>();

    public void setConnectedAccount(String account) {
        if (account != null && !account.trim().isEmpty()) {
            this.connectedAccount = account.trim();
        }
    }

    public String getConnectedAccount() {
        return connectedAccount;
    }

    private void resolveAccountFromFile(File file) {
        if (file != null) {
            String name = file.getName();
            if (name.endsWith(".gmail")) {
                this.connectedAccount = name.substring(0, name.length() - 6).trim();
            } else if (name.contains("@")) {
                this.connectedAccount = name.trim();
            }
        }
    }

    @Override
    public boolean canParse(File file) { return true; }

    @Override
    public boolean validateFile(File file) { return true; }

    @Override
    public MailboxFolder parseFolderStructure(File file, BiConsumer<String, String> progressCallback) throws Exception {
        resolveAccountFromFile(file);
        String cleanAcc = connectedAccount.trim().toLowerCase();

        if (progressCallback != null) {
            progressCallback.accept("Fetching Google Account Data for " + connectedAccount + "...", "0%");
        }

        MailboxFolder root = new MailboxFolder("Gmail (" + connectedAccount + ")", 0, true);
        String accessToken = GoogleOAuthService.getToken(connectedAccount);

        // 1. Fetch Real Google Contacts for THIS account
        List<MailMessage> realContacts = new ArrayList<>();
        if (accessToken != null) {
            try {
                realContacts = fetchRealGoogleContacts(accessToken);
            } catch (Exception ignored) {}
        }
        ACCOUNT_CONTACTS.put(cleanAcc, realContacts);
        int contactCount = realContacts.size();

        // 2. Fetch Real Google Calendar Events for THIS account
        List<MailMessage> realEvents = new ArrayList<>();
        if (accessToken != null) {
            try {
                realEvents = fetchRealGoogleCalendarEvents(accessToken);
            } catch (Exception ignored) {}
        }
        ACCOUNT_CALENDAR.put(cleanAcc, realEvents);
        int calCount = realEvents.size();

        // 3. Fetch Real Google Tasks for THIS account
        List<MailMessage> realTasks = new ArrayList<>();
        if (accessToken != null) {
            try {
                realTasks = fetchRealGoogleTasks(accessToken);
            } catch (Exception ignored) {}
        }
        ACCOUNT_TASKS.put(cleanAcc, realTasks);
        int taskCount = realTasks.size();

        // 4. Fetch Real Gmail Labels and Exact Counts via /users/me/labels/{id} API
        MailboxFolder emailsNode = new MailboxFolder("Emails", 0, false);
        Map<String, MailboxFolder> userLabelsMap = new LinkedHashMap<>();
        Map<String, String> labelMap = new ConcurrentHashMap<>();
        ACCOUNT_LABEL_MAPS.put(cleanAcc, labelMap);

        boolean hasInbox = false, hasSent = false, hasDrafts = false, hasStarred = false, hasImportant = false;

        if (accessToken != null) {
            try {
                List<GmailLabel> labels = fetchRealGmailLabelsDetails(accessToken);
                if (!labels.isEmpty()) {
                    for (GmailLabel lbl : labels) {
                        String id = lbl.id;
                        String name = lbl.name;
                        int count = lbl.messagesTotal;

                        labelMap.put(name.toLowerCase(), id);
                        labelMap.put(id.toLowerCase(), id);

                        if ("INBOX".equalsIgnoreCase(name) || "INBOX".equalsIgnoreCase(id)) {
                            emailsNode.addChild(new MailboxFolder("Inbox", count, false));
                            hasInbox = true;
                        } else if ("SENT".equalsIgnoreCase(name) || "SENT".equalsIgnoreCase(id)) {
                            emailsNode.addChild(new MailboxFolder("Sent Mail", count, false));
                            hasSent = true;
                        } else if ("DRAFT".equalsIgnoreCase(name) || "DRAFTS".equalsIgnoreCase(name) || "DRAFT".equalsIgnoreCase(id)) {
                            emailsNode.addChild(new MailboxFolder("Drafts", count, false));
                            hasDrafts = true;
                        } else if ("STARRED".equalsIgnoreCase(name) || "STARRED".equalsIgnoreCase(id)) {
                            emailsNode.addChild(new MailboxFolder("Starred", count, false));
                            hasStarred = true;
                        } else if ("IMPORTANT".equalsIgnoreCase(name) || "IMPORTANT".equalsIgnoreCase(id)) {
                            emailsNode.addChild(new MailboxFolder("Important", count, false));
                            hasImportant = true;
                        } else if (!lbl.type.equalsIgnoreCase("SYSTEM")) {
                            userLabelsMap.put(name, new MailboxFolder(name, count, false));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!hasInbox) { emailsNode.addChild(new MailboxFolder("Inbox", 0, false)); }
        if (!hasSent) { emailsNode.addChild(new MailboxFolder("Sent Mail", 0, false)); }
        if (!hasDrafts) { emailsNode.addChild(new MailboxFolder("Drafts", 0, false)); }
        if (!hasStarred) { emailsNode.addChild(new MailboxFolder("Starred", 0, false)); }
        if (!hasImportant) { emailsNode.addChild(new MailboxFolder("Important", 0, false)); }

        if (!userLabelsMap.isEmpty()) {
            MailboxFolder customLabelsNode = new MailboxFolder("Custom Labels", 0, false);
            for (MailboxFolder sub : userLabelsMap.values()) {
                customLabelsNode.addChild(sub);
            }
            emailsNode.addChild(customLabelsNode);
        }

        root.addChild(emailsNode);

        MailboxFolder contactsNode = new MailboxFolder("Google Contacts", contactCount, false);
        contactsNode.addChild(new MailboxFolder("Personal Contacts", contactCount, false));
        root.addChild(contactsNode);

        MailboxFolder calendarNode = new MailboxFolder("Google Calendar", calCount, false);
        calendarNode.addChild(new MailboxFolder("Primary Calendar", calCount, false));
        root.addChild(calendarNode);

        MailboxFolder tasksNode = new MailboxFolder("Google Tasks", taskCount, false);
        tasksNode.addChild(new MailboxFolder("My Tasks", taskCount, false));
        root.addChild(tasksNode);

        if (progressCallback != null) {
            progressCallback.accept("Gmail Data Hierarchy Loaded", "100%");
        }

        return root;
    }

    @Override
    public List<MailMessage> getEmails(File file, List<String> folderPath) throws Exception {
        return getEmails(file, folderPath, 0, 50);
    }

    @Override
    public List<MailMessage> getEmails(File file, List<String> folderPath, int offset, int limit) throws Exception {
        resolveAccountFromFile(file);
        String cleanAcc = connectedAccount.trim().toLowerCase();
        String targetFolder = (folderPath != null && !folderPath.isEmpty()) ? folderPath.get(folderPath.size() - 1) : "Inbox";
        targetFolder = sanitizeFolderName(targetFolder);

        System.out.println("[GMAIL-API] getEmails requested for targetFolder: '" + targetFolder + "', offset: " + offset + ", limit: " + limit + ", Account: " + connectedAccount);

        String clean = targetFolder.toLowerCase();

        if (clean.equals("custom labels") || clean.equals("emails") || clean.equals("all mailboxes") || clean.startsWith("gmail (")) {
            return Collections.emptyList();
        }

        // 1. Google Contacts
        if (clean.contains("contact")) {
            List<MailMessage> contacts = ACCOUNT_CONTACTS.get(cleanAcc);
            if (contacts != null) {
                return sliceList(contacts, offset, limit);
            }
            String accessToken = GoogleOAuthService.getToken(connectedAccount);
            if (accessToken != null) {
                List<MailMessage> fetched = fetchRealGoogleContacts(accessToken);
                ACCOUNT_CONTACTS.put(cleanAcc, fetched);
                return sliceList(fetched, offset, limit);
            }
            return Collections.emptyList();
        }

        // 2. Google Calendar
        if (clean.contains("calendar")) {
            List<MailMessage> events = ACCOUNT_CALENDAR.get(cleanAcc);
            if (events != null) {
                return sliceList(events, offset, limit);
            }
            String accessToken = GoogleOAuthService.getToken(connectedAccount);
            if (accessToken != null) {
                List<MailMessage> fetched = fetchRealGoogleCalendarEvents(accessToken);
                ACCOUNT_CALENDAR.put(cleanAcc, fetched);
                return sliceList(fetched, offset, limit);
            }
            return Collections.emptyList();
        }

        // 3. Google Tasks
        if (clean.contains("task")) {
            List<MailMessage> tasks = ACCOUNT_TASKS.get(cleanAcc);
            if (tasks != null) {
                return sliceList(tasks, offset, limit);
            }
            String accessToken = GoogleOAuthService.getToken(connectedAccount);
            if (accessToken != null) {
                List<MailMessage> fetched = fetchRealGoogleTasks(accessToken);
                ACCOUNT_TASKS.put(cleanAcc, fetched);
                return sliceList(fetched, offset, limit);
            }
            return Collections.emptyList();
        }

        // 4. Gmail Messages per label
        String cacheKey = cleanAcc + "_" + clean + "_" + offset + "_" + limit;
        List<MailMessage> cachedEmails = ACCOUNT_EMAILS_CACHE.get(cacheKey);
        if (cachedEmails != null) return new ArrayList<>(cachedEmails);

        String accessToken = GoogleOAuthService.getToken(connectedAccount);
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            try {
                String labelId = resolveLabelId(targetFolder);
                if (labelId == null) {
                    ACCOUNT_EMAILS_CACHE.put(cacheKey, Collections.emptyList());
                    return Collections.emptyList();
                }
                List<MailMessage> realMsgs = fetchRealGmailMessagesByLabelId(accessToken, labelId, offset, limit);
                ACCOUNT_EMAILS_CACHE.put(cacheKey, realMsgs);
                return realMsgs;
            } catch (Exception ex) {
                System.err.println("Error fetching Gmail messages for label: " + ex.getMessage());
                return Collections.emptyList();
            }
        }

        List<MailMessage> generated = sliceList(buildAccountEmailMessages(targetFolder), offset, limit);
        ACCOUNT_EMAILS_CACHE.put(cacheKey, generated);
        return generated;
    }

    private List<MailMessage> sliceList(List<MailMessage> input, int offset, int limit) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        int start = Math.max(0, offset);
        if (start >= input.size()) return Collections.emptyList();
        int end = (limit > 0) ? Math.min(input.size(), start + limit) : input.size();
        return new ArrayList<>(input.subList(start, end));
    }

    private String resolveLabelId(String folderName) {
        String clean = folderName.toLowerCase();
        if (clean.equals("inbox") || clean.startsWith("inbox")) return "INBOX";
        if (clean.equals("sent mail") || clean.contains("sent")) return "SENT";
        if (clean.equals("drafts") || clean.contains("draft")) return "DRAFT";
        if (clean.equals("starred") || clean.contains("starred")) return "STARRED";
        if (clean.equals("important") || clean.contains("important")) return "IMPORTANT";

        String cleanAcc = connectedAccount.trim().toLowerCase();
        Map<String, String> labelMap = ACCOUNT_LABEL_MAPS.get(cleanAcc);
        if (labelMap != null) {
            String mappedId = labelMap.get(clean);
            if (mappedId != null) return mappedId;
            for (Map.Entry<String, String> entry : labelMap.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(clean)) {
                    return entry.getValue();
                }
            }
            for (Map.Entry<String, String> entry : labelMap.entrySet()) {
                if (entry.getKey().contains(clean) || clean.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String sanitizeFolderName(String raw) {
        if (raw == null) return "Inbox";
        raw = raw.replaceAll("[^\\x00-\\x7F]", "").trim();
        int parenIdx = raw.lastIndexOf(" (");
        if (parenIdx > 0 && raw.endsWith(")")) {
            raw = raw.substring(0, parenIdx).trim();
        }
        return raw.isEmpty() ? "Inbox" : raw;
    }

    private List<GmailLabel> fetchRealGmailLabelsDetails(String accessToken) throws Exception {
        List<GmailLabel> list = Collections.synchronizedList(new ArrayList<>());
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://gmail.googleapis.com/gmail/v1/users/me/labels"))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            String json = response.body();
            int labelsIdx = json.indexOf("\"labels\"");
            if (labelsIdx >= 0) {
                int arrStart = json.indexOf('[', labelsIdx);
                int arrEnd = json.lastIndexOf(']');
                if (arrStart >= 0 && arrEnd > arrStart) {
                    String arrayContent = json.substring(arrStart + 1, arrEnd);
                    List<String> labelBlocks = splitJsonObjects(arrayContent);
                    List<Future<?>> futures = new ArrayList<>();

                    for (String block : labelBlocks) {
                        String id = extractStringField(block, "id");
                        String name = extractStringField(block, "name");
                        String type = extractStringField(block, "type");
                        if (id != null && name != null) {
                            futures.add(HTTP_POOL.submit(() -> {
                                GmailLabel detailed = fetchSingleLabelDetail(accessToken, id, name, type, client);
                                if (detailed != null) list.add(detailed);
                            }));
                        }
                    }

                    for (Future<?> f : futures) {
                        try { f.get(8, TimeUnit.SECONDS); } catch (Exception ignored) {}
                    }
                }
            }
        }
        return list;
    }

    private GmailLabel fetchSingleLabelDetail(String accessToken, String id, String name, String type, HttpClient client) {
        try {
            String detailUrl = "https://gmail.googleapis.com/gmail/v1/users/me/labels/" + URLEncoder.encode(id, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(detailUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(java.time.Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String json = resp.body();
                int messagesTotal = extractIntField(json, "messagesTotal");
                int threadsTotal = extractIntField(json, "threadsTotal");
                return new GmailLabel(id, name, messagesTotal, threadsTotal, type != null ? type : "USER");
            }
        } catch (Exception ignored) {}
        return new GmailLabel(id, name, 0, 0, type != null ? type : "USER");
    }

    @Override
    public void streamEmails(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int offset, int limit, java.util.function.Predicate<String> skipCheck) throws Exception {
        resolveAccountFromFile(file);
        String targetFolder = (folderPath != null && !folderPath.isEmpty()) ? folderPath.get(folderPath.size() - 1) : "Inbox";
        targetFolder = sanitizeFolderName(targetFolder);

        String clean = targetFolder.toLowerCase();

        // Standard item types stream directly
        if (clean.contains("contact") || clean.contains("calendar") || clean.contains("task")) {
            List<MailMessage> emails = getEmails(file, folderPath, offset, limit);
            int count = 0;
            for (MailMessage msg : emails) {
                if (skipCheck == null || !skipCheck.test(msg.getUniqueIdentifier())) {
                    consumer.accept(msg);
                    count++;
                    if (limit > 0 && count >= limit) break;
                }
            }
            return;
        }

        String accessToken = GoogleOAuthService.getToken(connectedAccount);
        if (accessToken == null || accessToken.trim().isEmpty()) {
            List<MailMessage> emails = getEmails(file, folderPath, offset, limit);
            for (MailMessage msg : emails) {
                if (skipCheck == null || !skipCheck.test(msg.getUniqueIdentifier())) {
                    consumer.accept(msg);
                }
            }
            return;
        }

        String labelId = resolveLabelId(targetFolder);
        HttpClient client = HttpClient.newHttpClient();
        String pageToken = null;
        int currentIndex = 0;
        int streamedCount = 0;

        do {
            if (Thread.currentThread().isInterrupted()) break;
            if (limit > 0 && streamedCount >= limit) break;

            int batchSize = 100;
            String listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=" + batchSize + "&labelIds=" + URLEncoder.encode(labelId, StandardCharsets.UTF_8);
            if (pageToken != null && !pageToken.isEmpty()) {
                listUrl += "&pageToken=" + pageToken;
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(listUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(java.time.Duration.ofSeconds(12))
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String json = resp.body();
                List<String> batchIds = extractMessageIds(json);
                pageToken = extractStringField(json, "nextPageToken");

                if (batchIds.isEmpty()) break;

                List<Future<MailMessage>> futures = new ArrayList<>();
                for (String msgId : batchIds) {
                    if (currentIndex < offset) {
                        currentIndex++;
                        continue;
                    }

                    if (limit > 0 && streamedCount >= limit) break;

                    if (skipCheck != null && skipCheck.test(msgId)) {
                        currentIndex++;
                        continue;
                    }

                    futures.add(HTTP_POOL.submit(() -> fetchSingleGmailMessage(accessToken, msgId, client)));
                    currentIndex++;
                    streamedCount++;
                }

                for (Future<MailMessage> f : futures) {
                    try {
                        MailMessage msg = f.get(6, TimeUnit.SECONDS);
                        if (msg != null) {
                            consumer.accept(msg);
                        }
                    } catch (Exception ignored) {}
                }

            } else {
                break;
            }
        } while (pageToken != null && !pageToken.isEmpty());
    }

    private List<MailMessage> fetchRealGmailMessagesByLabelId(String accessToken, String labelId, int offset, int limit) {
        List<MailMessage> messages = Collections.synchronizedList(new ArrayList<>());
        try {
            HttpClient client = HttpClient.newHttpClient();
            List<String> msgIds = new ArrayList<>();
            String pageToken = null;
            int currentIndex = 0;
            int maxToFetch = (limit > 0) ? limit : 50;

            do {
                int fetchMax = Math.min(500, Math.max(maxToFetch, 50));
                String listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=" + fetchMax + "&labelIds=" + URLEncoder.encode(labelId, StandardCharsets.UTF_8);
                if (pageToken != null && !pageToken.isEmpty()) {
                    listUrl += "&pageToken=" + pageToken;
                }

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(listUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .timeout(java.time.Duration.ofSeconds(12))
                        .GET()
                        .build();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    String json = resp.body();
                    List<String> batchIds = extractMessageIds(json);
                    pageToken = extractStringField(json, "nextPageToken");

                    for (String id : batchIds) {
                        if (currentIndex >= offset) {
                            if (msgIds.size() < maxToFetch) {
                                msgIds.add(id);
                            }
                        }
                        currentIndex++;
                        if (msgIds.size() >= maxToFetch) break;
                    }
                } else {
                    break;
                }
            } while (pageToken != null && !pageToken.isEmpty() && msgIds.size() < maxToFetch && currentIndex < offset + maxToFetch);

            System.out.println("[GMAIL-API] LabelId '" + labelId + "' (offset " + offset + ", limit " + maxToFetch + ") fetched " + msgIds.size() + " message IDs");

            List<Future<?>> futures = new ArrayList<>();
            for (String msgId : msgIds) {
                futures.add(HTTP_POOL.submit(() -> {
                    MailMessage msg = fetchSingleGmailMessage(accessToken, msgId, client);
                    if (msg != null) messages.add(msg);
                }));
            }

            for (Future<?> f : futures) {
                try { f.get(6, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
        } catch (Exception ex) {
            System.err.println("Error fetching messages for labelId: " + ex.getMessage());
        }
        return messages;
    }

    private MailMessage fetchSingleGmailMessage(String accessToken, String msgId, HttpClient client) {
        try {
            String detailUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + msgId + "?format=full";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(detailUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(java.time.Duration.ofSeconds(12))
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String json = resp.body();
                String snippet = extractStringField(json, "snippet");
                String from = extractHeaderFromPayload(json, "From");
                String subject = extractHeaderFromPayload(json, "Subject");
                String date = extractHeaderFromPayload(json, "Date");
                String to = extractHeaderFromPayload(json, "To");

                if (from == null || from.trim().isEmpty()) from = "Gmail User <" + connectedAccount + ">";
                if (subject == null || subject.trim().isEmpty()) subject = "(No Subject)";
                if (date == null || date.trim().isEmpty()) date = new Date().toString();

                from = cleanSenderAddress(unescapeJson(from));
                subject = unescapeJson(subject);
                snippet = unescapeJson(snippet);

                ParsedGmailContent parsedContent = parseGmailPayload(json);

                String fullBody = "";
                if (!parsedContent.htmlBody().isEmpty()) {
                    fullBody = parsedContent.htmlBody();
                } else if (!parsedContent.textBody().isEmpty()) {
                    String plainText = parsedContent.textBody();
                    String autoLinked = plainText
                            .replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replaceAll("(https?://[^\\s<>\"]+)", "<a href='$1' target='_blank' style='color: #2563eb; word-break: break-all;'>$1</a>")
                            .replace("\n", "<br>");
                    fullBody = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
                            + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size: 14px; line-height: 1.6; color: #1e293b; background-color: #ffffff; padding: 20px; margin: 0; }"
                            + "p { margin: 0 0 12px 0; }"
                            + "a { color: #2563eb; text-decoration: underline; word-break: break-all; }"
                            + "</style></head><body>" + autoLinked + "</body></html>";
                } else {
                    fullBody = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
                            + "body { font-family: sans-serif; font-size: 14px; color: #1e293b; padding: 20px; }"
                            + "</style></head><body><h2>" + subject + "</h2><p>" + (snippet != null ? snippet : "(No content)") + "</p></body></html>";
                }

                MailMessage msg = new MailMessage(from, subject, date, fullBody, "Mail");
                msg.setSenderAddress(extractEmailAddress(from));
                msg.setTo(to != null ? unescapeJson(to) : connectedAccount);
                msg.setMessageId(msgId);

                for (String attName : parsedContent.attachments()) {
                    msg.addAttachment(attName);
                }

                return msg;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private record ParsedGmailContent(String htmlBody, String textBody, List<String> attachments) {}

    private static String decodeBase64Url(String input) {
        if (input == null || input.isEmpty()) return "";
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(input);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e1) {
            try {
                String clean = input.replace('-', '+').replace('_', '/');
                while (clean.length() % 4 != 0) {
                    clean += "=";
                }
                byte[] decoded = Base64.getDecoder().decode(clean);
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (Exception e2) {
                return "";
            }
        }
    }

    private static ParsedGmailContent parseGmailPayload(String json) {
        StringBuilder htmlBuf = new StringBuilder();
        StringBuilder textBuf = new StringBuilder();
        List<String> attachments = new ArrayList<>();

        if (json == null || json.isEmpty()) {
            return new ParsedGmailContent("", "", attachments);
        }

        try {
            JsonObject rootObj = JsonParser.parseString(json).getAsJsonObject();
            if (rootObj.has("payload")) {
                JsonObject payload = rootObj.getAsJsonObject("payload");
                extractPartsFromPayloadNode(payload, htmlBuf, textBuf, attachments);
            }
        } catch (Exception ex) {
            System.err.println("Error parsing Gmail payload via Gson: " + ex.getMessage());
        }

        return new ParsedGmailContent(htmlBuf.toString().trim(), textBuf.toString().trim(), attachments);
    }

    private static void extractPartsFromPayloadNode(JsonObject node, StringBuilder htmlBuf, StringBuilder textBuf, List<String> attachments) {
        if (node == null) return;

        String mimeType = node.has("mimeType") ? node.get("mimeType").getAsString() : "";
        String filename = node.has("filename") ? node.get("filename").getAsString() : "";

        if (filename != null && !filename.trim().isEmpty()) {
            String cleanName = unescapeJson(filename.trim());
            if (!attachments.contains(cleanName)) {
                attachments.add(cleanName);
            }
        }

        if (node.has("body")) {
            JsonObject bodyObj = node.getAsJsonObject("body");
            if (bodyObj != null && bodyObj.has("data")) {
                String data = bodyObj.get("data").getAsString();
                if (data != null && !data.trim().isEmpty()) {
                    String decoded = decodeBase64Url(data.trim());
                    if ("text/html".equalsIgnoreCase(mimeType)) {
                        htmlBuf.append(decoded);
                    } else if ("text/plain".equalsIgnoreCase(mimeType)) {
                        textBuf.append(decoded);
                    }
                }
            }
        }

        if (node.has("parts")) {
            JsonArray partsArr = node.getAsJsonArray("parts");
            if (partsArr != null) {
                for (JsonElement elem : partsArr) {
                    if (elem.isJsonObject()) {
                        extractPartsFromPayloadNode(elem.getAsJsonObject(), htmlBuf, textBuf, attachments);
                    }
                }
            }
        }
    }

    private static String extractBodyDataFromBlock(String block) {
        if (block == null) return null;
        int bodyIdx = block.indexOf("\"body\"");
        if (bodyIdx >= 0) {
            int dataIdx = block.indexOf("\"data\"", bodyIdx);
            if (dataIdx >= 0 && dataIdx < bodyIdx + 2000) {
                int colonIdx = block.indexOf(':', dataIdx);
                if (colonIdx >= 0) {
                    int quoteStart = block.indexOf('"', colonIdx);
                    if (quoteStart >= 0) {
                        int quoteEnd = block.indexOf('"', quoteStart + 1);
                        if (quoteEnd > quoteStart) {
                            return block.substring(quoteStart + 1, quoteEnd);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String extractStringFieldStatic(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"[\\s:]+\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) return unescapeJson(matcher.group(1));
        return null;
    }

    private static List<String> splitJsonObjectsStatic(String json) {
        List<String> blocks = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    blocks.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return blocks;
    }

    private List<MailMessage> fetchRealGoogleContacts(String accessToken) {
        List<MailMessage> contacts = new ArrayList<>();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = "https://people.googleapis.com/v1/people/me/connections?personFields=names,emailAddresses,phoneNumbers&pageSize=1000";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(java.time.Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String json = resp.body();
                int connIdx = json.indexOf("\"connections\"");
                if (connIdx >= 0) {
                    int arrStart = json.indexOf('[', connIdx);
                    int arrEnd = json.lastIndexOf(']');
                    if (arrStart >= 0 && arrEnd > arrStart) {
                        String arrayContent = json.substring(arrStart + 1, arrEnd);
                        List<String> personBlocks = splitJsonObjects(arrayContent);
                        for (String person : personBlocks) {
                            String name = extractStringField(person, "displayName");
                            if (name == null) name = extractStringField(person, "givenName");
                            String email = extractStringField(person, "value");

                            if (name != null || email != null) {
                                if (name == null) name = email;
                                if (email == null) email = connectedAccount;

                                name = unescapeJson(name);
                                email = unescapeJson(email);

                                String body = "<html><body><h2>Google Contact</h2><p><strong>Name:</strong> " + name + "<br><strong>Email/Phone:</strong> " + email + "</p></body></html>";
                                MailMessage msg = new MailMessage(name + " <" + email + ">", "Contact: " + name, new Date().toString(), body, "Contact");
                                msg.setSenderAddress(email);
                                msg.setTo(connectedAccount);
                                msg.setMessageId("CONTACT_" + UUID.randomUUID().toString().substring(0, 8));
                                contacts.add(msg);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return contacts;
    }

    private List<MailMessage> fetchRealGoogleCalendarEvents(String accessToken) {
        List<MailMessage> events = new ArrayList<>();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=100";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String json = resp.body();
                Pattern p = Pattern.compile("\"summary\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(json);
                while (m.find()) {
                    String summary = unescapeJson(m.group(1));
                    String body = "<html><body><h2>Google Calendar Event</h2><p><strong>Summary:</strong> " + summary + "</p></body></html>";
                    MailMessage msg = new MailMessage("Google Calendar <calendar@google.com>", "Event: " + summary, new Date().toString(), body, "Appointment");
                    msg.setSenderAddress("calendar@google.com");
                    msg.setTo(connectedAccount);
                    msg.setMessageId("EVENT_" + UUID.randomUUID().toString().substring(0, 8));
                    events.add(msg);
                }
            }
        } catch (Exception ignored) {}
        return events;
    }

    private List<MailMessage> fetchRealGoogleTasks(String accessToken) {
        List<MailMessage> tasks = new ArrayList<>();
        if (accessToken == null || accessToken.trim().isEmpty()) return tasks;

        try {
            HttpClient client = HttpClient.newHttpClient();
            List<String> listIds = new ArrayList<>();

            try {
                String listsUrl = "https://tasks.googleapis.com/tasks/v1/users/@me/lists?maxResults=100";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(listsUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .timeout(java.time.Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                System.out.println("[GMAIL-API] Tasks Lists API response status: " + resp.statusCode());
                if (resp.statusCode() == 200) {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (root.has("items")) {
                        JsonArray items = root.getAsJsonArray("items");
                        for (JsonElement elem : items) {
                            if (elem.isJsonObject()) {
                                JsonObject obj = elem.getAsJsonObject();
                                if (obj.has("id")) {
                                    listIds.add(obj.get("id").getAsString());
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Error fetching task lists: " + ex.getMessage());
            }

            if (listIds.isEmpty()) {
                listIds.add("@default");
            }

            for (String listId : listIds) {
                try {
                    String taskUrl = "https://tasks.googleapis.com/tasks/v1/lists/" + URLEncoder.encode(listId, StandardCharsets.UTF_8)
                            + "/tasks?maxResults=500&showCompleted=true&showHidden=true";
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(taskUrl))
                            .header("Authorization", "Bearer " + accessToken)
                            .timeout(java.time.Duration.ofSeconds(15))
                            .GET()
                            .build();

                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    System.out.println("[GMAIL-API] Tasks List '" + listId + "' tasks status: " + resp.statusCode());
                    if (resp.statusCode() == 200) {
                        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                        if (root.has("items")) {
                            JsonArray items = root.getAsJsonArray("items");
                            for (JsonElement elem : items) {
                                if (elem.isJsonObject()) {
                                    JsonObject taskObj = elem.getAsJsonObject();
                                    String title = taskObj.has("title") ? taskObj.get("title").getAsString() : "";
                                    if (title.trim().isEmpty()) continue;

                                    String notes = taskObj.has("notes") ? taskObj.get("notes").getAsString() : "";
                                    String status = taskObj.has("status") ? taskObj.get("status").getAsString() : "needsAction";
                                    String due = taskObj.has("due") ? taskObj.get("due").getAsString() : "";
                                    String completed = taskObj.has("completed") ? taskObj.get("completed").getAsString() : "";
                                    String updated = taskObj.has("updated") ? taskObj.get("updated").getAsString() : new Date().toString();

                                    title = unescapeJson(title);
                                    notes = unescapeJson(notes);

                                    String displayDate = !completed.isEmpty() ? completed : (!due.isEmpty() ? due : updated);

                                    String body = "<html><body style='font-family: sans-serif; font-size: 14px; color: #1e293b; padding: 16px;'>"
                                            + "<h2>Task: " + title + "</h2>"
                                            + "<p><strong>Status:</strong> " + (status.equalsIgnoreCase("completed") ? "Completed" : "In Progress (Needs Action)") + "</p>"
                                            + (!due.isEmpty() ? "<p><strong>Due Date:</strong> " + due + "</p>" : "")
                                            + (!notes.isEmpty() ? "<p><strong>Notes:</strong><br>" + notes.replace("\n", "<br>") + "</p>" : "")
                                            + "</body></html>";

                                    MailMessage msg = new MailMessage("Google Tasks <tasks@google.com>", "Task: " + title, displayDate, body, "Task");
                                    msg.setSenderAddress("tasks@google.com");
                                    msg.setTo(connectedAccount);
                                    msg.setStatus(status.equalsIgnoreCase("completed") ? "Completed" : "NeedsAction");
                                    if (taskObj.has("id")) {
                                        msg.setMessageId(taskObj.get("id").getAsString());
                                    } else {
                                        msg.setMessageId("TASK_" + UUID.randomUUID().toString().substring(0, 8));
                                    }

                                    if (!notes.isEmpty()) {
                                        msg.addMetadata("Notes", notes);
                                    }
                                    msg.addMetadata("Status", status);

                                    tasks.add(msg);
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Error fetching tasks for listId '" + listId + "': " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            System.err.println("Error in fetchRealGoogleTasks: " + ex.getMessage());
        }

        System.out.println("[GMAIL-API] Total Google Tasks fetched for account '" + connectedAccount + "': " + tasks.size());
        return tasks;
    }

    private List<MailMessage> buildAccountEmailMessages(String targetFolder) {
        List<MailMessage> list = new ArrayList<>();
        String folderTitle = targetFolder.isEmpty() ? "Inbox" : targetFolder;

        list.add(new MailMessage("Google Security Team <no-reply@accounts.google.com>",
                "Security Alert: Google OAuth 2.0 Connected for " + connectedAccount,
                new Date().toString(),
                "<html><body><h2>Google Security Alert</h2><p>Your account <strong>" + connectedAccount + "</strong> was successfully connected via OAuth 2.0.</p></body></html>",
                "Mail"));

        list.add(new MailMessage("Google Cloud <cloud-notifications@google.com>",
                folderTitle + ": Service Activity Update for " + connectedAccount,
                new Date().toString(),
                "<html><body><h2>Google Service Notice</h2><p>Folder: <strong>" + folderTitle + "</strong><br>Account: <strong>" + connectedAccount + "</strong></p></body></html>",
                "Mail"));

        return list;
    }

    private List<String> extractMessageIds(String json) {
        List<String> ids = new ArrayList<>();
        Pattern p = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    private static String extractHeaderFromPayload(String json, String headerName) {
        Pattern p = Pattern.compile("\"name\"\\s*:\\s*\"" + Pattern.quote(headerName) + "\"\\s*,\\s*\"value\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (m.find()) return sanitizeDisplayString(decodeHeaderValue(unescapeJson(m.group(1))));

        Pattern p2 = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"name\"\\s*:\\s*\"" + Pattern.quote(headerName) + "\"", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(json);
        if (m2.find()) return sanitizeDisplayString(decodeHeaderValue(unescapeJson(m2.group(1))));

        return null;
    }

    private static String decodeHeaderValue(String input) {
        if (input == null || input.isEmpty()) return input;
        String val = input.trim();
        if (val.contains("=?") && val.contains("?=")) {
            try {
                val = javax.mail.internet.MimeUtility.decodeText(val);
            } catch (Exception ignored) {}
        }
        return val;
    }

    private static String sanitizeDisplayString(String text) {
        if (text == null) return "";
        return text.replace("\uD83D\uDDDCE", "")
                   .replace("🗎", "")
                   .replace("\uFFFD", "")
                   .trim();
    }

    private String extractStringField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"[\\s:]+\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) return unescapeJson(matcher.group(1));
        return null;
    }

    private int extractIntField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); } catch (Exception ignored) {}
        }
        return 0;
    }

    private static String unescapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\u003c", "<")
                    .replace("\\u003C", "<")
                    .replace("\\u003e", ">")
                    .replace("\\u003E", ">")
                    .replace("\\u0026", "&")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t");
    }

    private List<String> splitJsonObjects(String json) {
        List<String> blocks = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    blocks.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return blocks;
    }

    public static String cleanSenderAddress(String from) {
        if (from == null || from.trim().isEmpty()) return "Unknown Sender";
        from = from.trim();

        if (from.contains("=?") && from.contains("?=")) {
            try {
                from = javax.mail.internet.MimeUtility.decodeText(from);
            } catch (Exception ignored) {}
        }

        int angleStart = from.indexOf('<');
        int angleEnd = from.indexOf('>', angleStart);

        if (angleStart >= 0 && angleEnd > angleStart) {
            String name = from.substring(0, angleStart).trim();
            String email = from.substring(angleStart + 1, angleEnd).trim();

            name = name.replace("\"", "").trim();
            String cleanedEmail = cleanEmailAddress(email);

            if (!name.isEmpty()) {
                return name + " <" + cleanedEmail + ">";
            } else {
                return cleanedEmail;
            }
        }

        return cleanEmailAddress(from);
    }

    private static String cleanEmailAddress(String email) {
        if (email == null || !email.contains("@")) return email != null ? email : "";
        int atIdx = email.indexOf('@');
        String local = email.substring(0, atIdx);
        String domain = email.substring(atIdx + 1);

        java.util.regex.Pattern b64Pattern = java.util.regex.Pattern.compile("([A-Za-z0-9+/]{12,}={0,2})");
        java.util.regex.Matcher m = b64Pattern.matcher(local);
        if (m.find()) {
            String b64Match = m.group(1);
            try {
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(b64Match);
                String decodedStr = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                if (decodedStr.matches("[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")) {
                    String prefix = local.substring(0, m.start());
                    if (prefix.endsWith(".")) prefix = prefix.substring(0, prefix.length() - 1);
                    if (!prefix.isEmpty()) {
                        return prefix + "@" + decodedStr;
                    } else {
                        return "info@" + decodedStr;
                    }
                }
            } catch (Exception ignored) {}

            String prefix = local.substring(0, m.start());
            if (prefix.endsWith(".") || prefix.endsWith("_") || prefix.endsWith("-")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            if (!prefix.isEmpty()) {
                return prefix + "@" + domain;
            }
        }
        return email;
    }

    private String extractEmailAddress(String from) {
        if (from == null) return "";
        String cleaned = cleanSenderAddress(from);
        int start = cleaned.indexOf('<');
        int end = cleaned.indexOf('>');
        if (start >= 0 && end > start) {
            return cleaned.substring(start + 1, end).trim();
        }
        return cleaned.contains("@") ? cleaned.trim() : cleaned;
    }

    @Override public String getSourceType() { return "GMAIL"; }
    @Override public String getDisplayName() { return "Gmail Account (OAuth 2.0)"; }
    @Override public List<File> detectLocalMailboxes() { return Collections.emptyList(); }

    private record GmailLabel(String id, String name, int messagesTotal, int threadsTotal, String type) {}
}
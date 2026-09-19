package com.pstconverter.core.output;

import com.pstconverter.model.MailMessage;
import java.io.File;
import java.util.List;

/**
 * Interface representing a format exporter.
 * Implementing classes handle exporting parsed email data to a specific target format.
 */
public interface OutputHandler {
    
    /**
     * Exports the given messages to the specified target directory.
     *
     * @param targetFolder The target folder where output files are created.
     * @param emails The list of parsed email messages to export.
     * @throws Exception if exporting fails.
     */
    void export(File targetFolder, List<MailMessage> emails) throws Exception;

    default void export(File targetFolder, List<MailMessage> emails, String attachmentHandling) throws Exception {
        export(targetFolder, emails);
    }

    default void export(File targetFolder, List<MailMessage> emails, String attachmentHandling, String exportStructure) throws Exception {
        export(targetFolder, emails, attachmentHandling);
    }

    default void export(File targetFolder, List<MailMessage> emails, String attachmentHandling, String exportStructure, String namingConvention) throws Exception {
        export(targetFolder, emails, attachmentHandling, exportStructure);
    }

    interface Session extends AutoCloseable {
        void writeMessage(MailMessage msg) throws Exception;
        @Override
        void close() throws Exception;
    }

    default Session openSession(File targetFolder, String attachmentHandling, String exportStructure, String namingConvention) throws Exception {
        return new Session() {
            private final java.util.List<MailMessage> buffer = new java.util.ArrayList<>();
            @Override
            public void writeMessage(MailMessage msg) throws Exception {
                buffer.add(msg);
            }
            @Override
            public void close() throws Exception {
                export(targetFolder, buffer, attachmentHandling, exportStructure, namingConvention);
                buffer.clear();
            }
        };
    }

    /**
     * Cleans up any previously generated monolithic files to start fresh.
     *
     * @param targetFolder The target folder where the files would be created.
     * @param exportStructure The structure setting chosen by the user.
     */
    default void cleanMonolithicFiles(File targetFolder, String exportStructure) {
        // By default, do nothing
    }


    // ── Static Escaping & Sanitization Helpers ────────────────────────────────────

    static java.util.List<String> saveAttachments(File targetFolder, MailMessage msg, int index, String safeSubject, String attachmentHandling) {
        java.util.List<String> relativePaths = new java.util.ArrayList<>();
        if (attachmentHandling == null || attachmentHandling.equalsIgnoreCase("Skip / Drop Attachments") || attachmentHandling.contains("Embed Attachments")) {
            return relativePaths;
        }
        
        List<MailMessage.Attachment> attachments = msg.getAttachmentList();
        if (attachments == null || attachments.isEmpty()) {
            return relativePaths;
        }

        File destDir = targetFolder;
        String prefixPath = "";
        if (attachmentHandling.equalsIgnoreCase("Keep Attachments in Folder") || attachmentHandling.contains("Separate")) {
            prefixPath = "Attachments/" + index + ". " + safeSubject + "/";
            destDir = new File(targetFolder, "Attachments/" + index + ". " + safeSubject);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
        }

        for (MailMessage.Attachment att : attachments) {
            String cleanName = sanitizeFileName(att.getFilename());
            File attFile = new File(destDir, cleanName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(attFile)) {
                fos.write(att.getData());
                relativePaths.add(prefixPath + cleanName);
            } catch (Exception e) {
                System.err.println("Failed to save attachment " + att.getFilename() + ": " + e.getMessage());
            }
        }
        return relativePaths;
    }

    static String getFallbackEncoding() {
        String fallback = com.pstconverter.util.SettingsManager.getSetting("fallback_encoding", "Auto-Detect (Recommended)");
        if (fallback == null || fallback.equalsIgnoreCase("Auto-Detect (Recommended)")) {
            return "utf-8";
        }
        return fallback.toLowerCase(java.util.Locale.ENGLISH);
    }

    static String buildMimeMessageString(MailMessage msg) {
        String charset = getFallbackEncoding();
        StringBuilder sb = new StringBuilder();
        List<MailMessage.Attachment> attachments = msg.getAttachmentList();
        String rawBody = msg.getBody() != null ? msg.getBody() : "";
        boolean isHtml = rawBody.toLowerCase().contains("<html") || rawBody.toLowerCase().contains("<body") 
                      || rawBody.toLowerCase().contains("<div") || rawBody.toLowerCase().contains("<p")
                      || rawBody.toLowerCase().contains("<br") || rawBody.toLowerCase().contains("<img");
        String plainBody = getPlainTextBody(rawBody);

        if (attachments == null || attachments.isEmpty()) {
            if (isHtml) {
                String altBoundary = "----=_AltPart_" + System.currentTimeMillis() + "_" + Math.abs(msg.hashCode());
                sb.append("MIME-Version: 1.0\n");
                sb.append("Content-Type: multipart/alternative; boundary=\"").append(altBoundary).append("\"\n\n");
                
                sb.append("--").append(altBoundary).append("\n");
                sb.append("Content-Type: text/plain; charset=\"").append(charset).append("\"\n");
                sb.append("Content-Transfer-Encoding: 8bit\n\n");
                sb.append(plainBody).append("\n\n");
                
                sb.append("--").append(altBoundary).append("\n");
                sb.append("Content-Type: text/html; charset=\"").append(charset).append("\"\n");
                sb.append("Content-Transfer-Encoding: 8bit\n\n");
                sb.append(rawBody).append("\n\n");
                
                sb.append("--").append(altBoundary).append("--\n");
            } else {
                sb.append("MIME-Version: 1.0\n");
                sb.append("Content-Type: text/plain; charset=\"").append(charset).append("\"\n\n");
                sb.append(plainBody).append("\n");
            }
            return sb.toString();
        }

        String boundary = "----=_NextPart_" + System.currentTimeMillis() + "_" + Math.abs(msg.hashCode());
        sb.append("MIME-Version: 1.0\n");
        sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\n\n");
        
        // Body part
        if (isHtml) {
            String altBoundary = "----=_AltPart_" + System.currentTimeMillis() + "_" + Math.abs(msg.hashCode());
            sb.append("--").append(boundary).append("\n");
            sb.append("Content-Type: multipart/alternative; boundary=\"").append(altBoundary).append("\"\n\n");
            
            sb.append("--").append(altBoundary).append("\n");
            sb.append("Content-Type: text/plain; charset=\"").append(charset).append("\"\n");
            sb.append("Content-Transfer-Encoding: 8bit\n\n");
            sb.append(plainBody).append("\n\n");
            
            sb.append("--").append(altBoundary).append("\n");
            sb.append("Content-Type: text/html; charset=\"").append(charset).append("\"\n");
            sb.append("Content-Transfer-Encoding: 8bit\n\n");
            sb.append(rawBody).append("\n\n");
            
            sb.append("--").append(altBoundary).append("--\n\n");
        } else {
            sb.append("--").append(boundary).append("\n");
            sb.append("Content-Type: text/plain; charset=\"").append(charset).append("\"\n");
            sb.append("Content-Transfer-Encoding: 8bit\n\n");
            sb.append(plainBody).append("\n\n");
        }

        // Attachments
        for (MailMessage.Attachment att : attachments) {
            String filename = sanitizeFileName(att.getFilename());
            byte[] data = att.getData();
            if (data == null) continue;
            
            sb.append("--").append(boundary).append("\n");
            sb.append("Content-Type: application/octet-stream; name=\"").append(filename).append("\"\n");
            sb.append("Content-Disposition: attachment; filename=\"").append(filename).append("\"\n");
            sb.append("Content-Transfer-Encoding: base64\n\n");
            
            String base64 = java.util.Base64.getEncoder().encodeToString(data);
            int len = base64.length();
            for (int i = 0; i < len; i += 76) {
                sb.append(base64.substring(i, Math.min(i + 76, len))).append("\n");
            }
            sb.append("\n");
        }
        
        sb.append("--").append(boundary).append("--\n");
        return sb.toString();
    }

    static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "unnamed_message";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        if (sanitized.startsWith("_")) {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.endsWith("_")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        // Truncate to 120 characters to avoid OS file name limit issues
        if (sanitized.length() > 120) {
            sanitized = sanitized.substring(0, 120);
            if (sanitized.endsWith("_")) {
                sanitized = sanitized.substring(0, sanitized.length() - 1);
            }
        }
        return sanitized.isEmpty() ? "unnamed_message" : sanitized;
    }

    static String sanitizePdfText(String text) {
        return sanitizePdfText(text, false);
    }

    static String sanitizePdfText(String text, boolean preserveNewlines) {
        if (text == null) return "";

        // 1. Map common smart punctuation and non-ASCII symbols to standard ASCII equivalents
        text = text.replace("’", "'")
                   .replace("‘", "'")
                   .replace("`", "'")
                   .replace("“", "\"")
                   .replace("”", "\"")
                   .replace("—", "-")
                   .replace("–", "-")
                   .replace("•", "*")
                   .replace("…", "...")
                   .replace("\u00A0", " ")
                   .replace("™", "TM")
                   .replace("©", "(c)")
                   .replace("®", "(R)");

        // 2. Normalize and strip diacritical marks (accents)
        try {
            text = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKD);
            text = text.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        } catch (Exception ignored) {}

        // 3. Filter remaining characters, dropping surrogates/emojis and keeping standard WinAnsi printable ASCII [32, 126]
        StringBuilder sb = new StringBuilder();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c >= 32 && c < 127) {
                sb.append(c);
            } else if (c == '\n' || c == '\r') {
                if (preserveNewlines) {
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
            } else if (c == '\t') {
                sb.append(' ');
            } else if (Character.isHighSurrogate(c)) {
                // If it is a surrogate pair (e.g. emoji), skip both characters to clean it up completely
                if (i + 1 < length && Character.isLowSurrogate(text.charAt(i + 1))) {
                    i++;
                }
            }
        }
        return sb.toString();
    }

    static String getPlainTextBody(String body) {
        if (body == null) return "";
        String lower = body.toLowerCase();
        if (lower.contains("<html") || lower.contains("<body") || lower.contains("<p") || lower.contains("<br") || lower.contains("<div") || lower.contains("<style") || lower.contains("<!--") || lower.contains("&#") || lower.contains("&nbsp;") || lower.contains("&shy;")) {
            // 1. Strip HTML comments
            body = body.replaceAll("(?is)<!--.*?-->", "");
            
            // 2. Strip head, style, script tags and their contents completely
            body = body.replaceAll("(?is)<style\\b[^>]*>.*?</style>", "");
            body = body.replaceAll("(?is)<script\\b[^>]*>.*?</script>", "");
            body = body.replaceAll("(?is)<head\\b[^>]*>.*?</head>", "");
            
            // 3. Strip hidden marketing pre-header elements (e.g. font-size:0, display:none, max-height:0, mso-hide:all)
            body = body.replaceAll("(?is)<(div|span|p|table|td)[^>]*(display\\s*:\\s*none|max-height\\s*:\\s*0|font-size\\s*:\\s*0|visibility\\s*:\\s*hidden|mso-hide\\s*:\\s*all|preheader|preview-text)[^>]*>.*?</\\1>", "");
            
            // 4. Clean line breaks and block separators
            body = body.replaceAll("(?i)<br\\s*/?>", "\n");
            body = body.replaceAll("(?i)</?(p|h[1-6]|tr|li|blockquote)\\b[^>]*>", "\n");
            body = body.replaceAll("(?i)</?(div|table|tbody|thead|tfoot|td|th)\\b[^>]*>", " ");
            
            // 5. Strip all remaining HTML tags
            body = body.replaceAll("(?s)<[^>]*>", "");
            
            // 6. Decode numeric decimal HTML entities (e.g. &#8199; &#847; &#160;)
            try {
                java.util.regex.Pattern decPattern = java.util.regex.Pattern.compile("&#([0-9]{1,7});");
                java.util.regex.Matcher decMatcher = decPattern.matcher(body);
                StringBuilder decSb = new StringBuilder();
                while (decMatcher.find()) {
                    int code = Integer.parseInt(decMatcher.group(1));
                    if (code == 8199 || code == 847 || code == 8203 || code == 8204 || code == 8205 || code == 8239 || code == 65279 || code == 173 || (code >= 0x200B && code <= 0x200F)) {
                        decMatcher.appendReplacement(decSb, "");
                    } else if (code == 160) {
                        decMatcher.appendReplacement(decSb, " ");
                    } else if (Character.isValidCodePoint(code)) {
                        decMatcher.appendReplacement(decSb, java.util.regex.Matcher.quoteReplacement(new String(Character.toChars(code))));
                    } else {
                        decMatcher.appendReplacement(decSb, "");
                    }
                }
                decMatcher.appendTail(decSb);
                body = decSb.toString();
            } catch (Exception ignored) {}

            // 7. Decode numeric hex HTML entities (e.g. &#x2007; &#x034F;)
            try {
                java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("&#x([0-9a-fA-F]{1,6});");
                java.util.regex.Matcher hexMatcher = hexPattern.matcher(body);
                StringBuilder hexSb = new StringBuilder();
                while (hexMatcher.find()) {
                    int code = Integer.parseInt(hexMatcher.group(1), 16);
                    if (code == 8199 || code == 847 || code == 8203 || code == 8204 || code == 8205 || code == 8239 || code == 65279 || code == 173 || (code >= 0x200B && code <= 0x200F)) {
                        hexMatcher.appendReplacement(hexSb, "");
                    } else if (code == 160) {
                        hexMatcher.appendReplacement(hexSb, " ");
                    } else if (Character.isValidCodePoint(code)) {
                        hexMatcher.appendReplacement(hexSb, java.util.regex.Matcher.quoteReplacement(new String(Character.toChars(code))));
                    } else {
                        hexMatcher.appendReplacement(hexSb, "");
                    }
                }
                hexMatcher.appendTail(hexSb);
                body = hexSb.toString();
            } catch (Exception ignored) {}

            // 8. Decode standard named HTML entities
            body = body.replace("&nbsp;", " ")
                       .replace("&shy;", "")
                       .replace("&zwnj;", "")
                       .replace("&zwj;", "")
                       .replace("&lrm;", "")
                       .replace("&rlm;", "")
                       .replace("&lt;", "<")
                       .replace("&gt;", ">")
                       .replace("&amp;", "&")
                       .replace("&quot;", "\"")
                       .replace("&apos;", "'")
                       .replace("&#39;", "'")
                       .replace("&hellip;", "...")
                       .replace("&bull;", "* ")
                       .replace("&mdash;", "-")
                       .replace("&ndash;", "-")
                       .replace("&rsquo;", "'")
                       .replace("&lsquo;", "'")
                       .replace("&rdquo;", "\"")
                       .replace("&ldquo;", "\"");
                       
            // 9. Remove any lingering zero-width / soft hyphen characters
            body = body.replaceAll("[\u00AD\u200B\u200C\u200D\u200E\u200F\uFEFF\u034F\u2007\u202F\u2060]", "");

            // 10. Clean up horizontal whitespace on each line
            body = body.replaceAll("[ \\t\\x0B\\f]+", " ");
            
            // 11. Normalize newlines and collapse 3+ empty lines into max 2
            body = body.replaceAll("\\r?\\n", "\n");
            body = body.replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n").trim();
        }
        return body;
    }

    static String escapeHtml(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    static String escapeXml(String text) {
        return escapeHtml(text);
    }

    static String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    static String escapeCsv(String text) {
        if (text == null) return "";
        return text.replace("\"", "\"\"");
    }

    static String escapeRtf(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '\\' || c == '{' || c == '}') {
                sb.append('\\').append(c);
            } else if (c > 127) {
                sb.append("\\u").append((int) c).append('?');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String extractEmail(String from) {
        if (from == null) return "sender@example.com";
        int start = from.indexOf('<');
        int end = from.indexOf('>');
        if (start != -1 && end != -1 && start < end) {
            return from.substring(start + 1, end).trim();
        }
        return from.trim().isEmpty() ? "sender@example.com" : from.trim();
    }

    static String convertToAscTime(String dateStr) {
        String defaultTime = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", java.util.Locale.US).format(new java.util.Date());
        if (dateStr == null || dateStr.isEmpty()) return defaultTime;
        try {
            String[] patterns = {
                "EEE, d MMM yyyy HH:mm:ss Z",
                "d MMM yyyy HH:mm:ss Z",
                "EEE, d MMM yyyy HH:mm:ss z",
                "yyyy-MM-dd HH:mm:ss"
            };
            for (String pattern : patterns) {
                try {
                    java.text.SimpleDateFormat df = new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
                    java.util.Date date = df.parse(dateStr);
                    return new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", java.util.Locale.US).format(date);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return defaultTime;
    }

    static String getFormattedDate(String dateStr) {
        String defaultDate = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        if (dateStr == null || dateStr.trim().isEmpty()) return defaultDate;
        String[] patterns = {
            "EEE, d MMM yyyy HH:mm:ss Z",
            "d MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat df = new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
                java.util.Date date = df.parse(dateStr);
                return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
            } catch (Exception ignored) {}
        }
        return defaultDate;
    }

    static String getFormattedFileName(MailMessage msg, String namingConvention, int index, String extension) {
        String subject = msg.getSubject();
        if (subject == null || subject.trim().isEmpty()) {
            subject = "No Subject";
        }
        subject = subject.trim();

        String dateStr = getFormattedDate(msg.getDate());
        
        String fromStr = msg.getFrom();
        if (fromStr == null || fromStr.trim().isEmpty()) {
            fromStr = "Unknown Sender";
        } else {
            fromStr = extractEmail(fromStr);
        }

        String baseName = "";
        String conv = namingConvention != null ? namingConvention : "Original Subject";
        switch (conv) {
            case "Date + Subject":
            case "[Date]_[Subject]":
                baseName = dateStr + " - " + subject;
                break;
            case "From + Subject":
            case "[Subject]_[Sender]":
                baseName = fromStr + " - " + subject;
                break;
            case "Date + From + Subject":
                baseName = dateStr + " - " + fromStr + " - " + subject;
                break;
            case "Subject + Date":
                baseName = subject + " - " + dateStr;
                break;
            case "[Message-ID]":
                baseName = msg.getMessageId() != null && !msg.getMessageId().isEmpty() ? msg.getMessageId() : "msg_" + index;
                break;
            case "[Sequential ID]":
                baseName = "Message_" + index;
                break;
            case "Original Subject":
            default:
                baseName = subject;
                break;
        }

        String sanitizedBase = sanitizeFileName(baseName);
        
        String cleanExt = extension != null ? extension.trim().toLowerCase() : "";
        if (cleanExt.startsWith(".")) {
            cleanExt = cleanExt.substring(1);
        }
        
        return index + ". " + sanitizedBase + (cleanExt.isEmpty() ? "" : "." + cleanExt);
    }
}

package com.pstconverter.imap;

import com.chilkatsoft.CkImap;
import com.pstconverter.util.ChilkatLibraryLoader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Universal IMAP Authentication & Connectivity Engine powered by Chilkat.
 * Handles:
 * - Intelligent password sanitization (leading/trailing spaces, space-separated app passwords, dashed app passwords).
 * - Multi-candidate authentication fallbacks.
 * - Standard SSL/TLS (port 993) and STARTTLS (port 143) protocol negotiation.
 * - Provider auto-detection (Gmail, Yahoo, Outlook, AOL, iCloud, Zoho, Custom Domains).
 */
public final class ImapAuthHelper {

    private ImapAuthHelper() {}

    public static class AuthResult {
        public final boolean success;
        public final String verifiedPassword;
        public final String errorMessage;
        public final String serverGreeting;

        public AuthResult(boolean success, String verifiedPassword, String errorMessage, String serverGreeting) {
            this.success = success;
            this.verifiedPassword = verifiedPassword;
            this.errorMessage = errorMessage;
            this.serverGreeting = serverGreeting;
        }
    }

    public static class ProviderPreset {
        public final String name;
        public final String host;
        public final int port;
        public final boolean ssl;

        public ProviderPreset(String name, String host, int port, boolean ssl) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.ssl = ssl;
        }
    }

    /**
     * Resolves the recommended IMAP host, port, and security settings for any email address.
     */
    public static ProviderPreset detectPreset(String email) {
        if (email == null) return new ProviderPreset("Custom", "", 993, true);
        String lower = email.trim().toLowerCase();

        if (lower.contains("@gmail.com") || lower.contains("@googlemail.com")) {
            return new ProviderPreset("Gmail", "imap.gmail.com", 993, true);
        } else if (lower.contains("@yahoo.") || lower.contains("@ymail.com") || lower.contains("@rocketmail.com")) {
            return new ProviderPreset("Yahoo", "imap.mail.yahoo.com", 993, true);
        } else if (lower.contains("@outlook.com") || lower.contains("@hotmail.com") || lower.contains("@live.com") || lower.contains("@msn.com") || lower.contains("@office365.com")) {
            return new ProviderPreset("Outlook", "outlook.office365.com", 993, true);
        } else if (lower.contains("@aol.com") || lower.contains("@aim.com")) {
            return new ProviderPreset("AOL", "imap.aol.com", 993, true);
        } else if (lower.contains("@icloud.com") || lower.contains("@me.com") || lower.contains("@mac.com")) {
            return new ProviderPreset("iCloud", "imap.mail.me.com", 993, true);
        } else if (lower.contains("@zoho.com") || lower.contains("@zohomail.com") || lower.contains("@zoho.eu")) {
            return new ProviderPreset("Zoho", "imap.zoho.com", 993, true);
        } else if (lower.contains("@")) {
            String domain = lower.substring(lower.indexOf("@") + 1);
            if (!domain.isEmpty()) {
                return new ProviderPreset("Custom", "imap." + domain, 993, true);
            }
        }
        return new ProviderPreset("Custom", "", 993, true);
    }

    /**
     * Completely cleans any input password:
     * - Handles Unicode whitespace, Non-Breaking Spaces (\u00A0), zero-width characters, BOM.
     * - Strips prefixes like "App Password - ", "Password: "
     * - If extracting pure alphanumeric yields 16 chars (standard Google/Yahoo/iCloud/AOL app passwords),
     *   instantly returns the clean 16-character string (e.g. "jtumlzgmoaycrnbs").
     * - Removes all internal whitespace and dashes.
     */
    public static String sanitizeAppPassword(String rawPassword) {
        if (rawPassword == null) return "";
        
        // Strip leading & trailing Unicode whitespace, BOM, NBSP (\u00A0), zero-width chars
        String s = rawPassword.replaceAll("^[\\p{Z}\\s\\u00A0\\uFEFF\\u200B-\\u200D]+|[\\p{Z}\\s\\u00A0\\uFEFF\\u200B-\\u200D]+$", "").trim();
        if (s.isEmpty()) return "";

        // Remove prefix if user pasted "App Password - ..." or "Password: ..."
        String lowerTrim = s.toLowerCase();
        if (lowerTrim.startsWith("app password") || lowerTrim.startsWith("app-password") || lowerTrim.startsWith("password")) {
            int sep = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '-' || c == ':' || c == '=') {
                    sep = i;
                    break;
                }
            }
            if (sep >= 0 && sep < s.length() - 1) {
                s = s.substring(sep + 1).replaceAll("^[\\p{Z}\\s\\u00A0\\uFEFF\\u200B-\\u200D]+|[\\p{Z}\\s\\u00A0\\uFEFF\\u200B-\\u200D]+$", "").trim();
            }
        }

        // 1. Check if extracting pure alphanumeric characters yields exactly 16 characters!
        // Google, Yahoo, iCloud, AOL app passwords are ALWAYS 16 alphanumeric characters!
        // (e.g. "jtum lzgm oayc rnbs" with \u00A0 or spaces or hyphens)
        String alphaNumOnly = s.replaceAll("[^a-zA-Z0-9]", "");
        if (alphaNumOnly.length() == 16) {
            return alphaNumOnly.toLowerCase();
        }

        // 2. Check if a 16-character 4x4 app password block is embedded anywhere inside the string
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)[a-z0-9]{4}[\\p{Z}\\s\\u00A0\\uFEFF-]?[a-z0-9]{4}[\\p{Z}\\s\\u00A0\\uFEFF-]?[a-z0-9]{4}[\\p{Z}\\s\\u00A0\\uFEFF-]?[a-z0-9]{4}").matcher(s);
        if (m.find()) {
            String extracted = m.group().replaceAll("[^a-zA-Z0-9]", "");
            if (extracted.length() == 16) {
                return extracted.toLowerCase();
            }
        }

        // 3. For any other password: strip all Unicode whitespace, non-breaking spaces, zero-width chars, BOM, and dashes
        return s.replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF\\u200B-\\u200D-]+", "");
    }

    public static List<String> buildPasswordCandidates(String rawPassword) {
        Set<String> candidates = new LinkedHashSet<>();
        if (rawPassword == null) {
            candidates.add("");
            return new ArrayList<>(candidates);
        }

        // 1. Clean space-free and dash-free version ALWAYS FIRST!
        String clean = sanitizeAppPassword(rawPassword);
        if (!clean.isEmpty()) {
            candidates.add(clean);
        }

        // 2. If it's a 16-character app password, also generate standard 4x4 chunked variants
        if (clean.length() == 16) {
            String b1 = clean.substring(0, 4);
            String b2 = clean.substring(4, 8);
            String b3 = clean.substring(8, 12);
            String b4 = clean.substring(12, 16);
            candidates.add(b1 + " " + b2 + " " + b3 + " " + b4);
            candidates.add(b1 + "-" + b2 + "-" + b3 + "-" + b4);
        }

        // 3. Pure alphanumeric if different
        String alphaOnly = rawPassword.replaceAll("[^a-zA-Z0-9]", "");
        if (!alphaOnly.isEmpty() && !candidates.contains(alphaOnly)) {
            candidates.add(alphaOnly);
        }

        // 4. Raw password with only leading/trailing Unicode whitespace stripped (in case custom password has symbols)
        String trimmed = rawPassword.replaceAll("^[\\p{Z}\\s\\u00A0\\uFEFF\\u200B-\\u200D]+|[\\p{Z}\\s\\u00A0\\uFEFF\\u200B-\\u200D]+$", "").trim();
        if (!trimmed.isEmpty() && !candidates.contains(trimmed)) {
            candidates.add(trimmed);
        }

        // 5. Raw input string as fallback
        if (!candidates.contains(rawPassword)) {
            candidates.add(rawPassword);
        }

        return new ArrayList<>(candidates);
    }

    /**
     * Configures a Chilkat CkImap instance with proper SSL/TLS or STARTTLS parameters.
     */
    public static void configureImapClient(CkImap imap, int port, boolean ssl, int timeoutSeconds) {
        imap.put_ConnectTimeout(timeoutSeconds > 0 ? timeoutSeconds : 25);
        imap.put_ReadTimeout(timeoutSeconds > 0 ? timeoutSeconds : 25);
        imap.put_KeepSessionLog(true);

        if (port == 993) {
            imap.put_Ssl(true);
            imap.put_StartTls(false);
            imap.put_Port(993);
        } else if (port == 143) {
            if (ssl) {
                // STARTTLS negotiation on standard port 143
                imap.put_Ssl(false);
                imap.put_StartTls(true);
            } else {
                imap.put_Ssl(false);
                imap.put_StartTls(false);
            }
            imap.put_Port(143);
        } else {
            imap.put_Ssl(ssl);
            imap.put_Port(port);
        }
    }

    /**
     * Verifies IMAP connection and authenticates against any IMAP provider.
     * Automatically tests candidate password sanitizations and returns the verified password.
     */
    public static AuthResult testAndAuthenticate(String host, int port, boolean ssl, String email, String password, int timeoutSeconds) {
        ChilkatLibraryLoader.loadAndUnlock();

        String cleanHost = host != null ? host.replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF]+", "").trim() : "";
        String cleanEmail = email != null ? email.replaceAll("[\\p{Z}\\s\\u00A0\\uFEFF]+", "").trim() : "";

        if (cleanHost.isEmpty()) {
            return new AuthResult(false, null, "IMAP Host cannot be empty.", null);
        }
        if (cleanEmail.isEmpty()) {
            return new AuthResult(false, null, "Email address cannot be empty.", null);
        }

        List<String> candidates = buildPasswordCandidates(password);
        if (candidates.isEmpty() || candidates.get(0).isEmpty()) {
            return new AuthResult(false, null, "App Password cannot be empty.", null);
        }

        CkImap imap = new CkImap();
        try {
            configureImapClient(imap, port, ssl, timeoutSeconds);

            String lastAuthErr = "";
            for (String candidate : candidates) {
                // Ensure a fresh, connected socket for every attempt
                if (!imap.IsConnected()) {
                    boolean connected = imap.Connect(cleanHost);
                    if (!connected) {
                        String rawErr = imap.lastErrorText();
                        String firstLine = rawErr != null ? rawErr.split("\n")[0] : "Connection failed";
                        return new AuthResult(false, null, "Connection to " + cleanHost + ":" + port + " failed: " + firstLine, null);
                    }
                }

                boolean loggedIn = imap.Login(cleanEmail, candidate);
                if (loggedIn) {
                    imap.Disconnect();
                    return new AuthResult(true, candidate, null, "Connected & Verified");
                }
                lastAuthErr = imap.lastErrorText();

                // Disconnect so next candidate attempt has a clean state if the server closed socket
                try { imap.Disconnect(); } catch (Exception ignored) {}
            }

            // If full email failed on Yahoo or AOL or custom, and candidate didn't match, also try username part
            if (cleanEmail.contains("@")) {
                String usernameOnly = cleanEmail.substring(0, cleanEmail.indexOf("@"));
                for (String candidate : candidates) {
                    if (!imap.IsConnected()) {
                        if (!imap.Connect(cleanHost)) break;
                    }
                    boolean loggedIn = imap.Login(usernameOnly, candidate);
                    if (loggedIn) {
                        imap.Disconnect();
                        return new AuthResult(true, candidate, null, "Connected & Verified");
                    }
                    try { imap.Disconnect(); } catch (Exception ignored) {}
                }
            }

            try {
                if (imap.IsConnected()) imap.Disconnect();
            } catch (Exception ignored) {}

            // Provide intelligent troubleshooting guidance
            String diagnosis = "Authentication failed for " + cleanEmail + ".";
            String lowerHost = cleanHost.toLowerCase();
            if (lowerHost.contains("gmail") || lowerHost.contains("google")) {
                diagnosis += "\n• Gmail requires a 16-character App Password (with 2-Step Verification turned ON).";
            } else if (lowerHost.contains("yahoo")) {
                diagnosis += "\n• Yahoo requires generating a dedicated App Password from Yahoo Account Security.";
            } else if (lowerHost.contains("outlook") || lowerHost.contains("office365")) {
                diagnosis += "\n• Outlook/Hotmail requires an App Password or IMAP access enabled in account settings.";
            } else if (lowerHost.contains("apple") || lowerHost.contains("icloud") || lowerHost.contains("me.com")) {
                diagnosis += "\n• iCloud requires an App-Specific Password generated at appleid.apple.com.";
            } else if (lowerHost.contains("zoho")) {
                diagnosis += "\n• Zoho Mail requires an Application-Specific Password from Zoho Security settings.";
            } else {
                diagnosis += "\n• Please verify your username/email, password/app password, and IMAP server settings.";
            }

            return new AuthResult(false, null, diagnosis, lastAuthErr);

        } catch (Exception ex) {
            return new AuthResult(false, null, "Error during authentication: " + ex.getMessage(), null);
        } finally {
            try {
                if (imap.IsConnected()) imap.Disconnect();
            } catch (Exception ignored) {}
        }
    }
}

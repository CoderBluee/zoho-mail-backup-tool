package com.pstconverter.core.auth;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to execute the Google OAuth 2.0 authentication flow and manage tokens.
 * Hosts a temporary loopback HTTP server to automatically capture redirect codes.
 */
public class GoogleOAuthService {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GMAIL_SCOPE = "https://mail.google.com/ https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/contacts https://www.googleapis.com/auth/contacts.readonly https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/tasks https://www.googleapis.com/auth/tasks.readonly https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile email profile";
    public static final java.util.List<String> GSUITE_ALL_SCOPES = java.util.Arrays.asList(
        "https://mail.google.com/",
        "https://www.googleapis.com/auth/admin.directory.user.readonly",
        "https://www.googleapis.com/auth/contacts",
        "https://www.googleapis.com/auth/calendar",
        "https://www.googleapis.com/auth/tasks"
    );

    /**
     * Executes authorization flow: launches browser, runs loopback server, and waits for auth code.
     */
    public static String acquireAuthorizationCode(String clientId, String redirectUri) throws Exception {
        int port = 8888; // Default redirect port
        try {
            URI uri = new URI(redirectUri);
            if (uri.getPort() != -1) {
                port = uri.getPort();
            }
        } catch (Exception ignored) {}

        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/Callback", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String query = exchange.getRequestURI().getQuery();
                String code = null;
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length > 1 && "code".equals(pair[0])) {
                            code = pair[1];
                            break;
                        }
                    }
                }

                String responseHtml;
                if (code != null) {
                    codeFuture.complete(code);
                    responseHtml = "<html><body style='font-family:sans-serif;text-align:center;padding-top:50px;'>"
                            + "<h2 style='color:#10b981;'>Authentication Successful!</h2>"
                            + "<p>You can now close this browser window and return to " + com.pstconverter.config.BrandConfig.TOOL_NAME + ".</p>"
                            + "</body></html>";
                } else {
                    responseHtml = "<html><body style='font-family:sans-serif;text-align:center;padding-top:50px;'>"
                            + "<h2 style='color:#f87171;'>Authentication Failed</h2>"
                            + "<p>Could not extract authorization code from callback parameters.</p>"
                            + "</body></html>";
                }

                byte[] bytes = responseHtml.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        server.start();
        System.out.println("OAuth Loopback Server started on port " + port + ". Waiting for code...");

        // Launch Browser
        String authReqUrl = AUTH_URL 
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode(GMAIL_SCOPE, StandardCharsets.UTF_8)
                + "&access_type=offline"
                + "&prompt=consent";

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(authReqUrl));
        } else {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", authReqUrl).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", authReqUrl).start();
            } else {
                new ProcessBuilder("xdg-open", authReqUrl).start();
            }
        }

        try {
            // Wait for redirect to happen with 2 minute timeout
            return codeFuture.get(120, TimeUnit.SECONDS);
        } finally {
            server.stop(1);
            System.out.println("OAuth Loopback Server stopped.");
        }
    }

    /**
     * Exchanges auth code for Access Token and Refresh Token.
     */
    public static OAuthTokenResult exchangeCode(String code, String clientId, String clientSecret, String redirectUri) throws Exception {
        String requestBody = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to exchange Google OAuth code. Status: " + response.statusCode() + ", Response: " + response.body());
        }

        String json = response.body();
        String accessToken = extractJsonField(json, "access_token");
        String refreshToken = extractJsonField(json, "refresh_token");
        int expiresIn = extractJsonIntField(json, "expires_in");
        long expiryTimeMs = System.currentTimeMillis() + (expiresIn * 1000L);

        return new OAuthTokenResult(accessToken, refreshToken, expiryTimeMs);
    }

    /**
     * Refreshes access token using refresh token.
     */
    public static OAuthTokenResult refreshTokens(String refreshToken, String clientId, String clientSecret) throws Exception {
        String requestBody = "refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&grant_type=refresh_token";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to refresh Google OAuth access token. Status: " + response.statusCode() + ", Response: " + response.body());
        }

        String json = response.body();
        String accessToken = extractJsonField(json, "access_token");
        int expiresIn = extractJsonIntField(json, "expires_in");
        long expiryTimeMs = System.currentTimeMillis() + (expiresIn * 1000L);

        // Google token refresh usually does not return a new refresh token, so pass the old one
        return new OAuthTokenResult(accessToken, refreshToken, expiryTimeMs);
    }

    /**
     * Creates GoogleCredential for GSuite Service Account supporting both JSON key files and P12 key files.
     */
    public static com.google.api.client.googleapis.auth.oauth2.GoogleCredential createServiceAccountCredential(String keyConfig, String targetUser, java.util.Collection<String> scopes) throws Exception {
        if (keyConfig == null || keyConfig.trim().isEmpty()) {
            throw new java.io.IOException("GSuite Service Account key configuration is required.");
        }

        if (keyConfig.startsWith("P12|")) {
            String[] parts = keyConfig.split("\\|", 3);
            if (parts.length < 3) {
                throw new java.io.IOException("Invalid P12 Service Account configuration format.");
            }
            String saEmail = parts[1].trim();
            String p12Path = parts[2].trim();
            java.io.File p12File = new java.io.File(p12Path);
            if (!p12File.exists()) {
                throw new java.io.FileNotFoundException("P12 Service Account key file not found: " + p12Path);
            }
            return new com.google.api.client.googleapis.auth.oauth2.GoogleCredential.Builder()
                .setTransport(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(com.google.api.client.json.gson.GsonFactory.getDefaultInstance())
                .setServiceAccountId(saEmail)
                .setServiceAccountPrivateKeyFromP12File(p12File)
                .setServiceAccountScopes(scopes)
                .setServiceAccountUser(targetUser)
                .build();
        } else {
            String jsonPath = keyConfig.startsWith("JSON|") ? keyConfig.substring(5).trim() : keyConfig.trim();
            java.io.File jsonFile = new java.io.File(jsonPath);
            if (!jsonFile.exists()) {
                throw new java.io.FileNotFoundException("Service Account JSON file not found: " + jsonPath);
            }
            return com.google.api.client.googleapis.auth.oauth2.GoogleCredential.fromStream(new java.io.FileInputStream(jsonFile))
                .createScoped(scopes)
                .createDelegated(targetUser);
        }
    }

    /**
     * Fetches all domain user emails from Google Workspace Directory API using Service Account Key (JSON or P12).
     */
    public static java.util.List<String> fetchGSuiteDomainUsers(String keyConfig, String adminEmail) throws Exception {
        java.util.List<String> userEmails = new java.util.ArrayList<>();
        if (keyConfig == null || keyConfig.trim().isEmpty() || adminEmail == null || adminEmail.trim().isEmpty()) {
            return userEmails;
        }

        java.util.List<String> scopes = java.util.Arrays.asList(
            "https://www.googleapis.com/auth/admin.directory.user.readonly",
            "https://mail.google.com/"
        );

        com.google.api.client.googleapis.auth.oauth2.GoogleCredential credential =
            createServiceAccountCredential(keyConfig, adminEmail, scopes);

        credential.refreshToken();
        String accessToken = credential.getAccessToken();

        if (accessToken == null || accessToken.isEmpty()) {
            throw new IOException("Failed to obtain access token for GSuite Directory API.");
        }

        String domain = "";
        if (adminEmail.contains("@")) {
            domain = adminEmail.substring(adminEmail.indexOf('@') + 1);
        }

        String apiUrl = "https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer&maxResults=500";
        if (!domain.isEmpty()) {
            apiUrl = "https://admin.googleapis.com/admin/directory/v1/users?domain=" + URLEncoder.encode(domain, StandardCharsets.UTF_8) + "&maxResults=500";
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(java.time.Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            String json = response.body();
            Pattern pattern = Pattern.compile("\"primaryEmail\"[\\s:]+\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            while (matcher.find()) {
                String email = matcher.group(1).trim();
                if (!email.isEmpty() && email.contains("@") && !userEmails.contains(email)) {
                    userEmails.add(email);
                }
            }
        } else if (response.statusCode() == 403 || response.statusCode() == 400) {
            String fallbackUrl = "https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer&maxResults=500";
            HttpRequest fallbackReq = HttpRequest.newBuilder()
                    .uri(URI.create(fallbackUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> fallbackResp = client.send(fallbackReq, HttpResponse.BodyHandlers.ofString());
            if (fallbackResp.statusCode() == 200) {
                String json = fallbackResp.body();
                Pattern pattern = Pattern.compile("\"primaryEmail\"[\\s:]+\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(json);
                while (matcher.find()) {
                    String email = matcher.group(1).trim();
                    if (!email.isEmpty() && email.contains("@") && !userEmails.contains(email)) {
                        userEmails.add(email);
                    }
                }
            } else {
                System.err.println("GSuite Directory API error: HTTP " + response.statusCode() + " | " + response.body());
            }
        }

        return userEmails;
    }

    private static String extractJsonField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"[\\s:]+\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static int extractJsonIntField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"[\\s:]+(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    public record OAuthTokenResult(String accessToken, String refreshToken, long expiryTimeMs) {}

        private static final java.util.Map<String, String> ACCESS_TOKENS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.io.File TOKEN_FILE = new java.io.File(System.getProperty("user.home"), ".gmail_tokens.properties");

    public static void storeToken(String email, String accessToken) {
        if (email != null && accessToken != null) {
            String cleanEmail = email.toLowerCase().trim();
            ACCESS_TOKENS.put(cleanEmail, accessToken);
            System.out.println("[TOKEN-STORE] Memory cached token for: " + cleanEmail);

            try {
                java.util.Properties props = new java.util.Properties();
                if (TOKEN_FILE.exists()) {
                    try (java.io.InputStream is = new java.io.FileInputStream(TOKEN_FILE)) {
                        props.load(is);
                    }
                }
                props.setProperty(cleanEmail, accessToken);
                try (java.io.OutputStream os = new java.io.FileOutputStream(TOKEN_FILE)) {
                    props.store(os, "Gmail Backup Tool OAuth Tokens");
                }
                System.out.println("[TOKEN-STORE] Persisted token to disk for: " + cleanEmail);
            } catch (Exception ex) {
                System.err.println("Failed to persist token to disk: " + ex.getMessage());
            }
        }
    }

    public static String getToken(String email) {
        if (email == null) return null;
        String cleanEmail = email.toLowerCase().trim();
        String t = ACCESS_TOKENS.get(cleanEmail);
        if (t == null && TOKEN_FILE.exists()) {
            try (java.io.InputStream is = new java.io.FileInputStream(TOKEN_FILE)) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                t = props.getProperty(cleanEmail);
                if (t != null) {
                    ACCESS_TOKENS.put(cleanEmail, t);
                    System.out.println("[TOKEN-GET] Loaded persisted token from disk for: " + cleanEmail);
                }
            } catch (Exception ignored) {}
        }
        if (t == null) {
            try {
                var accounts = com.pstconverter.util.SettingsManager.getSavedGoogleOAuthAccounts();
                var rec = accounts.stream()
                        .filter(r -> r.email().equalsIgnoreCase(cleanEmail))
                        .findFirst().orElse(null);
                if (rec != null) {
                    if (System.currentTimeMillis() + 60000 >= rec.tokenExpiry() && rec.refreshToken() != null && !rec.refreshToken().isEmpty()) {
                        ClientSecrets secrets = loadClientSecrets();
                        OAuthTokenResult refreshed = refreshTokens(rec.refreshToken(), secrets.clientId, secrets.clientSecret);
                        t = refreshed.accessToken();
                        com.pstconverter.util.SettingsManager.saveGoogleOAuthAccount(cleanEmail, rec.refreshToken(), t, refreshed.expiryTimeMs());
                        storeToken(cleanEmail, t);
                        System.out.println("[TOKEN-GET] Automatically refreshed expired OAuth token from DB for: " + cleanEmail);
                    } else {
                        t = rec.accessToken();
                        storeToken(cleanEmail, t);
                        System.out.println("[TOKEN-GET] Loaded valid OAuth token from DB for: " + cleanEmail);
                    }
                }
            } catch (Exception ex) {
                System.err.println("[TOKEN-GET] Error restoring token from DB for " + cleanEmail + ": " + ex.getMessage());
            }
        }
        System.out.println("[TOKEN-GET] Query token for: " + cleanEmail + " -> " + (t != null ? "FOUND" : "NULL"));
        return t;
    }

    /**
     * Fetches authenticated user's real email address using Google UserInfo API.
     */
    public static String fetchUserEmail(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) return null;
        
        String[] endpoints = {
            "https://www.googleapis.com/oauth2/v2/userinfo",
            "https://www.googleapis.com/oauth2/v3/userinfo",
            "https://openidconnect.googleapis.com/v1/userinfo"
        };

        HttpClient client = HttpClient.newHttpClient();
        for (String url : endpoints) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + accessToken)
                        .timeout(java.time.Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String json = response.body();
                    String email = extractJsonField(json, "email");
                    if (email != null && !email.trim().isEmpty() && email.contains("@")) {
                        return email.trim();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

        public static final String REMOTE_GOOGLE_SECRETS_URL = "https://download.prismmigration.com/Modern%20Auth%20JSONs/google_client_secrets.json";

    public static class ClientSecrets {
        public String clientId;
        public String clientSecret;
        public String redirectUri;
    }

    public static ClientSecrets loadClientSecrets() {
        ClientSecrets secrets = new ClientSecrets();
        String json = null;

        // 1. Fetch from remote R2 endpoint with 2.5s timeout
        try {
            java.net.URI uri = java.net.URI.create(REMOTE_GOOGLE_SECRETS_URL);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("User-Agent", "PrismMigration-Client/1.0");
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                try (java.io.InputStream in = conn.getInputStream()) {
                    json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    com.pstconverter.util.SettingsManager.saveSetting("cached_google_client_secrets", json);
                }
            }
        } catch (Exception ex) {
            System.err.println("[INFO] Could not fetch remote Google secrets, trying local cache: " + ex.getMessage());
        }

        // 2. Fallback to SQLite cache
        if (json == null || json.trim().isEmpty()) {
            json = com.pstconverter.util.SettingsManager.getSetting("cached_google_client_secrets", null);
        }

        // 3. Fallback to embedded classpath resource
        if (json == null || json.trim().isEmpty()) {
            try (var is = GoogleOAuthService.class.getResourceAsStream("/credentials/google_client_secrets.json")) {
                if (is != null) {
                    json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception ex) {
                System.err.println("Failed to load embedded google_client_secrets.json: " + ex.getMessage());
            }
        }

        if (json != null && !json.trim().isEmpty()) {
            secrets.clientId = extractJsonField(json, "client_id");
            secrets.clientSecret = extractJsonField(json, "client_secret");

            Pattern pattern = Pattern.compile("\"redirect_uris\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                secrets.redirectUri = matcher.group(1);
            }
        }

        if (secrets.redirectUri != null) {
            String rUri = secrets.redirectUri.trim();
            if (rUri.equals("http://localhost") || rUri.equals("http://127.0.0.1") ||
                rUri.equals("http://localhost/") || rUri.equals("http://127.0.0.1/")) {
                secrets.redirectUri = "http://localhost:8888/Callback";
            }
        }

        if (secrets.redirectUri == null || secrets.redirectUri.isEmpty()) {
            secrets.redirectUri = "http://localhost:8888/Callback";
        }

        return secrets;
    }
}

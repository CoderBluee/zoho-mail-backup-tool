package com.pstconverter.core.auth;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
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
 * Service to execute the Microsoft Azure AD / Graph API OAuth 2.0 authentication flow.
 * Hosts a temporary loopback HTTP server to automatically capture redirect codes.
 */
public class MicrosoftOAuthService {

    private static final String SCOPES = "https://graph.microsoft.com/Mail.ReadWrite User.Read offline_access";

    public static String acquireAuthorizationCode(String tenantId, String clientId, String redirectUri) throws Exception {
        return acquireAuthorizationCode(tenantId, clientId, redirectUri, false);
    }

    public static String acquireAuthorizationCode(String tenantId, String clientId, String redirectUri, boolean isAdminConsent) throws Exception {
        int port = 8888;
        try {
            URI uri = new URI(redirectUri);
            if (uri.getPort() != -1) {
                port = uri.getPort();
            }
        } catch (Exception ignored) {}

        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        HttpHandler handler = new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String query = exchange.getRequestURI().getQuery();
                String code = null;
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length > 1 && "code".equals(pair[0])) {
                            code = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                            break;
                        }
                    }
                }

                String responseHtml;
                if (code != null) {
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
                    os.flush();
                } catch (Exception ignored) {}

                if (code != null) {
                    codeFuture.complete(code);
                }
            }
        };

        server.createContext("/", handler);
        server.createContext("/Callback", handler);
        server.createContext("/callback", handler);

        server.start();
        System.out.println("Microsoft OAuth Loopback Server started on port " + port + ". Waiting for code...");

        // Launch Browser
        String effectiveTenant = (tenantId == null || tenantId.trim().isEmpty() || "common".equalsIgnoreCase(tenantId.trim())) 
                ? (isAdminConsent ? "organizations" : "common") 
                : tenantId.trim();

        String scopesToUse = isAdminConsent ? (SCOPES + " User.Read.All Mail.ReadWrite.Shared Directory.Read.All") : SCOPES;
        String authUrl = "https://login.microsoftonline.com/" + effectiveTenant + "/oauth2/v2.0/authorize"
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_mode=query"
                + "&scope=" + URLEncoder.encode(scopesToUse, StandardCharsets.UTF_8);
        if (isAdminConsent) {
            authUrl += "&prompt=consent";
        }

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(authUrl));
        } else {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", authUrl).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", authUrl).start();
            } else {
                new ProcessBuilder("xdg-open", authUrl).start();
            }
        }

        String code = codeFuture.get(5, TimeUnit.MINUTES);
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(1500); } catch (Exception ignored) {}
            try { server.stop(0); } catch (Exception ignored) {}
        });
        return code;
    }

    /**
     * Exchanges auth code for Access Token and Refresh Token.
     */
    public static OAuthTokenResult exchangeCode(String tenantId, String code, String clientId, String clientSecret, String redirectUri) throws Exception {
        String effectiveTenant = (tenantId == null || tenantId.trim().isEmpty()) ? "organizations" : tenantId.trim();
        String tokenUrl = "https://login.microsoftonline.com/" + effectiveTenant + "/oauth2/v2.0/token";
        String requestBody = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8)
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code";
        if (clientSecret != null && !clientSecret.trim().isEmpty() && !clientSecret.equalsIgnoreCase("dummy-client-secret")) {
            requestBody += "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to exchange Microsoft OAuth code. Status: " + response.statusCode() + ", Response: " + response.body());
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
    public static OAuthTokenResult refreshTokens(String tenantId, String refreshToken, String clientId, String clientSecret) throws Exception {
        String effectiveTenant = (tenantId == null || tenantId.trim().isEmpty()) ? "organizations" : tenantId.trim();
        String tokenUrl = "https://login.microsoftonline.com/" + effectiveTenant + "/oauth2/v2.0/token";
        String requestBody = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8)
                + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                + "&grant_type=refresh_token";
        if (clientSecret != null && !clientSecret.trim().isEmpty() && !clientSecret.equalsIgnoreCase("dummy-client-secret")) {
            requestBody += "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to refresh Microsoft OAuth access token. Status: " + response.statusCode() + ", Response: " + response.body());
        }

        String json = response.body();
        String accessToken = extractJsonField(json, "access_token");
        String newRefreshToken = extractJsonField(json, "refresh_token");
        int expiresIn = extractJsonIntField(json, "expires_in");
        long expiryTimeMs = System.currentTimeMillis() + (expiresIn * 1000L);

        return new OAuthTokenResult(accessToken, newRefreshToken != null ? newRefreshToken : refreshToken, expiryTimeMs);
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
            return Pattern.compile("\"" + fieldName + "\"[\\s:]+(\\d+)").matcher(json).find() ? Integer.parseInt(matcher.group(1)) : 0;
        }
        return 0;
    }

    /**
     * Acquires Access Token using Client Credentials Flow (Application Permissions for Admin Impersonation).
     */
    public static OAuthTokenResult acquireClientCredentialsToken(String tenantId, String clientId, String clientSecret) throws Exception {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        String requestBody = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode("https://graph.microsoft.com/.default", StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&grant_type=client_credentials";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to acquire App-Only token for Admin Impersonation. Status: " + response.statusCode() + ", Response: " + response.body());
        }

        String json = response.body();
        String accessToken = extractJsonField(json, "access_token");
        int expiresIn = extractJsonIntField(json, "expires_in");
        long expiryTimeMs = System.currentTimeMillis() + (expiresIn * 1000L);

        return new OAuthTokenResult(accessToken, "", expiryTimeMs);
    }

    /**
     * Fetches all domain user emails from Microsoft Graph API using Access Token.
     */
    public static java.util.List<String> fetchTenantUserEmails(String accessToken) throws Exception {
        java.util.List<String> userEmails = new java.util.ArrayList<>();
        String url = "https://graph.microsoft.com/v1.0/users?$select=mail,userPrincipalName,displayName&$top=999";
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(java.time.Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            String json = response.body();
            Pattern pMail = Pattern.compile("\"mail\"[\\s:]+\"([^\"]+)\"");
            Pattern pUpn = Pattern.compile("\"userPrincipalName\"[\\s:]+\"([^\"]+)\"");
            
            Matcher m1 = pMail.matcher(json);
            while (m1.find()) {
                String e = m1.group(1).trim();
                if (!e.isEmpty() && e.contains("@") && !userEmails.contains(e)) {
                    userEmails.add(e);
                }
            }
            
            Matcher m2 = pUpn.matcher(json);
            while (m2.find()) {
                String e = m2.group(1).trim();
                if (!e.isEmpty() && e.contains("@") && !userEmails.contains(e)) {
                    userEmails.add(e);
                }
            }
        } else {
            System.err.println("Failed to fetch tenant users: HTTP " + response.statusCode() + " | " + response.body());
        }
        return userEmails;
    }

    public record OAuthTokenResult(String accessToken, String refreshToken, long expiryTimeMs) {}

        public static final String REMOTE_MICROSOFT_SECRETS_URL = "https://download.prismmigration.com/Modern%20Auth%20JSONs/microsoft_client_secrets.json";

    public static class ClientSecrets {
        public String clientId;
        public String clientSecret;
        public String tenantId;
        public String redirectUri;
    }

    public static ClientSecrets loadClientSecrets() {
        ClientSecrets secrets = new ClientSecrets();
        String json = null;

        // 1. Fetch from remote R2 endpoint with 2.5s timeout
        try {
            java.net.URI uri = java.net.URI.create(REMOTE_MICROSOFT_SECRETS_URL);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("User-Agent", "PrismMigration-Client/1.0");
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                try (java.io.InputStream in = conn.getInputStream()) {
                    json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    com.pstconverter.util.SettingsManager.saveSetting("cached_microsoft_client_secrets", json);
                }
            }
        } catch (Exception ex) {
            System.err.println("[INFO] Could not fetch remote Microsoft secrets, trying local cache: " + ex.getMessage());
        }

        // 2. Fallback to SQLite cache
        if (json == null || json.trim().isEmpty()) {
            json = com.pstconverter.util.SettingsManager.getSetting("cached_microsoft_client_secrets", null);
        }

        // 3. Fallback to embedded classpath resource
        if (json == null || json.trim().isEmpty()) {
            try (var is = MicrosoftOAuthService.class.getResourceAsStream("/credentials/microsoft_client_secrets.json")) {
                if (is != null) {
                    json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception ex) {
                System.err.println("Failed to load embedded microsoft_client_secrets.json: " + ex.getMessage());
            }
        }

        if (json != null && !json.trim().isEmpty()) {
            secrets.clientId = extractJsonField(json, "client_id");
            secrets.clientSecret = extractJsonField(json, "client_secret");
            secrets.tenantId = extractJsonField(json, "tenant_id");
            secrets.redirectUri = extractJsonField(json, "redirect_uri");
        }

        if (secrets.redirectUri != null) {
            String rUri = secrets.redirectUri.trim();
            if (rUri.equals("http://localhost") || rUri.equals("http://127.0.0.1") ||
                rUri.equals("http://localhost/") || rUri.equals("http://127.0.0.1/")) {
                secrets.redirectUri = "http://localhost:8888/Callback";
            }
        }

        if (secrets.tenantId == null || secrets.tenantId.isEmpty()) {
            secrets.tenantId = "common";
        }
        if (secrets.redirectUri == null || secrets.redirectUri.isEmpty()) {
            secrets.redirectUri = "http://localhost:8888/Callback";
        }

        return secrets;
    }
}

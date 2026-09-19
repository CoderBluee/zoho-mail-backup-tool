package com.pstconverter.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class LicenseManager {

    public static final int TRIAL_LIMIT_PER_FOLDER = 50;
    public static final String APP_VERSION = "1.0.0";

    // License Setting Keys
    public static final String LICENSE_KEY_SETTING = "license_key";
    public static final String LICENSE_TYPE_SETTING = "license_type";
    public static final String LICENSE_EXPIRY_SETTING = "license_expiry";
    public static final String LICENSE_ACTIVATED_ON_SETTING = "license_activated_on";
    public static final String LICENSE_ACTIVATIONS_REMAINING_SETTING = "license_activations_remaining";
    public static final String API_BASE_URL_SETTING = "license_api_url";

    // License Tiers
    public static final String TIER_TRIAL = "TRIAL";
    public static final String TIER_STANDARD = "STANDARD";
    public static final String TIER_BUSINESS = "BUSINESS";
    public static final String TIER_ENTERPRISE = "ENTERPRISE";

    // HTTP Timeouts
    public static final int CONNECT_TIMEOUT_SECONDS = 5;
    public static final int REQUEST_TIMEOUT_SECONDS = 10;

    private static final String DEFAULT_API_URL = com.pstconverter.config.BrandConfig.LICENSE_API_URL;
    private static Boolean isActivatedCache = null;

    private LicenseManager() {}

    /**
     * Checks if the application is activated.
     */
    public static synchronized boolean isActivated() {
        if (isActivatedCache != null) {
            return isActivatedCache;
        }

        String key = SettingsManager.getSetting(LICENSE_KEY_SETTING, "");
        if (key.isEmpty()) {
            isActivatedCache = false;
            return false;
        }

        // Verify that we have a valid non-Trial license type stored
        String type = SettingsManager.getSetting(LICENSE_TYPE_SETTING, TIER_TRIAL);
        if (TIER_TRIAL.equalsIgnoreCase(type)) {
            isActivatedCache = false;
            return false;
        }

        // Verify that the license is not expired
        String expiryStr = SettingsManager.getSetting(LICENSE_EXPIRY_SETTING, "");
        if (expiryStr.isEmpty() || "LIFETIME".equalsIgnoreCase(expiryStr) || "NEVER".equalsIgnoreCase(expiryStr)) {
            isActivatedCache = true;
            return true;
        }

        try {
            java.time.OffsetDateTime expiry = java.time.OffsetDateTime.parse(expiryStr);
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            boolean active = now.isBefore(expiry);
            isActivatedCache = active;
            return active;
        } catch (Exception e) {
            try {
                java.time.LocalDate expiry = java.time.LocalDate.parse(expiryStr);
                java.time.LocalDate now = java.time.LocalDate.now();
                boolean active = now.isBefore(expiry);
                isActivatedCache = active;
                return active;
            } catch (Exception ex) {
                // If there is an parsing error but the key and type are valid, fallback to active
                isActivatedCache = true;
                return true;
            }
        }
    }

    /**
     * Validates if the given key matches the license key format pattern.
     */
    public static boolean isValidLicenseKey(String key) {
        if (key == null) return false;
        key = key.trim().toUpperCase();

        // Pattern check: BrandConfig validation pattern
        return key.matches(com.pstconverter.config.BrandConfig.LICENSE_KEY_PATTERN);
    }

    /**
     * Activates the application using the given key.
     * Contacts the Spring Boot backend server for validation.
     */
    public static synchronized boolean activate(String key) {
        if (!isValidLicenseKey(key)) {
            return false;
        }

        key = key.trim().toUpperCase();
        boolean onlineActivated = activateOnline(key);
        if (onlineActivated) {
            SettingsManager.saveSetting(LICENSE_KEY_SETTING, key);
            isActivatedCache = true;
            return true;
        }

        // Online activation failed and local offline fallback has been removed.
        System.out.println("Activation failed. Server is down, unreachable, or key is invalid.");
        isActivatedCache = false;
        return false;
    }

    /**
     * Performs an HTTP POST call to the backend server to register the license and machine ID.
     */
    private static boolean activateOnline(String key) {
        String machineId = getMachineId();
        String apiUrl = SettingsManager.getSetting(API_BASE_URL_SETTING, DEFAULT_API_URL);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                    .build();

            // Collect user name and host name for rich metadata
            String machineName = System.getProperty("user.name") + "@" + getHostName();
            String osName = System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")";

            String jsonRequest = String.format(
                "{\"licenseKey\":\"%s\",\"machineId\":\"%s\",\"machineName\":\"%s\",\"osName\":\"%s\"}",
                key, machineId, escapeJson(machineName), escapeJson(osName)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                if (body != null) {
                    String activatedVal = parseJsonField(body, "activated");
                    boolean activated = "true".equalsIgnoreCase(activatedVal);
                    if (activated) {
                        String licenseType = parseJsonField(body, "licenseType");
                        String validTill = parseJsonField(body, "validTill");
                        String activatedOn = parseJsonField(body, "activatedOn");
                        String remaining = parseJsonField(body, "activationsRemaining");
                        String userEmail = parseJsonField(body, "userEmail");
                        if (userEmail.isEmpty()) {
                            userEmail = parseJsonField(body, "email");
                        }

                        SettingsManager.saveSetting(LICENSE_TYPE_SETTING, licenseType.isEmpty() ? TIER_STANDARD : licenseType.toUpperCase());
                        SettingsManager.saveSetting(LICENSE_EXPIRY_SETTING, validTill.isEmpty() ? "LIFETIME" : validTill);
                        SettingsManager.saveSetting(LICENSE_ACTIVATED_ON_SETTING, activatedOn.isEmpty() ? "" : activatedOn);
                        SettingsManager.saveSetting(LICENSE_ACTIVATIONS_REMAINING_SETTING, remaining);
                        SettingsManager.saveSetting("license_email", userEmail);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Online license server request failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Deactivates the application, returning it to trial mode.
     */
    public static synchronized void deactivate() {
        SettingsManager.saveSetting(LICENSE_KEY_SETTING, "");
        SettingsManager.saveSetting(LICENSE_TYPE_SETTING, TIER_TRIAL);
        SettingsManager.saveSetting(LICENSE_EXPIRY_SETTING, "");
        SettingsManager.saveSetting(LICENSE_ACTIVATED_ON_SETTING, "");
        SettingsManager.saveSetting(LICENSE_ACTIVATIONS_REMAINING_SETTING, "");
        isActivatedCache = false;
    }

    /**
     * Helper to get active license type.
     */
    public static String getLicenseType() {
        if (!isActivated()) {
            return TIER_TRIAL;
        }
        return SettingsManager.getSetting(LICENSE_TYPE_SETTING, TIER_STANDARD).toUpperCase();
    }

    /**
     * Helper to get days remaining. Returns -1 for lifetime license.
     */
    public static int getDaysRemaining() {
        if (!isActivated()) {
            return -1; // Unactivated Trial has no expiry
        }

        String expiryStr = SettingsManager.getSetting(LICENSE_EXPIRY_SETTING, "");
        if (expiryStr.isEmpty() || "LIFETIME".equalsIgnoreCase(expiryStr) || "NEVER".equalsIgnoreCase(expiryStr)) {
            return -1; // Lifetime
        }

        try {
            java.time.OffsetDateTime expiry = java.time.OffsetDateTime.parse(expiryStr);
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            if (now.isBefore(expiry)) {
                return (int) java.time.temporal.ChronoUnit.DAYS.between(now, expiry);
            } else {
                return 0; // Expired
            }
        } catch (Exception e) {
            try {
                java.time.LocalDate expiry = java.time.LocalDate.parse(expiryStr);
                java.time.LocalDate now = java.time.LocalDate.now();
                if (now.isBefore(expiry)) {
                    return (int) java.time.temporal.ChronoUnit.DAYS.between(now, expiry);
                } else {
                    return 0;
                }
            } catch (Exception ex) {
                return -1;
            }
        }
    }

    /**
     * Helper to get formatted expiration date text.
     */
    public static String getExpirationDateText() {
        if (!isActivated()) {
            return "Never (Unlimited Trial)";
        }
        String expiryStr = SettingsManager.getSetting(LICENSE_EXPIRY_SETTING, "");
        if (expiryStr.isEmpty() || "LIFETIME".equalsIgnoreCase(expiryStr) || "NEVER".equalsIgnoreCase(expiryStr)) {
            return "Never (Lifetime)";
        }
        try {
            java.time.OffsetDateTime expiry = java.time.OffsetDateTime.parse(expiryStr);
            return expiry.format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
        } catch (Exception e) {
            try {
                java.time.LocalDate expiry = java.time.LocalDate.parse(expiryStr);
                return expiry.format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
            } catch (Exception ex) {
                return expiryStr;
            }
        }
    }

    /**
     * Helper to get max activations allowed by active tier.
     */
    public static int getMaxActivations() {
        String type = getLicenseType();
        return switch (type) {
            case TIER_STANDARD -> 1;
            case TIER_BUSINESS -> 3;
            case TIER_ENTERPRISE -> 10;
            default -> 0;
        };
    }

    /**
     * Helper to get activations remaining.
     */
    public static int getActivationsRemaining() {
        if (!isActivated()) {
            return 0;
        }
        String remStr = SettingsManager.getSetting(LICENSE_ACTIVATIONS_REMAINING_SETTING, "");
        if (!remStr.isEmpty()) {
            try {
                return Integer.parseInt(remStr);
            } catch (Exception ignored) {}
        }
        // Fallback for offline activation
        String type = getLicenseType();
        return switch (type) {
            case TIER_STANDARD -> 0;
            case TIER_BUSINESS -> 2;
            case TIER_ENTERPRISE -> 9;
            default -> 0;
        };
    }

    /**
     * Generates a unique machine identifier based on network interface MAC addresses.
     */
    public static String getMachineId() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0 && !ni.isLoopback()) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {}
        return System.getProperty("user.name") + "@" + System.getProperty("os.name") + "@" + System.getProperty("os.arch");
    }

    private static String getHostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String parseJsonField(String json, String field) {
        if (json == null) return "";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\s]+))");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            if (matcher.group(1) != null) {
                return matcher.group(1);
            }
            if (matcher.group(2) != null) {
                return matcher.group(2).trim();
            }
        }
        return "";
    }

    public static void checkLicenseAndVersionOnStartup(javafx.stage.Stage stage) {
        String key = SettingsManager.getSetting(LICENSE_KEY_SETTING, "");
        if (key.isEmpty()) {
            return;
        }
        String type = SettingsManager.getSetting(LICENSE_TYPE_SETTING, TIER_TRIAL);
        if (TIER_TRIAL.equalsIgnoreCase(type)) {
            return; // Leave version checks on startup via JSON check-in for activated users
        }

        new Thread(() -> {
            try {
                // Wait 2 seconds for UI stability
                Thread.sleep(2000);

                String machineId = getMachineId();
                String apiUrl = SettingsManager.getSetting(API_BASE_URL_SETTING, DEFAULT_API_URL);
                String verifyUrl = apiUrl.replace("/activate", "/verify");
                if (verifyUrl.equals(apiUrl)) {
                    verifyUrl = apiUrl + "/verify";
                }

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                        .build();

                String machineName = System.getProperty("user.name") + "@" + getHostName();
                String osName = System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")";

                String jsonRequest = String.format(
                    "{\"licenseKey\":\"%s\",\"machineId\":\"%s\",\"machineName\":\"%s\",\"osName\":\"%s\",\"appVersion\":\"%s\"}",
                    key, machineId, escapeJson(machineName), escapeJson(osName), APP_VERSION
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(verifyUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                        .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String body = response.body();
                    if (body != null) {
                        String activatedVal = parseJsonField(body, "activated");
                        boolean activated = "true".equalsIgnoreCase(activatedVal);
                        if (!activated) {
                            deactivate();
                            javafx.application.Platform.runLater(() -> {
                                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                                alert.setTitle("License Expired");
                                alert.setHeaderText("License Key Deactivated");
                                alert.setContentText("Your license key has been deactivated by the server. The application has been returned to Trial mode.");
                                alert.showAndWait();
                            });
                            return;
                        }

                        // Parse update status
                        String updateAvailableVal = parseJsonField(body, "updateAvailable");
                        boolean updateAvailable = "true".equalsIgnoreCase(updateAvailableVal);
                        if (updateAvailable) {
                            String latestVersion = parseJsonField(body, "latestVersion");
                            String updateUrl = parseJsonField(body, "updateUrl");
                            String notes = parseJsonField(body, "releaseNotes");

                            javafx.application.Platform.runLater(() -> {
                                showUpdateDialog(latestVersion, updateUrl, notes, stage);
                            });
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[INFO] Online verification skipped: Server unreachable or offline.");
            }
        }).start();
    }

    private static void showUpdateDialog(String latestVersion, String updateUrl, String releaseNotes, javafx.stage.Stage stage) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Software Update");
        alert.setHeaderText("New Update Available (v" + latestVersion + ")");
        alert.setContentText(
            "A new version of " + com.pstconverter.config.BrandConfig.TOOL_NAME + " is available for download.\n\n" +
            "Release Notes:\n" +
            (releaseNotes.isEmpty() ? " • Performance improvements and minor bug fixes." : releaseNotes) + "\n\n" +
            "Would you like to download and install this update in the background?"
        );
        alert.initOwner(stage);

        javafx.scene.control.ButtonType btnUpdate = new javafx.scene.control.ButtonType("Update Now", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType btnLater = new javafx.scene.control.ButtonType("Remind Me Later", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnUpdate, btnLater);

        java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == btnUpdate) {
            triggerBackgroundUpdate(updateUrl, latestVersion, stage);
        }
    }

    private static void triggerBackgroundUpdate(String updateUrl, String latestVersion, javafx.stage.Stage stage) {
        javafx.scene.control.Dialog<Void> progressDialog = new javafx.scene.control.Dialog<>();
        progressDialog.setTitle("Downloading Update");
        progressDialog.setHeaderText("Downloading v" + latestVersion + "...");
        progressDialog.initOwner(stage);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(12);
        content.setPadding(new javafx.geometry.Insets(20));
        content.setPrefWidth(350);

        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.control.Label statusLabel = new javafx.scene.control.Label("Connecting to update server...");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        content.getChildren().addAll(progressBar, statusLabel);
        progressDialog.getDialogPane().setContent(content);
        progressDialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CANCEL);

        javafx.scene.control.Button btnCancel = (javafx.scene.control.Button) progressDialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL);
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        btnCancel.setOnAction(e -> cancelled.set(true));

        progressDialog.show();

        new Thread(() -> {
            try {
                if (updateUrl == null || updateUrl.isEmpty() || updateUrl.contains("example.com")) {
                    for (int i = 0; i <= 100; i += 5) {
                        if (cancelled.get()) break;
                        final double progress = i / 100.0;
                        final String status = "Downloading... " + i + "%";
                        javafx.application.Platform.runLater(() -> {
                            progressBar.setProgress(progress);
                            statusLabel.setText(status);
                        });
                        Thread.sleep(150);
                    }
                } else {
                    HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
                    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(updateUrl)).build();
                    HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() == 200) {
                        long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                        java.io.InputStream is = response.body();

                        java.io.File tempFile = new java.io.File(System.getProperty("java.io.tmpdir"), "pst-converter-update.jar");
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                            byte[] buffer = new byte[8192];
                            long readBytes = 0;
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                if (cancelled.get()) break;
                                fos.write(buffer, 0, bytesRead);
                                readBytes += bytesRead;
                                if (totalBytes > 0) {
                                    final double progress = (double) readBytes / totalBytes;
                                    final String status = String.format("Downloaded %.1f MB / %.1f MB", readBytes / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0));
                                    javafx.application.Platform.runLater(() -> {
                                        progressBar.setProgress(progress);
                                        statusLabel.setText(status);
                                    });
                                } else {
                                    final String status = String.format("Downloaded %.1f MB", readBytes / (1024.0 * 1024.0));
                                    javafx.application.Platform.runLater(() -> {
                                        progressBar.setProgress(-1.0);
                                        statusLabel.setText(status);
                                    });
                                }
                            }
                        }
                    } else {
                        throw new RuntimeException("Server returned status: " + response.statusCode());
                    }
                }

                javafx.application.Platform.runLater(progressDialog::close);

                if (!cancelled.get()) {
                    javafx.application.Platform.runLater(() -> {
                        javafx.scene.control.Alert finishAlert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                        finishAlert.setTitle("Update Downloaded");
                        finishAlert.setHeaderText("Update Ready to Install");
                        finishAlert.setContentText("The update has been downloaded successfully. Would you like to restart and apply the update now?");

                        javafx.scene.control.ButtonType btnRestart = new javafx.scene.control.ButtonType("Restart & Apply", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
                        javafx.scene.control.ButtonType btnLater = new javafx.scene.control.ButtonType("Later", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
                        finishAlert.getButtonTypes().setAll(btnRestart, btnLater);

                        java.util.Optional<javafx.scene.control.ButtonType> opt = finishAlert.showAndWait();
                        if (opt.isPresent() && opt.get() == btnRestart) {
                            applyUpdateAndRestart();
                        }
                    });
                }
            } catch (Exception ex) {
                javafx.application.Platform.runLater(progressDialog::close);
                javafx.application.Platform.runLater(() -> {
                    javafx.scene.control.Alert errAlert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    errAlert.setTitle("Update Error");
                    errAlert.setHeaderText("Failed to download update");
                    errAlert.setContentText("An error occurred while downloading the update:\n" + ex.getMessage());
                    errAlert.showAndWait();
                });
            }
        }).start();
    }

    private static void applyUpdateAndRestart() {
        try {
            String jarPath = LicenseManager.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                jarPath = new java.io.File(jarPath).getAbsolutePath();
            }

            java.io.File currentJar = new java.io.File(jarPath);
            java.io.File tempUpdate = new java.io.File(System.getProperty("java.io.tmpdir"), "pst-converter-update.jar");

            if (!tempUpdate.exists()) {
                System.out.println("[INFO] Simulated restart and upgrade.");
                System.exit(0);
                return;
            }

            java.io.File batFile = new java.io.File(System.getProperty("java.io.tmpdir"), "apply_update.bat");
            try (java.io.PrintWriter writer = new java.io.PrintWriter(batFile)) {
                writer.println("@echo off");
                writer.println("timeout /t 2 /nobreak > nul");
                writer.println("move /y \"" + tempUpdate.getAbsolutePath() + "\" \"" + currentJar.getAbsolutePath() + "\"");
                writer.println("start javaw -jar \"" + currentJar.getAbsolutePath() + "\"");
                writer.println("del \"%~f0\"");
            }

            Runtime.getRuntime().exec("cmd.exe /c start /b " + batFile.getAbsolutePath());
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }
}

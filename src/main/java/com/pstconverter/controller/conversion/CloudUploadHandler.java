package com.pstconverter.controller.conversion;

import com.pstconverter.controller.ConversionUIContext;
import com.pstconverter.core.destination.DestinationAdapter;
import com.pstconverter.core.destination.DestinationAdapterFactory;
import com.pstconverter.core.destination.EmailDestinationConfig;
import com.pstconverter.model.MailMessage;
import com.pstconverter.util.DiagnosticLogger;

import javafx.application.Platform;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates all cloud-specific logic — adapter connection with retry,
 * per-message upload with backoff, internet connectivity monitoring,
 * and disconnect/restore handling.
 * <p>
 * Extracted from ConversionTaskManager to isolate network concerns
 * from the core conversion pipeline.
 */
public class CloudUploadHandler implements AutoCloseable {

    private final ConversionConfig config;
    private final ConversionUIContext ui;
    private final AtomicBoolean stopRequested;
    private final AtomicBoolean pauseRequested;
    private final AtomicBoolean internetDisconnected = new AtomicBoolean(false);
    private final AtomicBoolean internetDisconnectedUIUpdated = new AtomicBoolean(false);

    // Per-thread cloud adapters (each consumer thread gets its own connection)
    private final ConcurrentHashMap<String, DestinationAdapter> activeCloudAdapters = new ConcurrentHashMap<>();
    private final Set<String> foldersWithErrors;

    // Internet monitor
    private Thread internetMonitorThread;
    private final Object monitorLock = new Object();

    // UI controls (for pause button enable/disable during disconnect)
    private javafx.scene.control.Button btnPauseResume;
    private javafx.scene.control.Label lblStatus;

    public CloudUploadHandler(ConversionConfig config, ConversionUIContext ui,
                               AtomicBoolean stopRequested, AtomicBoolean pauseRequested,
                               Set<String> foldersWithErrors) {
        this.config = config;
        this.ui = ui;
        this.stopRequested = stopRequested;
        this.pauseRequested = pauseRequested;
        this.foldersWithErrors = foldersWithErrors;
    }

    /**
     * Injects optional UI controls for internet disconnect/restore handling.
     */
    public void setUIControls(javafx.scene.control.Button btnPauseResume, javafx.scene.control.Label lblStatus) {
        this.btnPauseResume = btnPauseResume;
        this.lblStatus = lblStatus;
    }

    // ── Cloud Adapter Connection ─────────────────────────────────────────────

    /**
     * Gets or creates a cloud adapter for the current thread. Handles connection
     * retries and internet disconnect/restore cycles.
     *
     * @return the connected adapter, or null if connection failed
     */
    public DestinationAdapter getOrConnectAdapter(String folderKey) {
        String threadKey = Thread.currentThread().getName();
        DestinationAdapter cloudAdapter = activeCloudAdapters.get(threadKey);
        EmailDestinationConfig cloudConfig = config.cloudConfig();

        if (cloudAdapter == null && cloudConfig != null && !foldersWithErrors.contains(folderKey)) {
            synchronized (activeCloudAdapters) {
                cloudAdapter = activeCloudAdapters.get(threadKey);
                if (cloudAdapter == null) {
                    while (!stopRequested.get()) {
                        if (internetDisconnected.get() || !isInternetAvailable()) {
                            if (!internetDisconnected.get()) {
                                internetDisconnected.set(true);
                                Platform.runLater(this::handleInternetDisconnected);
                            }
                            while (internetDisconnected.get() && !stopRequested.get()) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            if (stopRequested.get()) {
                                break;
                            }
                        }

                        try {
                            System.out.println("Connecting to cloud adapter for format " + config.format() + " using username: " + cloudConfig.username());
                            DestinationAdapter adapter = DestinationAdapterFactory.getAdapter(config.format());
                            adapter.connect(cloudConfig);
                            activeCloudAdapters.put(threadKey, adapter);
                            cloudAdapter = adapter;
                            break;
                        } catch (Exception ex) {
                            System.err.println("Cloud connection attempt failed: " + ex.getMessage());
                            if (!isInternetAvailable()) {
                                internetDisconnected.set(true);
                                Platform.runLater(this::handleInternetDisconnected);
                            } else {
                                foldersWithErrors.add(folderKey);
                                String folderName = folderKey.substring(folderKey.lastIndexOf('/') + 1);
                                ui.appendLog("  ⚠ Failed to connect to " + config.format() + " for " + folderName + ": " + ex.getMessage());
                                break;
                            }
                        }
                    }
                }
            }
        }

        return cloudAdapter;
    }

    // ── Message Upload ───────────────────────────────────────────────────────

    /**
     * Uploads a single message to the cloud destination with retry and internet resilience.
     *
     * @return true if upload succeeded, false otherwise
     */
    public boolean uploadMessage(MailMessage msg, String folderKey, String targetFolder, DestinationAdapter cloudAdapter) {
        return uploadMessage(msg, folderKey, targetFolder, cloudAdapter, null);
    }

    public boolean uploadMessage(MailMessage msg, String folderKey, String targetFolder, DestinationAdapter cloudAdapter, String overrideTargetUserEmail) {
        EmailDestinationConfig cloudConfig = config.cloudConfig();
        if (cloudAdapter == null || cloudConfig == null || foldersWithErrors.contains(folderKey)) {
            return false;
        }

        // Respect throttling delay and rate limit before uploading
        long rateLimitDelay = 0;
        if (cloudConfig.maxMessagesPerMin() > 0) {
            rateLimitDelay = 60000L / cloudConfig.maxMessagesPerMin();
        }
        long throttleDelay = cloudConfig.throttlingDelayMs();
        long delayMs = Math.max(throttleDelay, rateLimitDelay);
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        boolean uploaded = false;
        Exception lastEx = null;
        int maxUploadRetries = 1;
        int backoffDelayMs = 0;

        while (!uploaded && !stopRequested.get()) {
            if (internetDisconnected.get() || !isInternetAvailable()) {
                if (!internetDisconnected.get()) {
                    internetDisconnected.set(true);
                    Platform.runLater(this::handleInternetDisconnected);
                }
                while (internetDisconnected.get() && !stopRequested.get()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (stopRequested.get()) {
                    break;
                }
            }

            for (int attempt = 1; attempt <= maxUploadRetries && !stopRequested.get(); attempt++) {
                try {
                    synchronized (cloudAdapter) {
                        cloudAdapter.uploadMessage(msg, targetFolder, cloudConfig, overrideTargetUserEmail);
                    }
                    uploaded = true;
                    break;
                } catch (Exception ex) {
                    lastEx = ex;
                    System.err.println("Upload attempt " + attempt + " failed for Subject: " + msg.getSubject() + " | Error: " + ex.getMessage());
                    if (!isInternetAvailable()) {
                        internetDisconnected.set(true);
                        Platform.runLater(this::handleInternetDisconnected);
                        break;
                    }
                    if (attempt < maxUploadRetries) {
                        try {
                            Thread.sleep((long) backoffDelayMs * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            if (!uploaded && !internetDisconnected.get()) {
                break;
            }
        }

        if (!uploaded && !stopRequested.get()) {
            System.err.println("Message upload failed: " + msg.getSubject());
            ui.appendLog("  ⚠ Failed to upload message: " + (lastEx != null ? lastEx.getMessage() : "Unknown"));
        }

        return uploaded;
    }

    // ── Internet Connectivity ────────────────────────────────────────────────

    public boolean isInternetAvailable() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("8.8.8.8", 53), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isInternetDisconnected() {
        return internetDisconnected.get();
    }

    private void startInternetMonitor() {
        synchronized (monitorLock) {
            if (internetMonitorThread != null && internetMonitorThread.isAlive()) {
                return;
            }
            internetMonitorThread = new Thread(() -> {
                System.out.println("Internet monitor thread started.");
                while (internetDisconnected.get() && !stopRequested.get()) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (isInternetAvailable()) {
                        System.out.println("Internet monitor detected connectivity. Restoring...");
                        Platform.runLater(this::handleInternetRestored);
                        break;
                    }
                }
                System.out.println("Internet monitor thread stopped.");
            }, "internet-monitor-thread");
            internetMonitorThread.setDaemon(true);
            internetMonitorThread.start();
        }
    }

    private void handleInternetDisconnected() {
        if (!internetDisconnected.get()) {
            internetDisconnected.set(true);
        }
        if (internetDisconnectedUIUpdated.getAndSet(true)) {
            startInternetMonitor();
            return;
        }
        System.out.println("⚠️ Internet connection lost. Pausing migration...");
        ui.appendLog("⚠️ Internet connection lost. Pausing migration...");

        if (lblStatus != null) {
            lblStatus.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold;");
        }
        ui.setMasterStatus("⚠️ Internet connection lost. Pausing...");

        if (btnPauseResume != null) {
            btnPauseResume.setDisable(true);
        }

        startInternetMonitor();
    }

    private void handleInternetRestored() {
        if (!internetDisconnected.get()) return;
        System.out.println("▶ Internet connection restored. Resuming migration...");
        ui.appendLog("▶ Internet connection restored. Resuming migration...");
        internetDisconnected.set(false);
        internetDisconnectedUIUpdated.set(false);

        if (lblStatus != null) {
            lblStatus.setStyle("");
        }
        ui.setMasterStatus("Running...");

        if (btnPauseResume != null) {
            btnPauseResume.setDisable(false);
        }
    }

    // ── Pause Check (shared with local processing) ───────────────────────────

    /**
     * Blocks the calling thread while pause is requested or internet is disconnected.
     */
    public void checkPause() {
        while ((pauseRequested.get() || internetDisconnected.get()) && !stopRequested.get()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    /**
     * Disconnects all cloud adapters for all threads.
     */
    public void disconnectAll() {
        for (var entry : activeCloudAdapters.entrySet()) {
            try {
                entry.getValue().disconnect();
            } catch (Exception ignored) {}
        }
        activeCloudAdapters.clear();
    }

    /**
     * Disconnects the current thread's cloud adapter.
     */
    public void disconnectCurrentThread() {
        String threadKey = Thread.currentThread().getName();
        DestinationAdapter adapter = activeCloudAdapters.remove(threadKey);
        if (adapter != null) {
            try {
                adapter.disconnect();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Resolves the target folder path for cloud upload from the folder key.
     */
    public static String resolveTargetFolder(String key) {
        String[] parts = key.split("/");
        if (parts.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p < parts.length; p++) {
                if (sb.length() > 0) sb.append("/");
                sb.append(parts[p]);
            }
            return sb.toString();
        } else {
            return key.substring(key.lastIndexOf('/') + 1);
        }
    }

    @Override
    public void close() {
        disconnectAll();
    }
}

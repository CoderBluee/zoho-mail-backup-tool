package com.pstconverter.controller.conversion;

import com.pstconverter.controller.ConversionUIContext;
import com.pstconverter.core.destination.DestinationAdapter;
import com.pstconverter.core.filter.FilterEngine;
import com.pstconverter.model.MailMessage;
import com.pstconverter.util.DiagnosticLogger;
import com.pstconverter.util.LicenseManager;
import com.pstconverter.util.SettingsManager;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The core per-message processing Runnable. Takes a {@link WorkItem} from the
 * blocking queue, decides skip/export/cloud, records telemetry, and manages
 * folder lifecycle transitions (ACTIVE → DONE).
 * <p>
 * Extracted from ConversionTaskManager's inline consumer lambda.
 * Thread-safe: multiple instances run concurrently in the consumer executor pool.
 */
public class MessageConsumerWorker implements Runnable {

    // ── Shared state (injected, not owned) ──────────────────────────────────
    private final BlockingQueue<WorkItem> workQueue;
    private final ConversionConfig config;
    private final ConversionTelemetryTracker telemetry;
    private final ConversionSessionTracker sessions;
    private final CloudUploadHandler cloudHandler;  // null for local-only
    private final FilterEngine filterEngine;
    private final ConversionUIContext ui;
    private final AtomicBoolean stopRequested;
    private final AtomicBoolean pauseRequested;

    // ── Shared maps (constructor-injected references for zero-copy) ─────────
    private final Map<String, Integer> actualFolderItemCounts;
    private final Map<String, Integer> realFolderItemCounts;
    private final Map<String, File> folderToFileMap;
    private final Map<File, List<String>> fileFoldersMap;
    private final List<File> uniqueFiles;
    private final Set<String> activeFolders;
    private final Set<String> foldersWithErrors;
    private final Map<String, com.pstconverter.core.output.OutputHandler.Session> activeSessions;
    private final Map<String, Long> folderStartTimes;
    private final int totalFilesCount;

    // ── Volatile progress state shared with UI polling ──────────────────────
    private final AtomicReference<String> latestSubject;
    private final VolatileProgressState progressState;

    /**
     * Mutable progress state container shared between consumer workers and the
     * UI polling timer. Uses volatile fields for lock-free cross-thread visibility.
     */
    public static class VolatileProgressState {
        public volatile int currentIdx = 0;
        public volatile int estCount = 0;
        public volatile int folderIndexInCurrentFile = 0;
        public volatile int totalFoldersInCurrentFile = 1;
        public volatile int currentFileIndex = 1;
    }

    public MessageConsumerWorker(
            BlockingQueue<WorkItem> workQueue,
            ConversionConfig config,
            ConversionTelemetryTracker telemetry,
            ConversionSessionTracker sessions,
            CloudUploadHandler cloudHandler,
            FilterEngine filterEngine,
            ConversionUIContext ui,
            AtomicBoolean stopRequested,
            AtomicBoolean pauseRequested,
            Map<String, Integer> actualFolderItemCounts,
            Map<String, Integer> realFolderItemCounts,
            Map<String, File> folderToFileMap,
            Map<File, List<String>> fileFoldersMap,
            List<File> uniqueFiles,
            Set<String> activeFolders,
            Set<String> foldersWithErrors,
            Map<String, com.pstconverter.core.output.OutputHandler.Session> activeSessions,
            Map<String, Long> folderStartTimes,
            int totalFilesCount,
            AtomicReference<String> latestSubject,
            VolatileProgressState progressState
    ) {
        this.workQueue = workQueue;
        this.config = config;
        this.telemetry = telemetry;
        this.sessions = sessions;
        this.cloudHandler = cloudHandler;
        this.filterEngine = filterEngine;
        this.ui = ui;
        this.stopRequested = stopRequested;
        this.pauseRequested = pauseRequested;
        this.actualFolderItemCounts = actualFolderItemCounts;
        this.realFolderItemCounts = realFolderItemCounts;
        this.folderToFileMap = folderToFileMap;
        this.fileFoldersMap = fileFoldersMap;
        this.uniqueFiles = uniqueFiles;
        this.activeFolders = activeFolders;
        this.foldersWithErrors = foldersWithErrors;
        this.activeSessions = activeSessions;
        this.folderStartTimes = folderStartTimes;
        this.totalFilesCount = totalFilesCount;
        this.latestSubject = latestSubject;
        this.progressState = progressState;
    }

    @Override
    public void run() {
        try {
            while (true) {
                WorkItem item = workQueue.take();
                if (item.isPoisonPill()) {
                    break;
                }
                processWorkItem(item);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // Disconnect this thread's cloud adapter
            if (cloudHandler != null) {
                cloudHandler.disconnectCurrentThread();
            }
        }
    }

    private void processWorkItem(WorkItem item) {
        long msgStartTime = System.nanoTime();
        boolean currentItemSuccess = false;
        Exception lastException = null;

        String key = item.folderKey();
        MailMessage msg = item.message();
        String folderName = key.substring(key.lastIndexOf('/') + 1);

        // ── Stop-requested fast path ─────────────────────────────────────────
        if (stopRequested.get()) {
            int currentItemIndex = telemetry.incrementFolderProcessed(key);
            int folderTotal = actualFolderItemCounts.getOrDefault(key, 0);
            if (currentItemIndex == folderTotal) {
                closeSession(key);
                activeFolders.remove(key);
                updateFolderStatuses(key, "PENDING");
            }
            return;
        }

        // ── Pause check ──────────────────────────────────────────────────────
        if (cloudHandler != null) {
            cloudHandler.checkPause();
        } else {
            checkPauseLocal();
        }

        // ── Skip evaluation ──────────────────────────────────────────────────
        boolean isAlreadyMigrated = false;
        if (config.isResume()) {
            isAlreadyMigrated = sessions.isMigrated(msg.getUniqueIdentifier());
        }

        boolean skipCorruptSetting = "true".equals(SettingsManager.getSetting("skip_corrupt_items", "true"));
        boolean isCorrupt = msg.getMetadata() != null && msg.getMetadata().get("Error-Reason") != null;
        boolean isSkip = isAlreadyMigrated || (isCorrupt && skipCorruptSetting) || !filterEngine.test(msg);
        boolean isTrialLimit = false;
        if (!isSkip && !LicenseManager.isActivated()) {
            int successInFolder = telemetry.getSuccessInFolder(key);
            if (successInFolder >= LicenseManager.TRIAL_LIMIT_PER_FOLDER) {
                isSkip = true;
                isTrialLimit = true;
            }
        }

        // ── Record skip telemetry ────────────────────────────────────────────
        if (isSkip) {
            telemetry.incrementSkipped(key);
            double durationMs = (System.nanoTime() - msgStartTime) / 1_000_000.0;
            if (isAlreadyMigrated) {
                telemetry.trackSkipReason("Already migrated");
                DiagnosticLogger.logResumeSkip(msg.getUniqueIdentifier(), msg.getSubject(), key, durationMs);
            } else if (isTrialLimit) {
                telemetry.trackSkipReason("Trial Limit Reached (" + LicenseManager.TRIAL_LIMIT_PER_FOLDER + " items/folder)");
                DiagnosticLogger.logTrialLimitSkip(msg.getUniqueIdentifier(), msg.getSubject(), key, durationMs);
            } else if (isCorrupt && skipCorruptSetting) {
                String corruptReason = msg.getMetadata() != null ? msg.getMetadata().get("Error-Reason") : "Unknown Error";
                telemetry.trackSkipReason("Corrupt Item: " + corruptReason);
                DiagnosticLogger.logMessageSkipped(msg.getUniqueIdentifier(), msg.getSubject(), key, durationMs, "CORRUPT ITEM — " + corruptReason);
            } else {
                String reason = filterEngine.getRejectionReason(msg);
                telemetry.trackSkipReason(reason);
                DiagnosticLogger.logFilterReject(msg.getUniqueIdentifier(), msg.getSubject(), key, durationMs, reason);
            }
        }

        // ── Resolve file/folder indices ──────────────────────────────────────
        File currentFile = folderToFileMap.get(key);
        int currentFileIndex = resolveFileIndex(currentFile);
        List<String> foldersInCurrentFile = fileFoldersMap.get(currentFile);
        int totalFoldersInCurrentFile = foldersInCurrentFile != null ? foldersInCurrentFile.size() : 1;
        int folderIndexInCurrentFile = foldersInCurrentFile != null ? foldersInCurrentFile.indexOf(key) : 0;
        int folderTotal = actualFolderItemCounts.getOrDefault(key, 0);

        // ── Mark folder ACTIVE ───────────────────────────────────────────────
        if (!activeFolders.contains(key)) {
            activeFolders.add(key);
            updateFolderStatuses(key, "ACTIVE");
            progressState.currentIdx = 0;
            progressState.estCount = folderTotal;
            latestSubject.set("Starting folder migration...");
            progressState.folderIndexInCurrentFile = folderIndexInCurrentFile;
            progressState.totalFoldersInCurrentFile = totalFoldersInCurrentFile;
            progressState.currentFileIndex = currentFileIndex;
            folderStartTimes.putIfAbsent(key, System.currentTimeMillis());
            DiagnosticLogger.logFolderStart(key, folderTotal, currentFileIndex, totalFilesCount);
        }

        // ── Export (if not skipped) ──────────────────────────────────────────
        if (!isSkip) {
            if (config.isCloud()) {
                currentItemSuccess = processCloudMessage(msg, key, folderName);
            } else {
                // Local output session
                var result = processLocalMessage(msg, key);
                currentItemSuccess = result.success;
                lastException = result.exception;
            }

            if (currentItemSuccess) {
                telemetry.incrementSuccess(key);
                double durationMs = (System.nanoTime() - msgStartTime) / 1_000_000.0;
                DiagnosticLogger.logMessageSuccess(msg.getUniqueIdentifier(), msg.getSubject(), msg.getFrom(), msg.getDate(), key, durationMs);
                if (currentFile != null) {
                    sessions.recordMigration(
                        currentFile.getAbsolutePath(),
                        config.format(),
                        config.resolvedOutputPath() != null ? config.resolvedOutputPath() : "",
                        key,
                        msg.getUniqueIdentifier()
                    );
                }
            } else {
                telemetry.incrementFailed(key);
                double durationMs = (System.nanoTime() - msgStartTime) / 1_000_000.0;
                String errorReason = lastException != null ? lastException.getMessage() : "Export failed";
                DiagnosticLogger.logMessageFailed(msg.getUniqueIdentifier(), msg.getSubject(), key, durationMs, errorReason, lastException);
                File sessionDir = null;
                try {
                    // Access session dir through controller would break decoupling;
                    // instead we use the session CSV logger on the tracker
                    sessions.logFailedEmail(msg, "Export failed", null);
                } catch (Exception ignored) {}
            }
        }

        // ── Update counters and progress ─────────────────────────────────────
        telemetry.incrementProcessed();
        telemetry.addOutputBytes((msg.getBody() != null ? msg.getBody().getBytes().length : 0) + 1024);

        int currentItemIndex = telemetry.incrementFolderProcessed(key);

        progressState.currentIdx = currentItemIndex;
        progressState.estCount = folderTotal;
        latestSubject.set(isSkip ? "Skipped: " + (msg.getSubject() != null ? msg.getSubject() : "No Subject") : msg.getSubject());
        progressState.folderIndexInCurrentFile = folderIndexInCurrentFile;
        progressState.totalFoldersInCurrentFile = totalFoldersInCurrentFile;
        progressState.currentFileIndex = currentFileIndex;

        // ── Folder completion ────────────────────────────────────────────────
        if (currentItemIndex == folderTotal) {
            closeSession(key);
            activeFolders.remove(key);
            telemetry.incrementFoldersDone();

            boolean isError = foldersWithErrors.contains(key);
            String finalStatus = isError ? "ERROR" : (stopRequested.get() ? "PENDING" : "DONE");

            telemetry.recordFolderComplete(key, currentItemIndex, finalStatus);
            updateFolderStatuses(key, finalStatus);

            if (isError) {
                ui.appendLog("  ⚠ " + folderName + " completed with errors.");
            } else {
                ui.appendLog("  ✅ " + folderName + " — done");
            }

            progressState.currentIdx = currentItemIndex;
            progressState.estCount = folderTotal;
            latestSubject.set("");
            progressState.folderIndexInCurrentFile = folderIndexInCurrentFile;
            progressState.totalFoldersInCurrentFile = totalFoldersInCurrentFile;
            progressState.currentFileIndex = currentFileIndex;
        }
    }

    // ── Cloud Message Processing ─────────────────────────────────────────────

    private boolean processCloudMessage(MailMessage msg, String key, String folderName) {
        if (cloudHandler == null) return false;

        DestinationAdapter adapter = cloudHandler.getOrConnectAdapter(key);
        if (adapter == null || foldersWithErrors.contains(key)) {
            return false;
        }

        String overrideTargetUserEmail = null;
        File sourceFile = folderToFileMap.get(key);
        if (sourceFile != null && config.cloudConfig() != null && config.cloudConfig().userMappingMap() != null) {
            Map<String, String> map = config.cloudConfig().userMappingMap();
            overrideTargetUserEmail = map.get(sourceFile.getAbsolutePath());
            if (overrideTargetUserEmail == null) {
                overrideTargetUserEmail = map.get(sourceFile.getName());
            }
        }

        String targetFolder = CloudUploadHandler.resolveTargetFolder(key);
        return cloudHandler.uploadMessage(msg, key, targetFolder, adapter, overrideTargetUserEmail);
    }

    // ── Local Message Processing ─────────────────────────────────────────────

    private record LocalResult(boolean success, Exception exception) {}
    private final Map<String, Exception> sessionOpenExceptions = new java.util.concurrent.ConcurrentHashMap<>();

    private LocalResult processLocalMessage(MailMessage msg, String key) {
        com.pstconverter.core.output.OutputHandler.Session outSession = activeSessions.computeIfAbsent(key, k -> {
            try {
                String[] parts = k.split("/");
                File targetFolder;
                String resolvedOutputPath = config.resolvedOutputPath();
                if (config.isMonolithic() && resolvedOutputPath != null) {
                    targetFolder = new File(resolvedOutputPath);
                } else if (resolvedOutputPath != null) {
                    String relativePath = "";
                    if (parts.length > 2) {
                        StringBuilder sb = new StringBuilder();
                        for (int p = 2; p < parts.length; p++) {
                            if (sb.length() > 0) sb.append(File.separator);
                            sb.append(parts[p]);
                        }
                        relativePath = sb.toString();
                    }
                    targetFolder = new File(resolvedOutputPath);
                    if (!relativePath.isEmpty()) {
                        targetFolder = new File(resolvedOutputPath, relativePath);
                    }
                } else {
                    targetFolder = new File(System.getProperty("user.home"), "Documents");
                }
                if (!targetFolder.exists() && resolvedOutputPath != null) {
                    targetFolder.mkdirs();
                }
                com.pstconverter.core.output.OutputHandler handler = com.pstconverter.core.output.OutputFactory.getHandler(config.format());
                if (handler != null) {
                    return handler.openSession(targetFolder, config.attachmentHandling(), config.exportStructure(), config.namingConvention());
                }
            } catch (Exception ex) {
                sessionOpenExceptions.put(k, ex);
                foldersWithErrors.add(k);
                System.err.println("Failed to open output session: " + ex.getMessage());
            }
            return null;
        });

        if (outSession != null) {
            try {
                synchronized (outSession) {
                    outSession.writeMessage(msg);
                }
                return new LocalResult(true, null);
            } catch (Exception ex) {
                System.err.println("Failed to write email to session: " + ex.getMessage());
                return new LocalResult(false, ex);
            }
        }
        Exception openEx = sessionOpenExceptions.get(key);
        return new LocalResult(false, openEx != null ? openEx : new Exception("Output session unavailable"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int resolveFileIndex(File currentFile) {
        if (currentFile != null) {
            for (int idx = 0; idx < uniqueFiles.size(); idx++) {
                if (uniqueFiles.get(idx).getAbsolutePath().equalsIgnoreCase(currentFile.getAbsolutePath())) {
                    return idx + 1;
                }
            }
        }
        return 1;
    }

    private void closeSession(String key) {
        com.pstconverter.core.output.OutputHandler.Session session = activeSessions.remove(key);
        if (session != null) {
            try {
                session.close();
            } catch (Exception ex) {
                foldersWithErrors.add(key);
                System.err.println("Failed to close output session: " + ex.getMessage());
            }
        }
    }

    private void updateFolderStatuses(String key, String status) {
        int total = realFolderItemCounts != null ? realFolderItemCounts.getOrDefault(key, actualFolderItemCounts.getOrDefault(key, 0)) : actualFolderItemCounts.getOrDefault(key, 0);
        int success = telemetry.getSuccessInFolder(key);
        int failed = telemetry.getFailedInFolder(key);
        int skipped = telemetry.getSkippedInFolder(key);
        ui.updateTreeItemStatus(key, status, success, skipped, failed, total);
    }

    private void checkPauseLocal() {
        while (pauseRequested.get() && !stopRequested.get()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}

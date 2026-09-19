package com.pstconverter.controller.conversion;

import com.pstconverter.controller.ConversionUIContext;
import com.pstconverter.core.adapter.SourceAdapter;
import com.pstconverter.core.adapter.SourceAdapterFactory;
import com.pstconverter.model.MailMessage;
import com.pstconverter.util.DiagnosticLogger;
import com.pstconverter.util.LicenseManager;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.view.Step2ExplorerView;

import javafx.application.Platform;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Iterates over selected folders, streams emails from {@link SourceAdapter},
 * and feeds {@link WorkItem} entries into the consumer BlockingQueue.
 * Handles resume offset skipping, trial limit abort, and empty folder detection.
 * <p>
 * Runs on the main conversion task thread (blocking call).
 * Extracted from ConversionTaskManager's producer loop.
 */
public class MessageProducerLoop {

    private final List<String> pendingFolders;
    private final BlockingQueue<WorkItem> workQueue;
    private final ConversionConfig config;
    private final ConversionTelemetryTracker telemetry;
    private final ConversionSessionTracker sessions;
    private final ConversionUIContext ui;
    private final AtomicBoolean stopRequested;
    private final AtomicBoolean isParallelMode;

    // ── Shared maps (injected references) ────────────────────────────────────
    private final Map<String, Integer> actualFolderItemCounts;
    private final Map<String, Integer> realFolderItemCounts;
    private final Map<String, TreeItem<String>> originalFolderNodeMap;
    private final Map<String, File> folderToFileMap;
    private final Map<File, List<String>> fileFoldersMap;
    private final List<File> uniqueFiles;
    private final Set<String> foldersWithErrors;
    private final Step2ExplorerView step2;
    private final int totalFilesCount;
    private final int threadCount;
    private final int totalFoldersToProcess;

    public MessageProducerLoop(
            List<String> pendingFolders,
            BlockingQueue<WorkItem> workQueue,
            ConversionConfig config,
            ConversionTelemetryTracker telemetry,
            ConversionSessionTracker sessions,
            ConversionUIContext ui,
            AtomicBoolean stopRequested,
            AtomicBoolean isParallelMode,
            Map<String, Integer> actualFolderItemCounts,
            Map<String, Integer> realFolderItemCounts,
            Map<String, TreeItem<String>> originalFolderNodeMap,
            Map<String, File> folderToFileMap,
            Map<File, List<String>> fileFoldersMap,
            List<File> uniqueFiles,
            Set<String> foldersWithErrors,
            Step2ExplorerView step2,
            int totalFilesCount,
            int threadCount,
            int totalFoldersToProcess
    ) {
        this.pendingFolders = pendingFolders;
        this.workQueue = workQueue;
        this.config = config;
        this.telemetry = telemetry;
        this.sessions = sessions;
        this.ui = ui;
        this.stopRequested = stopRequested;
        this.isParallelMode = isParallelMode;
        this.actualFolderItemCounts = actualFolderItemCounts;
        this.realFolderItemCounts = realFolderItemCounts;
        this.originalFolderNodeMap = originalFolderNodeMap;
        this.folderToFileMap = folderToFileMap;
        this.fileFoldersMap = fileFoldersMap;
        this.uniqueFiles = uniqueFiles;
        this.foldersWithErrors = foldersWithErrors;
        this.step2 = step2;
        this.totalFilesCount = totalFilesCount;
        this.threadCount = threadCount;
        this.totalFoldersToProcess = totalFoldersToProcess;
    }

    /**
     * Blocking call — iterates all pending folders, streams emails into the work queue,
     * then sends poison pills and waits for session total updates.
     * Must be called from the background Task thread.
     */
    public void produce() {
        try {
            for (String key : pendingFolders) {
                if (stopRequested.get()) {
                    break;
                }
                processFolder(key);
            }
        } finally {
            // Queue poison pills to signal consumers to stop
            for (int i = 0; i < threadCount; i++) {
                try {
                    workQueue.put(new WorkItem(null, null, true));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            // Update the session's totalMessages to the real count now that
            // streaming has completed and actualFolderItemCounts is accurate
            for (File srcFile : uniqueFiles) {
                sessions.updateSessionTotalMessages(
                    srcFile, config.format(),
                    config.resolvedOutputPath(),
                    fileFoldersMap, actualFolderItemCounts
                );
            }
        }
    }

    private void processFolder(String key) {
        File currentFile = folderToFileMap.get(key);
        TreeItem<String> sourceNode = originalFolderNodeMap.get(key);
        final int[] queuedForThisFolder = {0};
        String folderName = key.substring(key.lastIndexOf('/') + 1);
        String format = config.format();
        String resolvedOutputPath = config.resolvedOutputPath();
        boolean isResume = config.isResume();
        boolean isCloud = config.isCloud();

        int currentFileIndex = resolveFileIndex(currentFile);

        if (sourceNode != null && step2 != null && currentFile != null && currentFile.exists()) {
            List<String> path = step2.getFolderPathFromNode(sourceNode);
            SourceAdapter parser = SourceAdapterFactory.getAdapterForFile(currentFile);
            boolean readSuccess = false;

            if (parser != null) {
                try {
                    int folderOffset = 0;
                    if (isResume) {
                        folderOffset = SettingsManager.getMigratedCountForFolder(
                            currentFile.getAbsolutePath(),
                            format,
                            resolvedOutputPath != null ? resolvedOutputPath : "",
                            key
                        );
                    }
                    final int finalOffset = folderOffset;
                    int totalItemsInFolder = actualFolderItemCounts.getOrDefault(key, 0);

                    if (isResume && totalItemsInFolder > 0 && finalOffset >= totalItemsInFolder) {
                        // Folder is already fully migrated — skip entirely
                        telemetry.addSkipped(key, totalItemsInFolder);
                        telemetry.addProcessed(totalItemsInFolder);
                        telemetry.addFolderProcessed(key, totalItemsInFolder);
                        queuedForThisFolder[0] += totalItemsInFolder;
                        System.out.println("Bypass resume: Folder " + key + " is already fully migrated (" + totalItemsInFolder + "/" + totalItemsInFolder + " items). Skipping entirely.");
                        DiagnosticLogger.log("Bypass resume: Folder " + key + " is already fully migrated (" + totalItemsInFolder + "/" + totalItemsInFolder + " items). Skipping entirely.");
                        int realTotal = realFolderItemCounts != null ? realFolderItemCounts.getOrDefault(key, totalItemsInFolder) : totalItemsInFolder;
                        Platform.runLater(() -> ui.updateTreeItemStatus(key, "DONE", 0, totalItemsInFolder, 0, realTotal));
                        telemetry.recordFolderComplete(key, totalItemsInFolder, "DONE");
                        telemetry.incrementFoldersDone();
                        DiagnosticLogger.logFolderStart(key, totalItemsInFolder, currentFileIndex, totalFilesCount);
                        DiagnosticLogger.logFolderComplete(key, totalItemsInFolder, 0, totalItemsInFolder, 0, "DONE", 0);
                        readSuccess = true;
                    } else {
                        if (finalOffset > 0) {
                            telemetry.addSkipped(key, finalOffset);
                            telemetry.addProcessed(finalOffset);
                            telemetry.addFolderProcessed(key, finalOffset);
                            queuedForThisFolder[0] += finalOffset;
                            System.out.println("Offset resume: Skipping first " + finalOffset + " items for " + key);
                            DiagnosticLogger.log("Offset resume: Skipping first " + finalOffset + " items for " + key);
                        }
                        parser.streamEmails(currentFile, path, msg -> {
                            try {
                                if (!LicenseManager.isActivated()) {
                                    int successInFolder = telemetry.getSuccessInFolder(key);
                                    if (successInFolder >= LicenseManager.TRIAL_LIMIT_PER_FOLDER) {
                                        throw new RuntimeException("TRIAL_LIMIT_ABORT");
                                    }
                                }
                                if (path != null && !path.isEmpty()) {
                                    msg.addMetadata("Original-Folder-Path", String.join("/", path));
                                }
                                workQueue.put(new WorkItem(key, msg, false));
                                queuedForThisFolder[0]++;
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(ie);
                            }
                        }, finalOffset, -1, isResume ? sessions::isMigrated : null);
                        readSuccess = true;
                    }
                    readSuccess = true;
                } catch (Exception ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("TRIAL_LIMIT_ABORT")) {
                        readSuccess = true;
                    } else if (ex.getCause() != null && ex.getCause().getMessage() != null && ex.getCause().getMessage().contains("TRIAL_LIMIT_ABORT")) {
                        readSuccess = true;
                    } else {
                        foldersWithErrors.add(key);
                        System.err.println("Mailbox stream failed for folder " + key + ": " + ex.getMessage());
                    }
                }
            }

            if (!readSuccess) {
                if (isCloud) {
                    ui.appendLog("  ⚠ Skipping cloud upload for " + folderName + " — could not read source emails from mailbox.");
                } else {
                    int estCount = actualFolderItemCounts.getOrDefault(key, 10);
                    if (!LicenseManager.isActivated()) {
                        estCount = Math.min(estCount, LicenseManager.TRIAL_LIMIT_PER_FOLDER);
                    }
                    System.out.println("Mailbox read failed. Generating simulated emails for testing. Count: " + estCount);
                    for (int i = 0; i < estCount; i++) {
                        MailMessage msg = new MailMessage(
                            "sender@example.com",
                            "Simulated Message #" + (i + 1),
                            new Date().toString(),
                            "This is a simulated message body for folder: " + folderName
                        );
                        try {
                            workQueue.put(new WorkItem(key, msg, false));
                            queuedForThisFolder[0]++;
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(ie);
                        }
                    }
                }
            }
        }

        // Adjust dynamically for the folder
        int originalTotal = actualFolderItemCounts.getOrDefault(key, 0);
        int actualTotal = queuedForThisFolder[0];
        if (actualTotal != originalTotal) {
            System.out.println("Adjusting expected items for folder " + key + " from " + originalTotal + " to " + actualTotal);
            actualFolderItemCounts.put(key, actualTotal);
        }

        if (actualTotal == 0) {
            String finalStatus = foldersWithErrors.contains(key) ? "ERROR" : "DONE";
            int realTotal = realFolderItemCounts != null ? realFolderItemCounts.getOrDefault(key, 0) : 0;
            ui.updateTreeItemStatus(key, finalStatus, 0, 0, 0, realTotal);
            int fd = telemetry.incrementFoldersDone();
            telemetry.recordFolderComplete(key, 0, finalStatus);
            ui.appendLog("  ✅ " + folderName + " — done (empty/no items)");
            if (isParallelMode.get()) {
                Platform.runLater(() -> {
                    ui.setMasterStatus("Folder " + fd + " of " + totalFoldersToProcess + " completed");
                    ui.updateSessionFiles("Files: " + fd + "/" + totalFoldersToProcess + " folders completed");
                });
            }
        }
    }

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
}

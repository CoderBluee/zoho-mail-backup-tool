package com.pstconverter.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DiagnosticLogger — Granular, toggleable diagnostic logging for PST Migrator Elite.
 *
 * <p>This utility writes detailed runtime diagnostics to a flat text report file so that
 * developers can trace the exact execution path during a migration session — including
 * which messages succeeded, which were skipped (and why), which failed, and the precise
 * wall-clock time each operation took.
 *
 * <h3>On/Off switch</h3>
 * Set {@link #DIAGNOSTIC_MODE} to {@code true} to enable all output.
 * Set it to {@code false} to suppress everything with zero overhead.
 *
 * <h3>Output location</h3>
 * <pre>~/Documents/PST Migrator Elite/testing_report.txt</pre>
 *
 * <h3>Thread safety</h3>
 * All write operations are guarded by an internal {@link ReentrantLock} so the logger
 * is safe to call from multiple worker threads simultaneously.
 */
public final class DiagnosticLogger {

    // ─────────────────────────────────────────────────────────────────────────
    // MASTER TOGGLE — flip to false to completely disable all diagnostic output.
    // ─────────────────────────────────────────────────────────────────────────
    public static volatile boolean DIAGNOSTIC_MODE = true;

    // ── Constants ──────────────────────────────────────────────────────────
    private static final String REPORT_DIR_NAME  = com.pstconverter.config.BrandConfig.REPORT_DIR_NAME;
    private static final String REPORT_FILE_NAME = "testing_report.txt";
    private static final String DIVIDER          = "═══════════════════════════════════════════════════════════════════════════════";
    private static final String THIN_DIVIDER     = "───────────────────────────────────────────────────────────────────────────────";
    private static final SimpleDateFormat TIMESTAMP_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    // ── Internal state ─────────────────────────────────────────────────────
    private static PrintWriter writer      = null;
    private static boolean     initialized = false;
    private static final ReentrantLock LOCK = new ReentrantLock();

    private DiagnosticLogger() {} // Non-instantiable utility class

    // =========================================================================
    // INITIALISATION
    // =========================================================================

    /**
     * Opens (or re-opens) the report file and writes the session header.
     * Must be called once at the start of each conversion run when
     * {@link #DIAGNOSTIC_MODE} is {@code true}.
     *
     * @param sessionLabel A short human-readable label for this session
     *                     (e.g. the source file name + format).
     */
    public static void init(String sessionLabel) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            // We only mirror to standard output; we no longer create or append to the global testing_report.txt file.
            writer = null;
            initialized = true;

            // Write session header
            writeln(DIVIDER);
            writeln("SESSION START  — " + ts());
            writeln("Label          : " + sessionLabel);
            writeln("Java Version   : " + System.getProperty("java.version"));
            writeln("OS             : " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            writeln("Available CPUs : " + Runtime.getRuntime().availableProcessors());
            long maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
            writeln("Max Heap       : " + maxMb + " MB");
            writeln(DIVIDER);
            flush();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Closes the report file and writes the session footer.
     * Must be called when the conversion task finishes (success or failure).
     */
    public static void close() {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            if (!initialized) return;
            writeln(DIVIDER);
            writeln("SESSION END    — " + ts());
            writeln(DIVIDER);
            writeln("");
            if (writer != null) {
                writer.flush();
                writer.close();
            }
            writer      = null;
            initialized = false;
        } finally {
            LOCK.unlock();
        }
    }

    // =========================================================================
    // WIZARD STEP TRANSITION LOGGING
    // =========================================================================

    /**
     * Logs the state capture when the user transitions from Step 1 to Step 2.
     *
     * @param files List of source file models describing what was imported.
     */
    public static void logStep1to2(java.util.List<com.pstconverter.core.model.SourceFileModel> files) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("");
            writeln(DIVIDER);
            writeln("WIZARD TRANSITION: Step 1 → Step 2  (" + ts() + ")");
            writeln(THIN_DIVIDER);
            writeln("Source files imported: " + files.size());
            for (int i = 0; i < files.size(); i++) {
                com.pstconverter.core.model.SourceFileModel f = files.get(i);
                writeln(String.format(
                    "  [%d] Name: %-40s | Size: %s | Format: %s | Status: %s",
                    i + 1, f.getFileName(), f.getFileSize(), f.getSourceType(), f.getStatus()));
                writeln("       Path: " + f.getFilePath());
            }
            writeln(DIVIDER);
            flush();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs folder selections when the user moves from Step 2 to Step 3.
     *
     * @param checkedItems  Checked tree items (raw string values).
     * @param totalFolders  Total folders found during analysis.
     */
    public static void logStep2to3(java.util.List<javafx.scene.control.TreeItem<String>> checkedItems,
                                    int totalFolders) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("");
            writeln(DIVIDER);
            writeln("WIZARD TRANSITION: Step 2 → Step 3  (" + ts() + ")");
            writeln(THIN_DIVIDER);
            writeln("Total folders analysed (in source): " + totalFolders);
            writeln("User-selected folders             : " + checkedItems.size());
            for (int i = 0; i < checkedItems.size(); i++) {
                writeln("  [" + (i + 1) + "] " + checkedItems.get(i).getValue());
            }
            writeln(DIVIDER);
            flush();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs all active filter settings when the user moves from Step 3 to Step 4.
     *
     * @param props The filter {@link java.util.Properties} as read from Step 3.
     */
    public static void logStep3to4(java.util.Properties props) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("");
            writeln(DIVIDER);
            writeln("WIZARD TRANSITION: Step 3 → Step 4  (" + ts() + ")");
            writeln(THIN_DIVIDER);
            if (props == null || props.isEmpty()) {
                writeln("  (No filters active — all messages will be exported)");
            } else {
                writeln("Active filter settings (" + props.size() + " properties):");
                for (String key : new java.util.TreeSet<>(props.stringPropertyNames())) {
                    writeln(String.format("  %-45s = %s", key, props.getProperty(key)));
                }
            }
            writeln(DIVIDER);
            flush();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs all output / destination parameters when the user moves from Step 4 to Step 5.
     *
     * @param format          The selected output format string.
     * @param outputPath      The resolved local destination path (null for cloud).
     * @param attachHandling  Attachment handling strategy label.
     * @param exportStructure Export structure label.
     * @param naming          Naming convention label.
     * @param metadataFields  Key-value map of metadata field include flags.
     */
    public static void logStep4to5(String format, String outputPath,
                                    String attachHandling, String exportStructure,
                                    String naming,
                                    java.util.Map<String, String> metadataFields) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("");
            writeln(DIVIDER);
            writeln("WIZARD TRANSITION: Step 4 → Step 5  (" + ts() + ")");
            writeln(THIN_DIVIDER);
            writeln("  Output Format           : " + format);
            writeln("  Target Output Path      : " + (outputPath != null ? outputPath : "(Cloud / not local)"));
            writeln("  Attachment Handling     : " + attachHandling);
            writeln("  Export Structure        : " + exportStructure);
            writeln("  File Naming Convention  : " + naming);
            if (metadataFields != null && !metadataFields.isEmpty()) {
                writeln("  Included Metadata Fields:");
                for (java.util.Map.Entry<String, String> e : metadataFields.entrySet()) {
                    writeln("    " + e.getKey() + " = " + e.getValue());
                }
            }
            writeln(DIVIDER);
            flush();
        } finally {
            LOCK.unlock();
        }
    }

    // =========================================================================
    // CONVERSION SESSION LOGGING
    // =========================================================================

    /**
     * Logs the overall conversion session start — totals, thread config, resume mode.
     *
     * @param isResume       True if this is a resume run (skip already-migrated messages).
     * @param totalFolders   Number of folders queued for export.
     * @param totalFiles     Number of unique source files in this run.
     * @param totalItems     Estimated total message count across all folders.
     * @param format         Output format.
     * @param threadCount    Number of consumer threads.
     * @param isMonolithic   True if using a single monolithic output file.
     */
    public static void logConversionStart(boolean isResume,
                                           int totalFolders, int totalFiles, long totalItems,
                                           String format, int threadCount, boolean isMonolithic) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("");
            writeln(DIVIDER);
            writeln("CONVERSION STARTED  — " + ts());
            writeln(THIN_DIVIDER);
            writeln("  Mode           : " + (isResume ? "RESUME (skip already migrated items)" : "FRESH RUN"));
            writeln("  Output Format  : " + format);
            writeln("  Total Folders  : " + totalFolders);
            writeln("  Total Files    : " + totalFiles);
            writeln("  Total Items    : " + totalItems + " (estimated)");
            writeln("  Thread Count   : " + threadCount);
            writeln("  Monolithic Mode: " + isMonolithic);
            writeln(DIVIDER);
            flush();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs the start of processing for a specific mail folder.
     *
     * @param folderKey     The unique folder path key.
     * @param estimatedItems Estimated number of items in this folder.
     * @param fileIndex     Index of the source file this folder belongs to.
     * @param totalFiles    Total source files.
     */
    public static void logFolderStart(String folderKey, int estimatedItems,
                                       int fileIndex, int totalFiles) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("");
            writeln(THIN_DIVIDER);
            writeln("FOLDER START  [" + ts() + "]");
            writeln("  Folder Key     : " + folderKey);
            writeln("  Estimated Items: " + estimatedItems);
            writeln("  Source File    : " + fileIndex + " / " + totalFiles);
            writeln(THIN_DIVIDER);
            flush();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs a successfully migrated message with full timing detail.
     *
     * @param msgId      Unique message identifier (from {@code MailMessage.getUniqueIdentifier()}).
     * @param subject    Email subject.
     * @param from       Sender address.
     * @param date       Message date string.
     * @param folderKey  Folder this message belongs to.
     * @param durationMs Wall-clock time in milliseconds from queue-pick to write-complete.
     */
    public static void logMessageSuccess(String msgId, String subject,
                                          String from, String date,
                                          String folderKey, double durationMs) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln(String.format(
                "[SUCCESS] [%s] ID=%-40s | Duration: %7.2f ms | Subject: \"%s\" | From: %s | Date: %s | Folder: %s",
                ts(), truncate(msgId, 40), durationMs,
                truncate(subject, 80), truncate(from, 50), date, folderKey));
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs a failed message export with the exception message.
     *
     * @param msgId      Unique message identifier.
     * @param subject    Email subject.
     * @param folderKey  Folder this message belongs to.
     * @param durationMs Duration until failure in milliseconds.
     * @param reason     Error reason or exception message.
     * @param ex         The exception if available (stack trace written to log), or null.
     */
    public static void logMessageFailed(String msgId, String subject,
                                         String folderKey, double durationMs,
                                         String reason, Throwable ex) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln(String.format(
                "[FAILED ] [%s] ID=%-40s | Duration: %7.2f ms | Subject: \"%s\" | Folder: %s | Reason: %s",
                ts(), truncate(msgId, 40), durationMs,
                truncate(subject, 80), folderKey, reason));
            if (ex != null) {
                writeln("           Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
                // Write a compact stack trace (top 10 frames only to keep the log readable)
                StackTraceElement[] frames = ex.getStackTrace();
                int limit = Math.min(frames.length, 10);
                for (int i = 0; i < limit; i++) {
                    writeln("             at " + frames[i].toString());
                }
                if (frames.length > 10) {
                    writeln("             ... " + (frames.length - 10) + " more frames omitted");
                }
            }
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs a skipped message with the exact reason for the skip.
     *
     * @param msgId       Unique message identifier.
     * @param subject     Email subject.
     * @param folderKey   Folder this message belongs to.
     * @param durationMs  Duration of the evaluation in milliseconds.
     * @param skipReason  Human-readable skip reason (e.g. "Already migrated", "Date out of range").
     */
    public static void logMessageSkipped(String msgId, String subject,
                                          String folderKey, double durationMs,
                                          String skipReason) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln(String.format(
                "[SKIP   ] [%s] ID=%-40s | Duration: %7.2f ms | Subject: \"%s\" | Folder: %s | Reason: %s",
                ts(), truncate(msgId, 40), durationMs,
                truncate(subject, 80), folderKey, skipReason));
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs a cloud upload attempt with result, attempt number, and timing.
     *
     * @param msgId        Unique message identifier.
     * @param subject      Email subject.
     * @param format       Cloud format (e.g. "Office 365", "Gmail").
     * @param attempt      Attempt number (1-based).
     * @param maxAttempts  Maximum allowed retries.
     * @param success      Whether this attempt succeeded.
     * @param durationMs   Duration of this attempt in milliseconds.
     * @param errorMsg     Error message on failure (null on success).
     */
    public static void logCloudUploadAttempt(String msgId, String subject,
                                              String format, int attempt, int maxAttempts,
                                              boolean success, double durationMs, String errorMsg) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            String result = success ? "OK  " : "FAIL";
            writeln(String.format(
                "[CLOUD-%s] [%s] Attempt %d/%d | Format: %-12s | Duration: %7.2f ms | ID: %s | Subject: \"%s\"%s",
                result, ts(), attempt, maxAttempts, format, durationMs,
                truncate(msgId, 36), truncate(subject, 60),
                errorMsg != null ? " | Error: " + errorMsg : ""));
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs the completion summary for a single folder.
     *
     * @param folderKey       The unique folder path key.
     * @param totalProcessed  Total messages dequeued for this folder.
     * @param successCount    Number successfully exported.
     * @param skippedCount    Number skipped.
     * @param failedCount     Number failed.
     * @param status          Final status string ("DONE", "ERROR", etc.).
     * @param elapsedMs       Total wall-clock time for this folder in ms.
     */
    public static void logFolderComplete(String folderKey,
                                          int totalProcessed, int successCount,
                                          int skippedCount, int failedCount,
                                          String status, long elapsedMs) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("");
            writeln(String.format(
                "FOLDER DONE [%s] Status: %-8s | Elapsed: %d ms | Folder: %s",
                ts(), status, elapsedMs, folderKey));
            writeln(String.format(
                "             Total: %d | ✅ Success: %d | ⏭ Skipped: %d | ❌ Failed: %d",
                totalProcessed, successCount, skippedCount, failedCount));
            writeln(THIN_DIVIDER);
            flush();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs the overall conversion completion summary.
     *
     * @param totalProcessed  Grand total items dequeued.
     * @param successCount    Grand total successfully exported.
     * @param skippedCount    Grand total skipped.
     * @param failedCount     Grand total failed.
     * @param stopped         True if user clicked Stop before completion.
     * @param elapsedMs       Total elapsed wall time for the entire run in ms.
     */
    public static void logConversionComplete(long totalProcessed,
                                              long successCount,
                                              long skippedCount,
                                              long failedCount,
                                              boolean stopped, long elapsedMs) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("");
            writeln(DIVIDER);
            writeln("CONVERSION " + (stopped ? "STOPPED" : "COMPLETE") + "  — " + ts());
            writeln(THIN_DIVIDER);
            writeln("  Total Items Processed  : " + totalProcessed);
            writeln("  ✅ Successfully Exported: " + successCount);
            writeln("  ⏭ Skipped              : " + skippedCount);
            writeln("  ❌ Failed               : " + failedCount);
            writeln("  Total Elapsed Time     : " + formatElapsed(elapsedMs));
            if (totalProcessed > 0 && elapsedMs > 0) {
                double rate = (double) totalProcessed / (elapsedMs / 1000.0);
                writeln(String.format("  Throughput             : %.1f items/sec", rate));
            }
            writeln(DIVIDER);
            flush();
        } finally {
            LOCK.unlock();
        }
    }

    // =========================================================================
    // GENERAL-PURPOSE LOGGING
    // =========================================================================

    /**
     * Logs a free-form informational message with a timestamp prefix.
     *
     * @param message The log message to write.
     */
    public static void log(String message) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("[INFO   ] [" + ts() + "] " + message);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs a warning message.
     *
     * @param message Warning text.
     */
    public static void warn(String message) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("[WARN   ] [" + ts() + "] " + message);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs an error message, optionally with a stack trace.
     *
     * @param message The error description.
     * @param ex      The exception (stack trace will be written), or null.
     */
    public static void error(String message, Throwable ex) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln("[ERROR  ] [" + ts() + "] " + message);
            if (ex != null) {
                writeln("           " + ex.getClass().getName() + ": " + ex.getMessage());
                StackTraceElement[] frames = ex.getStackTrace();
                int limit = Math.min(frames.length, 15);
                for (int i = 0; i < limit; i++) {
                    writeln("             at " + frames[i].toString());
                }
                if (frames.length > 15) {
                    writeln("             ... " + (frames.length - 15) + " more frames omitted");
                }
            }
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs resume/skip tracking events during a resume run.
     *
     * @param msgId       Unique message identifier.
     * @param subject     Email subject.
     * @param folderKey   Folder key.
     * @param durationMs  Local DB lookup duration in milliseconds.
     */
    public static void logResumeSkip(String msgId, String subject,
                                      String folderKey, double durationMs) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln(String.format(
                "[SKIP   ] [%s] RESUME — Already migrated in previous session | DB check: %.2f ms | ID: %s | Subject: \"%s\" | Folder: %s",
                ts(), durationMs, truncate(msgId, 40), truncate(subject, 80), folderKey));
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs a filter rejection with the rejection reason.
     *
     * @param msgId       Unique message identifier.
     * @param subject     Email subject.
     * @param folderKey   Folder key.
     * @param durationMs  Filter evaluation duration in milliseconds.
     * @param reason      Human-readable rejection reason from FilterEngine.
     */
    public static void logFilterReject(String msgId, String subject,
                                        String folderKey, double durationMs,
                                        String reason) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln(String.format(
                "[SKIP   ] [%s] FILTER — %s | Eval: %.2f ms | ID: %s | Subject: \"%s\" | Folder: %s",
                ts(), reason, durationMs, truncate(msgId, 40), truncate(subject, 80), folderKey));
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs trial limit skip events.
     *
     * @param msgId       Unique message identifier.
     * @param subject     Email subject.
     * @param folderKey   Folder key.
     * @param durationMs  Duration in milliseconds.
     */
    public static void logTrialLimitSkip(String msgId, String subject,
                                          String folderKey, double durationMs) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln(String.format(
                "[SKIP   ] [%s] TRIAL LIMIT — Max %d items/folder reached | Duration: %.2f ms | ID: %s | Subject: \"%s\" | Folder: %s",
                ts(), LicenseManager.TRIAL_LIMIT_PER_FOLDER, durationMs, truncate(msgId, 40), truncate(subject, 80), folderKey));
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs a session record event (writing to migration_sessions DB).
     *
     * @param sourceFilePath  Absolute path of the source file.
     * @param format          Output format.
     * @param destinationPath Resolved destination path (empty for cloud).
     * @param totalFolders    Total folder count stored in the session.
     * @param totalMessages   Total message count stored in the session.
     */
    public static void logSessionSaved(String sourceFilePath, String format,
                                        String destinationPath,
                                        int totalFolders, int totalMessages) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln(String.format(
                "[SESSION] [%s] IN_PROGRESS session saved | Format: %s | Folders: %d | Messages: %d | Dest: %s | Source: %s",
                ts(), format, totalFolders, totalMessages,
                destinationPath.isEmpty() ? "(cloud)" : destinationPath,
                sourceFilePath));
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Logs a session cleared event (migration fully completed for this source/format/dest triplet).
     *
     * @param sourceFilePath  Source file path.
     * @param format          Output format.
     * @param destinationPath Destination path.
     */
    public static void logSessionCleared(String sourceFilePath, String format, String destinationPath) {
        if (!DIAGNOSTIC_MODE) return;
        LOCK.lock();
        try {
            writeln(String.format(
                "[SESSION] [%s] Session cleared (migration complete) | Format: %s | Dest: %s | Source: %s",
                ts(), format,
                destinationPath.isEmpty() ? "(cloud)" : destinationPath,
                sourceFilePath));
        } finally {
            LOCK.unlock();
        }
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private static void writeln(String line) {
        // Called only while holding LOCK
        if (writer != null) {
            writer.println(line);
        }
        // Mirror to stdout so the in-app log area also shows diagnostics
        System.out.println(line);
    }

    private static void flush() {
        if (writer != null) {
            writer.flush();
        }
    }

    /** Returns a timestamp string for use inside log lines. */
    private static String ts() {
        return TIMESTAMP_FMT.format(new Date());
    }

    /** Truncates a string to maxLen chars, appending "…" if needed. */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "(null)";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 1) + "…";
    }

    /** Formats an elapsed millisecond count into a human-readable string. */
    private static String formatElapsed(long ms) {
        if (ms < 1000) return ms + " ms";
        long secs  = ms / 1000;
        long mins  = secs / 60;
        long hours = mins / 60;
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, mins % 60, secs % 60);
        } else if (mins > 0) {
            return String.format("%dm %ds", mins, secs % 60);
        } else {
            return String.format("%.2f s", ms / 1000.0);
        }
    }
}

package com.pstconverter.controller.conversion;

import com.pstconverter.core.destination.EmailDestinationConfig;
import com.pstconverter.model.MailMessage;
import com.pstconverter.util.DiagnosticLogger;
import com.pstconverter.util.SettingsManager;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates all SQLite migration session persistence — saving IN_PROGRESS sessions,
 * marking them COMPLETED, preloading migrated IDs for resume, and logging failed emails to CSV.
 * <p>
 * Decoupled from JavaFX and threading concerns — pure data/DB operations.
 */
public class ConversionSessionTracker {

    private final Set<String> migratedMessageIds = ConcurrentHashMap.newKeySet();

    // ── Resume ID Management ─────────────────────────────────────────────────

    /**
     * Preloads all migrated message IDs for the given source files into an in-memory
     * set for O(1) resume-skip checks during conversion.
     */
    public Set<String> preloadMigratedIds(List<File> uniqueFiles, String format, String destPath) {
        migratedMessageIds.clear();
        for (File srcFile : uniqueFiles) {
            Set<String> ids = SettingsManager.getMigratedMessageIdsForFile(
                srcFile.getAbsolutePath(), format, destPath
            );
            migratedMessageIds.addAll(ids);
        }
        System.out.println("Preloaded " + migratedMessageIds.size() + " migrated message IDs into memory cache.");
        return migratedMessageIds;
    }

    /**
     * Clears all preloaded migrated IDs (used for non-resume runs).
     */
    public void clearMigratedIds() {
        migratedMessageIds.clear();
    }

    /**
     * Checks if a message ID is already in the preloaded migrated set.
     */
    public boolean isMigrated(String msgId) {
        return migratedMessageIds.contains(msgId);
    }

    /**
     * Records a successfully migrated message both in the in-memory cache and the DB.
     */
    public void recordMigration(String filePath, String format, String outputPath,
                                 String folderKey, String msgId) {
        migratedMessageIds.add(msgId);
        SettingsManager.recordMessageMigration(filePath, format, outputPath, folderKey, msgId, "SUCCESS");
    }

    // ── Session Persistence ──────────────────────────────────────────────────

    /**
     * Saves an IN_PROGRESS session record for every unique source file involved
     * in the current conversion run. Called once at the start of launchConversionTask.
     */
    public void saveInProgressSessions(List<File> uniqueFiles,
                                        Map<File, List<String>> fileFoldersMap,
                                        Map<String, Integer> actualFolderItemCounts,
                                        Map<String, File> folderToFileMap,
                                        ConversionConfig config,
                                        String filterStr, String outputStr,
                                        int totalFolders, List<String> pendingFolderKeys) {
        String format = config.format();
        String outPath = config.resolvedOutputPath() != null ? config.resolvedOutputPath() : "";

        // Collect selected folder path keys per source file
        Map<File, List<String>> fileToFolderKeys = new LinkedHashMap<>();
        for (String key : pendingFolderKeys) {
            File srcFile = folderToFileMap.get(key);
            if (srcFile != null) {
                fileToFolderKeys.computeIfAbsent(srcFile, f -> new ArrayList<>()).add(key);
            }
        }

        for (File srcFile : uniqueFiles) {
            List<String> folderKeysForFile = fileToFolderKeys.getOrDefault(srcFile, Collections.emptyList());

            int totalMessages = 0;
            for (String key : folderKeysForFile) {
                totalMessages += actualFolderItemCounts.getOrDefault(key, 0);
            }

            String selectedFolders = String.join(",", folderKeysForFile);

            SettingsManager.MigrationSession existing =
                SettingsManager.getMigrationSession(srcFile.getAbsolutePath(), format, outPath);

            if (existing == null || !"IN_PROGRESS".equals(existing.status())) {
                SettingsManager.saveMigrationSession(
                    new SettingsManager.MigrationSession(
                        srcFile.getAbsolutePath(),
                        outPath,
                        format,
                        config.exportStructure() != null ? config.exportStructure() : "",
                        config.attachmentHandling() != null ? config.attachmentHandling() : "",
                        config.namingConvention() != null ? config.namingConvention() : "",
                        filterStr,
                        outputStr,
                        totalFolders,
                        totalMessages,
                        "IN_PROGRESS",
                        null,
                        selectedFolders
                    )
                );
                DiagnosticLogger.logSessionSaved(
                    srcFile.getAbsolutePath(), format, outPath, totalFolders, totalMessages
                );
                System.out.println("[SessionTracker] Saved IN_PROGRESS session for: " + srcFile.getName() + " format=" + format + " dest=" + outPath);
            } else {
                System.out.println("[SessionTracker] Resuming existing IN_PROGRESS session for: " + srcFile.getName());
            }
        }
    }

    /**
     * Keeps sessions as IN_PROGRESS when the user stops migration early.
     * Preserves the original total message count.
     */
    public void keepSessionsInProgress(List<File> uniqueFiles, String format, String outputPath) {
        for (File sourceFile : uniqueFiles) {
            SettingsManager.MigrationSession existing =
                SettingsManager.getMigrationSession(sourceFile.getAbsolutePath(), format, outputPath);
            if (existing != null) {
                SettingsManager.saveMigrationSession(
                    new SettingsManager.MigrationSession(
                        existing.sourceFilePath(),
                        existing.destinationPath(),
                        existing.format(),
                        existing.exportStructure(),
                        existing.attachmentHandling(),
                        existing.namingConvention(),
                        existing.filterSettings(),
                        existing.outputProperties(),
                        existing.totalFolders(),
                        existing.totalMessages(),
                        "IN_PROGRESS",
                        existing.timestamp(),
                        existing.selectedFolders()
                    )
                );
            }
        }
    }

    /**
     * Marks sessions as COMPLETED when migration finishes successfully.
     */
    public void markSessionsCompleted(List<File> uniqueFiles, String format, String outputPath) {
        for (File sourceFile : uniqueFiles) {
            SettingsManager.completeMigrationSession(
                sourceFile.getAbsolutePath(), format, outputPath);
            DiagnosticLogger.log(String.format(
                "[SESSION] Completed session saved to history | File: %s | Format: %s",
                sourceFile.getName(), format));
        }
    }

    /**
     * Updates the totalMessages count for a session after streaming reveals the actual count.
     */
    public void updateSessionTotalMessages(File srcFile, String format, String outputPath,
                                            Map<File, List<String>> fileFoldersMap,
                                            Map<String, Integer> actualFolderItemCounts) {
        String outPath = outputPath != null ? outputPath : "";
        SettingsManager.MigrationSession sess =
            SettingsManager.getMigrationSession(srcFile.getAbsolutePath(), format, outPath);
        if (sess != null) {
            List<String> fkeys = fileFoldersMap.get(srcFile);
            int realTotal = 0;
            if (fkeys != null) {
                for (String fk : fkeys) {
                    realTotal += actualFolderItemCounts.getOrDefault(fk, 0);
                }
            }
            if (realTotal != sess.totalMessages()) {
                SettingsManager.saveMigrationSession(
                    new SettingsManager.MigrationSession(
                        sess.sourceFilePath(), sess.destinationPath(), sess.format(),
                        sess.exportStructure(), sess.attachmentHandling(), sess.namingConvention(),
                        sess.filterSettings(), sess.outputProperties(),
                        sess.totalFolders(), realTotal,
                        sess.status(), sess.timestamp(), sess.selectedFolders()
                    )
                );
                System.out.println("[SessionTracker] Updated totalMessages for " + srcFile.getName() + " → " + realTotal);
            }
        }
    }

    // ── Failed Email CSV Logging ─────────────────────────────────────────────

    private int consoleErrorCount = 0;

    /**
     * Logs a failed email to the session's failed_emails.csv file.
     */
    public synchronized void logFailedEmail(MailMessage msg, String errorReason, File sessionDir) {
        consoleErrorCount++;
        if (consoleErrorCount <= 20) {
            System.err.println("Failed email logged to CSV: " + (msg != null ? msg.getSubject() : "Unknown Subject") + " | Reason: " + errorReason);
        } else if (consoleErrorCount == 21) {
            System.err.println("... Further individual email failures will be logged to CSV only to prevent console spam.");
        }
        if (sessionDir == null) return;
        File logsDir = new File(sessionDir, "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        File csvFile = new File(logsDir, "failed_emails.csv");
        boolean isNew = !csvFile.exists();
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile, true))) {
            if (isNew) {
                writer.println("Date,Sender,Subject,Message-ID,Error Reason");
            }
            String date = msg != null && msg.getDate() != null ? escapeCsv(msg.getDate()) : "Unknown";
            String sender = msg != null && msg.getFrom() != null ? escapeCsv(msg.getFrom()) : "Unknown";
            String subject = msg != null && msg.getSubject() != null ? escapeCsv(msg.getSubject()) : "Unknown";
            String msgId = msg != null && msg.getMessageId() != null ? escapeCsv(msg.getMessageId()) : "Unknown";
            String reason = escapeCsv(errorReason);
            writer.println(date + "," + sender + "," + subject + "," + msgId + "," + reason);
        } catch (Exception ex) {
            System.err.println("Failed to write to failed_emails.csv: " + ex.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Serializes a Properties object into a newline-separated key=value string
     * suitable for storing in the migration_sessions table.
     */
    public static String serializePropertiesToString(Properties props) {
        if (props == null || props.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String key : props.stringPropertyNames()) {
            sb.append(key).append('=').append(props.getProperty(key)).append('\n');
        }
        return sb.toString().trim();
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        String clean = value.replace("\"", "\"\"");
        if (clean.contains(",") || clean.contains("\n") || clean.contains("\r") || clean.contains("\"")) {
            return "\"" + clean + "\"";
        }
        return clean;
    }
}

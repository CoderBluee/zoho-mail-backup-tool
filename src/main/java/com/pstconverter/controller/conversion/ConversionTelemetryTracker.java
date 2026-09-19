package com.pstconverter.controller.conversion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns all atomic counters, per-folder telemetry maps, and skip-reason tracking
 * for a single conversion run. Provides clean getters for the Step 6 report panel.
 * <p>
 * Thread-safe: all mutable state uses atomic types or explicit synchronization.
 */
public class ConversionTelemetryTracker {

    // ── Global counters ──────────────────────────────────────────────────────
    private final AtomicLong processedItems = new AtomicLong(0);
    private final AtomicLong successItems = new AtomicLong(0);
    private final AtomicLong failedItems = new AtomicLong(0);
    private final AtomicLong skippedItems = new AtomicLong(0);
    private final AtomicLong outputSizeBytes = new AtomicLong(0);
    private final AtomicLong lastUiUpdateMs = new AtomicLong(0);

    private final AtomicInteger foldersDone = new AtomicInteger(0);
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final AtomicInteger consoleErrorSpamCounter = new AtomicInteger(0);

    // ── Per-folder counters ──────────────────────────────────────────────────
    private final Map<String, AtomicInteger> folderSuccessCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> folderFailedCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> folderSkippedCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> folderProcessedCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> folderActiveProcessedCount = new ConcurrentHashMap<>();

    // ── Folder telemetry snapshots (for Step 6 report) ───────────────────────
    public static record FolderTelemetry(
        int totalCount,
        int successCount,
        int skippedCount,
        int failedCount,
        String status
    ) {}

    private final Map<String, FolderTelemetry> folderTelemetryMap = new LinkedHashMap<>();

    // ── Skip reason categories ───────────────────────────────────────────────
    private final Map<String, Integer> filterSkipCounts = new LinkedHashMap<>();

    // ── Reset ────────────────────────────────────────────────────────────────

    public void reset() {
        processedItems.set(0);
        successItems.set(0);
        failedItems.set(0);
        skippedItems.set(0);
        outputSizeBytes.set(0);
        lastUiUpdateMs.set(0);
        foldersDone.set(0);
        activeTasks.set(0);
        consoleErrorSpamCounter.set(0);

        folderSuccessCounts.clear();
        folderFailedCounts.clear();
        folderSkippedCounts.clear();
        folderProcessedCounts.clear();
        folderActiveProcessedCount.clear();

        synchronized (folderTelemetryMap) {
            folderTelemetryMap.clear();
        }
        synchronized (filterSkipCounts) {
            filterSkipCounts.clear();
        }
    }

    // ── Increment operations ─────────────────────────────────────────────────

    public long incrementProcessed() {
        return processedItems.incrementAndGet();
    }

    public void addProcessed(long count) {
        processedItems.addAndGet(count);
    }

    public long incrementSuccess(String folderKey) {
        folderSuccessCounts.computeIfAbsent(folderKey, k -> new AtomicInteger(0)).incrementAndGet();
        return successItems.incrementAndGet();
    }

    public long incrementFailed(String folderKey) {
        folderFailedCounts.computeIfAbsent(folderKey, k -> new AtomicInteger(0)).incrementAndGet();
        return failedItems.incrementAndGet();
    }

    public long incrementSkipped(String folderKey) {
        folderSkippedCounts.computeIfAbsent(folderKey, k -> new AtomicInteger(0)).incrementAndGet();
        return skippedItems.incrementAndGet();
    }

    public void addSkipped(String folderKey, int count) {
        folderSkippedCounts.computeIfAbsent(folderKey, k -> new AtomicInteger(0)).addAndGet(count);
        skippedItems.addAndGet(count);
    }

    public void addOutputBytes(long bytes) {
        outputSizeBytes.addAndGet(bytes);
    }

    public int incrementFolderProcessed(String folderKey) {
        int val = folderProcessedCounts.computeIfAbsent(folderKey, k -> new AtomicInteger(0)).incrementAndGet();
        folderActiveProcessedCount.put(folderKey, val);
        return val;
    }

    public void addFolderProcessed(String folderKey, int count) {
        int val = folderProcessedCounts.computeIfAbsent(folderKey, k -> new AtomicInteger(0)).addAndGet(count);
        folderActiveProcessedCount.put(folderKey, val);
    }

    public int incrementFoldersDone() {
        return foldersDone.incrementAndGet();
    }

    public int getSuccessInFolder(String folderKey) {
        AtomicInteger count = folderSuccessCounts.get(folderKey);
        return count != null ? count.get() : 0;
    }

    public int getFailedInFolder(String folderKey) {
        AtomicInteger count = folderFailedCounts.get(folderKey);
        return count != null ? count.get() : 0;
    }

    public int getSkippedInFolder(String folderKey) {
        AtomicInteger count = folderSkippedCounts.get(folderKey);
        return count != null ? count.get() : 0;
    }

    // ── Folder telemetry snapshot ────────────────────────────────────────────

    public void recordFolderComplete(String key, int totalProcessed, String status) {
        int success = getSuccessInFolder(key);
        int failed = getFailedInFolder(key);
        int skipped = getSkippedInFolder(key);
        synchronized (folderTelemetryMap) {
            folderTelemetryMap.put(key, new FolderTelemetry(totalProcessed, success, skipped, failed, status));
        }
    }

    // ── Skip reason tracking ─────────────────────────────────────────────────

    public void trackSkipReason(String reason) {
        if (reason == null) return;
        System.out.println("Filtering Skip Reason tracked: " + reason);
        String category = "Other Exclusion Rules";
        if (reason.startsWith("Item Type:")) {
            category = "Item Type Constraint";
        } else if (reason.startsWith("Date:")) {
            category = "Date Range Filter";
        } else if (reason.startsWith("Sender:")) {
            category = "Sender Address Filter";
        } else if (reason.startsWith("Recipient:")) {
            category = "Recipient Address Filter";
        } else if (reason.startsWith("Keywords:")) {
            category = "Keyword Search Filter";
        } else if (reason.startsWith("Attachments:")) {
            category = "Attachment Constraint";
        } else if (reason.contains("empty message") || reason.contains("Empty message")) {
            category = "Empty Email Hygiene";
        } else if (reason.contains("duplicate") || reason.contains("Duplicate")) {
            category = "Duplicate Elimination";
        } else if (reason.contains("Already migrated") || reason.contains("Already on server")) {
            category = "Already Converted (Skipped)";
        } else if (reason.contains("Trial Limit Reached") || reason.contains("Trial Mode")) {
            category = "Trial Mode Limit (Skipped)";
        } else if (reason.startsWith("Corrupt Item:")) {
            category = "Corrupted Mailbox Item (Skipped)";
        }

        synchronized (filterSkipCounts) {
            filterSkipCounts.put(category, filterSkipCounts.getOrDefault(category, 0) + 1);
        }
    }

    // ── Getters (for UI polling and Step 6 report) ───────────────────────────

    public long getProcessedItems() { return processedItems.get(); }
    public long getSuccessItems() { return successItems.get(); }
    public long getFailedItems() { return failedItems.get(); }
    public long getSkippedItems() { return skippedItems.get(); }
    public long getOutputSizeBytes() { return outputSizeBytes.get(); }
    public long getLastUiUpdateMs() { return lastUiUpdateMs.get(); }
    public void setLastUiUpdateMs(long ms) { lastUiUpdateMs.set(ms); }
    public int getFoldersDone() { return foldersDone.get(); }
    public int getConsoleErrorSpamCount() { return consoleErrorSpamCounter.get(); }
    public int incrementConsoleErrorSpam() { return consoleErrorSpamCounter.incrementAndGet(); }

    public Map<String, FolderTelemetry> getFolderTelemetryMap() {
        synchronized (folderTelemetryMap) {
            return new LinkedHashMap<>(folderTelemetryMap);
        }
    }

    public Map<String, Integer> getFilterSkipCounts() {
        synchronized (filterSkipCounts) {
            return new LinkedHashMap<>(filterSkipCounts);
        }
    }
}

# Prism Zoho Mail Backup Tool — JavaFX Desktop Application (Modular Template)

<!-- SSOT_RULES_HEADER_START -->
> **🚨 MANDATORY SINGLE SOURCE OF TRUTH (SSOT) FOR ALL RULES & PACKAGING:**
> All engineering rules, pre-packaging checklists, packaging pipelines, UI standards, and content guidelines are centralized in the master repository. Do NOT duplicate rule files inside this project. Always consult and reference the master SSOT directory:
> 
> 📁 **Master Rulebook Hub:** [`docs/rules/`](file:///Users/akashsahu.blue/Documents/Akas/software-selling-platform/docs/rules/README.md)
> 
> ### 🛑 Mandatory Pre-Packaging Gates (Before Building JAR / EXE / DMG):
> 1. **💡 Universal Default Light Theme:** ALL desktop tools MUST initialize with **FlatLaf Light** (`FlatLightLaf.setup()` / `FlatMacLightLaf`) or native Light theme by default on first launch. Dark mode may only exist as an optional user toggle.
> 2. **⌨️ 100% Keyboard Operability:** The entire conversion flow must be executable mouse-free using `Tab`/`Shift+Tab` focus cycles, `Enter`/`Space`, and arrow keys (Section 508 standard).
> 3. **📦 Shaded Fat JAR Size Gate:** Fat JAR size must be `< 75 MB` (Target: 50MB–65MB). If `> 100MB`, stop and inspect dependency tree (`mvn dependency:tree`).
> 4. **🪟 Windows Setup EXE:** Output standard `Prism-[Tool-Name]-Setup.exe`, embedded 64-bit JRE 17/21, total installer `< 135 MB`.
> 5. **🍎 macOS DMG Package:** Install4j project preserved in Obsidian vault (`Obsidian-Work/Package Maker/Install4j/`), output to `~/Downloads/`.
> 6. **🔍 Full Master Rulebook:** [Desktop Packaging Master Rulebook (SSOT)](file:///Users/akashsahu.blue/Documents/Akas/software-selling-platform/docs/rules/packaging/desktop-packaging-master-rulebook.md) & [Developer KT Handover Rulebook](file:///Users/akashsahu.blue/Documents/Akas/software-selling-platform/docs/rules/engineering/dev-kt-handover-rulebook.md).
<!-- SSOT_RULES_HEADER_END -->

## Overview
Java 21 / JavaFX 21 Maven project. A 6-step wizard that reads Outlook PST files (and other future mailbox formats generic enough to be adapted), lets users browse/filter mailbox contents, and exports to 17 different target formats (PDF, HTML, MBOX, EML, MSG, CSV, JSON, MHTML, RTF, DOC, PST, EMLX, Office 365, Gmail, IMAP, Yahoo Mail). Uses `java-libpst` (0.9.3) for PST parsing, Apache PDFBox (2.0.31) for generating valid, page-aware PDF files, MaterialFX for UI controls, and SQLite for settings.

## Entry Points
- **Launcher.java** (7 lines) — public static void main (avoids JavaFX module issues with shaded JAR). Manifest main-class for the fat JAR.
- **App.java** (44 lines) — extends `Application`, calls `Application.launch()`. Loads MaterialFX `UserAgentBuilder` for theming, then initializes `MainController`.
- **MainController.java** — extends `BorderPane`. Acts as the wizard application controller. Holds shared state (`ObservableList<SourceFileModel> fileList`, `int currentStep`). Swaps center view via `showStep()`. Manages theme switching via `SettingsManager` and provides helper `isDarkMode()` to query the active theme.

## Build System (Maven)
- **Java**: source/target 21 (uses Oracle JDK 21 at `D:\IDE and Zip Softwares\Java\jdk-21_windows-x64_bin\jdk-21.0.8`)
- **Run**: `mvn javafx:run` (uses `javafx-maven-plugin` 0.0.8)
- **Package fat JAR & Obfuscate**:
  ```cmd
  set JAVA_HOME=D:\IDE and Zip Softwares\Java\jdk-21_windows-x64_bin\jdk-21.0.8
  set PATH=D:\IDE and Zip Softwares\Java\jdk-21_windows-x64_bin\jdk-21.0.8\bin;%PATH%
  mvn clean package -DskipTests
  ```
  Produces timestamped Fat JAR and ProGuard protected JAR in `target/`:
  - `target/zoho-mail-backup-tool-1.0-SNAPSHOT-win-<yyyyMMdd-HHmm>.jar` (~110 MB)
  - `target/zoho-mail-backup-tool-1.0-SNAPSHOT-win-<yyyyMMdd-HHmm>-protected.jar` (~103 MB)
- **Dependencies**:
  - `org.openjfx:javafx-controls:21`, `javafx-web:21`, `javafx-fxml:21`, `javafx-media:21`
  - `org.xerial:sqlite-jdbc:3.45.1.0`
  - `com.pff:java-libpst:0.9.3`
  - `org.apache.pdfbox:pdfbox:2.0.31`
  - `org.slf4j:slf4j-api:2.0.9`, `org.slf4j:slf4j-simple:2.0.9`
  - `io.github.palexdev:materialfx:11.17.0`
  - **Local In-Project Maven Repository (`libs/repo`)**:
    - `libs/chilkat.jar` (`com.chilkatsoft:chilkat:9.5.0`)
    - `libs/aspose-email-22.7-jdk16.jar` (`com.aspose:aspose-email:22.7`)

## Production Build & Distribution Pipeline (EXE & AIP)

### Automated Packaging Script (`package_exe.ps1`)
Run in PowerShell:
```powershell
.\package_exe.ps1
```
Optional flags:
- `-SkipBuild`: Skips Maven recompilation and packages the latest `target/*-protected.jar`.
- `-AppImageOnly`: Stops after generating the portable application directory (`target/app-image/`).

### Deliverables & Output Directory Paths

| Deliverable | Purpose | Exact Path |
|---|---|---|
| **Single-File Setup Installer** | Final installer EXE for end-users (Advanced Installer `ExeInside`, shortcuts, icons, splash) | `I:\My Drive\Akas\EXE\Prism-Zoho-Mail-Backup-Tool-Setup.exe` (~135.4 MB) |
| **Advanced Installer Project** | `.aip` file source for setup configurations | `I:\My Drive\Akas\Package Maker\Advance Package\Prism-Zoho-Mail-Backup-Tool.aip` |
| **Packaging Automation Script** | Reusable pipeline script synced to Drive | `I:\My Drive\Akas\Package Maker\Advance Package\package_exe_zoho_mail_backup_tool.ps1` |
| **Standalone Protected JAR** | Deployed copy of latest obfuscated Java 21 JAR | `I:\My Drive\Akas\Jars\IMAP Backup\Prism-Zoho-Mail-Backup-Tool.jar` (~103.65 MB) |
| **Local Standalone App-Image** | Portable runtime folder with launcher for local developer testing | `target/app-image/Prism-Zoho-Mail-Backup-Tool/Prism-Zoho-Mail-Backup-Tool.exe` (~583 KB launcher + bundled JRE) |

### Understanding the 2 Types of EXEs
1. **Portable Launcher EXE (`target/app-image/.../Prism-Zoho-Mail-Backup-Tool.exe` - 583 KB)**:
   Generated by `jpackage`. It is a lightweight native bootstrapper that requires the adjacent `runtime/` and `app/` folders. It is meant for portable execution or developer testing without installing.
2. **Single-File Setup Installer EXE (`I:\My Drive\Akas\EXE\Prism-Zoho-Mail-Backup-Tool-Setup.exe` - 135.4 MB)**:
   Generated by Advanced Installer. Everything (the JRE runtime, protected JAR, icons, splash, licenses) is compressed into a single self-extracting installer. When users run it, it installs to `Program Files`, creates Desktop & Start Menu shortcuts, and adds an uninstaller in Control Panel.

## Full File Structure

```
src/main/java/com/pstconverter/
  App.java                          — Entry point
  Launcher.java                     — Manifest main-class launcher
  controller/
    MainController.java             — Wizard controller orchestrator
  model/
    MailMessage.java                — Single email/item POJO
    MailboxFolder.java              — Tree node representing parsed mailbox hierarchy
  core/
    adapter/
      SourceAdapter.java            — Interface for mailbox format adapters
      SourceAdapterFactory.java     — Dynamic reflection-based adapter registry
    filter/
      FilterEngine.java             — Active filtering, search, and deduplication logic
    exception/
      ConverterException.java       — General conversion processing exception
    model/
      SourceFileModel.java          — Generic model for imported files (replaces PstFileModel)
    output/
      OutputCategory.java           — Target format classification (Documents, Emails, Cloud)
      OutputHandler.java            — Strategy interface for format writers with text escaping helpers
      OutputFactory.java            — Exporter strategy registry
      handlers/
        PdfOutputHandler.java       — Apache PDFBox page-aware PDF generator
        HtmlOutputHandler.java      — HTML5 responsive card generator
        MhtmlOutputHandler.java     — Multipart MIME MHTML writer
        TxtOutputHandler.java       — Clean text/metadata writer
        RtfOutputHandler.java       — Rich Text RTF layout writer
        DocOutputHandler.java       — MS Word XML-HTML layout writer
        JsonOutputHandler.java      — Escaped JSON serialization handler
        CsvOutputHandler.java       — RFC 4180 CSV log writer
        MboxOutputHandler.java      — Unix MBOX RFC 4155 envelope writer
        EmlOutputHandler.java       — Standard RFC 822 internet message writer
        MsgOutputHandler.java       — Plain MSG envelope writer
        PstOutputHandler.java       — Mock archive PST writer
        EmlxOutputHandler.java      — Apple Mail EMLX XML-plist writer
        CloudOutputHandler.java     — Mock Office 365, Gmail, IMAP, Yahoo Mail uploader
  pst/
    PstSourceAdapter.java           — Isolated java-libpst mailbox parsing logic
    OutlookDetector.java            — Local Outlook PST file auto-detector
  util/
    SettingsManager.java            — SQLite key-value settings database store (houses centralized public settings key constants)
    MaterialIcons.java              — Icon codes and styling configurations
    ThemeManager.java               — Static utility for theme resolution (dark/light/system) and CSS switching
    DiagnosticLogger.java           — Toggleable diagnostic report writer (testing_report.txt)
    DiagnosticsExporter.java        — ZIP export of session logs and settings DB
    LicenseManager.java             — License activation and trial enforcement
    ValidateMetadata.java           — Metadata validation utilities
    ValidatePstSplit.java           — PST split-size validation utilities
  view/
    Step1ImportView.java            — Queue list file importer screen
    Step2ExplorerView.java          — Lazy-loaded folder tree visualizer
    Step3FilterView.java            — Filters configuration panel
    Step4DestinationView.java       — Output folder & format selection screen
    Step5ConversionView.java        — Processing dashboard calling OutputFactory & FilterEngine
    Step6ReportView.java            — Stands-alone conversion report summary
    SettingsDialog.java             — Self-contained modal settings dialog (6 category panels + licensing)
    QuickSettingsDrawer.java        — Quick-access settings sidebar VBox component
src/main/resources/
  style-dark.css                    (Full dark theme stylesheet)
  style-light.css                   (Full light theme stylesheet)
  fonts/
    MaterialIcons-Regular.ttf       (Material Design Icons font asset)
```

## Architecture & Patterns

### Reusable Modular Wizard
- All 6 UI steps, navigation buttons, styling, and controllers are completely decoupled from mailbox formats.
- Future email format converters (MBOX, EML, MSG) can be added simply by:
  1. Creating an adapter class implementing `SourceAdapter` under its own package.
  2. Registering the class under `SourceAdapterFactory` mapping its extension (e.g. `ADAPTER_REGISTRY.put("mbox", "com.pstconverter.mbox.MboxSourceAdapter")`).

### Adapter & Strategy Patterns
- **SourceAdapter**: Adapts format-specific parsing libraries (like `java-libpst`) to supply folder nodes and list messages uniformly.
- **OutputHandler / OutputFactory**: Routes target format writes dynamically at runtime based on the selected format strategy.

### Filter Engine
- The `FilterEngine` reads `filter_settings.properties` saved in the session directory.
- During active conversion execution inside the `Step5ConversionView` thread task, messages are evaluated against active date boundaries, sender lists, keywords, item types, and deduplication keys before export. Unmatched emails are skipped and recorded in the telemetry results.

### UI / Styling Conventions
- **TreeView cell refresh**: TreeView nodes update visually using a `folderTreeView.refresh()` trigger when the conversion thread marks items as done, error, or active.
- **Contrast & Layout Compliance**:
  - Action buttons in the report panel are locked inside a fixed `.report-header-bar` at the top of the BorderPane so they never scroll away.
  - Transparent `.sidebar-card` styles are removed from report panels, relying solely on solid, high-contrast `.report-card` background boxes (`#ffffff` for light mode and `#1e293b` for dark mode).
  - Telemetry statistics value colors are parsed dynamically in Java at runtime (deep-saturated colors for light mode, bright pastel colors for dark mode) to ensure AAA-compliant contrast standards.

## Future Extensibility & Code Quality Guidelines

To ensure the template remains fully format-agnostic and easily adaptable to other mailbox formats (like MBOX, EML, or MSG) or Cloud APIs, all future modifications must follow these architectural rules:

### 1. Maintain Strict Loose Coupling
- **No Direct Imports of Concrete Adapters**: Never import classes from format-specific packages (e.g., `com.pstconverter.pst.*` or `com.pstconverter.mbox.*`) into the `core/` package or the `view/` package.
- **Dynamic Registry**: Always route creation through `SourceAdapterFactory` or `OutputFactory`. If you add a new format, register its class dynamically via reflection mapping inside the factory's static registry (e.g., `SourceAdapterFactory.java`).
- **Encapsulate Format-Specific Operations**: All logic tied to a specific mailbox format (including parsing libraries, DTO parsing, metadata extraction, and local auto-detection) must live inside its format package (e.g., `com.pstconverter.pst`).
- **De-coupled Auto-Detection**: Auto-detection logic must be implemented via the `detectLocalMailboxes()` method on the format's `SourceAdapter` class. Views and coordinator controllers should only trigger detection generically via `SourceAdapterFactory.detectAllLocalMailboxes()`.

### 2. Format-Neutral Naming Conventions
- **Avoid Format-Specific Labels in UI Code**: All controllers, views, tables, and tree variables should use format-neutral terms.
  - *Incorrect*: `isPstNativeFolder`, `getPstFileForNode`, `"PST File (Single)"`, `"Detect Outlook"`
  - *Correct*: `isSourceFolder`, `getSourceFileForNode`, `"Mailbox (Single)"`, `"Auto Detect"`
- This ensures the UI is immediately ready for any format without search-and-replace naming updates.

### 3. Memory-Safe Processing & Streaming
- **Avoid Loading Full Message Lists in Heap**: When extracting messages or metadata from folders, do not collect them in a giant list to return to the caller. Always implement a streaming or iterator pattern (e.g., `streamEmails` and `streamEmailsMetadata`).
- This prevents `OutOfMemoryErrors` when processing massive mailboxes.

### 4. Modern DTO Immutable Patterns
- **Leverage Immutable Records**: For new data carriers, models, or payloads, use Java 21 `record` declarations instead of standard POJOs. This enforces data immutability, thread visibility, and cuts down boilerplates.

## Diagnostic Logging (Testing Mode)

The suite includes `DiagnosticLogger` — a dedicated, toggleable report writer that captures every significant event during a conversion session. It is the **primary tool for debugging conversion issues**, and it is entirely separate from the in-app `TextArea` log shown to the user.

### Location & Toggle

- **Report file**: `~/Documents/Prism Zoho Mail Backup Tool/testing_report.txt` (appended across runs; one session per run separated by `═══` banners).
- **Master toggle**: `com.pstconverter.util.DiagnosticLogger.DIAGNOSTIC_MODE` (default: `true`).
  - Set to `false` to completely silence the logger with zero overhead.
  - Toggle is `volatile` — safe to change at runtime from any thread.
- The logger also mirrors everything to `System.out` so the IDE console and the in-app log both reflect the same events.

### Thread Safety

`DiagnosticLogger` uses an internal `ReentrantLock` to serialise all writes. It is **safe to call from multiple consumer threads simultaneously**. Never add external synchronisation around DiagnosticLogger calls — doing so risks deadlocks.

### Lifecycle — Mandatory Call Sites

Every conversion run MUST follow this exact lifecycle:

| When | What to call | Where |
|------|-------------|-------|
| Before `conversionTask` is created | `DiagnosticLogger.init(sessionLabel)` | `Step5ConversionView.launchConversionTask()` |
| Background task starts (inside `call()`) | `DiagnosticLogger.log("Background task thread started...")` | Inside the `Task.call()` body |
| Thread pool configured | `DiagnosticLogger.logConversionStart(...)` | After `threadCount` is set, before producer loop |
| Folder enters ACTIVE state | `DiagnosticLogger.logFolderStart(...)` | Inside the `!activeFolders.contains(key)` guard in the consumer |
| Message succeeds | `DiagnosticLogger.logMessageSuccess(...)` | After `currentItemSuccess = true`, local-output branch |
| Message fails | `DiagnosticLogger.logMessageFailed(...)` | After `failedItems.incrementAndGet()` |
| Message skipped (filter) | `DiagnosticLogger.logFilterReject(...)` | Inside the `else` (filter reject) branch of the skip block |
| Message skipped (trial limit) | `DiagnosticLogger.logTrialLimitSkip(...)` | Inside the `isTrialLimit` branch |
| Message skipped (resume) | `DiagnosticLogger.logResumeSkip(...)` | Immediately after the `isMessageMigrated()` DB check returns `true` |
| Folder processing complete | `DiagnosticLogger.logFolderComplete(...)` | When `currentItemIndex == folderTotal` at end of consumer loop |
| Task succeeded | `DiagnosticLogger.logConversionComplete(...)` then `DiagnosticLogger.close()` | In `conversionTask.setOnSucceeded(...)` |
| Task failed (exception) | `DiagnosticLogger.error(...)` then `DiagnosticLogger.logConversionComplete(...)` then `DiagnosticLogger.close()` | In `conversionTask.setOnFailed(...)` |
| Task cancelled | `DiagnosticLogger.log(...)` then `DiagnosticLogger.close()` | In `conversionTask.setOnCancelled(...)` |

**CRITICAL**: `DiagnosticLogger.close()` MUST be called in ALL three task outcome handlers (`setOnSucceeded`, `setOnFailed`, `setOnCancelled`). If `close()` is not called, the report file handle leaks and the session footer will be missing from `testing_report.txt`.

**CRITICAL**: `DiagnosticLogger.init()` MUST be called BEFORE the `conversionTask = new Task<>()` block. Calling `init()` inside the task body means the session header would be written from the worker thread, racing with the UI thread's `setOnSucceeded` call.

### Wizard Step Transition Logging

All four forward-navigation transitions in `MainController.handleNext()` MUST log to DiagnosticLogger:

| Transition | Method | Data captured |
|-----------|--------|---------------|
| Step 1 → 2 | `DiagnosticLogger.logStep1to2(fileList)` | All imported source files — name, size, format, path, status |
| Step 2 → 3 | `DiagnosticLogger.logStep2to3(checkedItems, totalFolders)` | Selected folders vs. total folders analysed |
| Step 3 → 4 | `DiagnosticLogger.logStep3to4(filterProps)` | Every active filter key=value property |
| Step 4 → 5 | `DiagnosticLogger.logStep4to5(format, path, attach, struct, naming, metaFields)` | Full output configuration including metadata field inclusion flags |

These calls live **alongside** the existing `System.out.println` blocks — do NOT replace them.

### Full API Reference

```java
// Lifecycle
DiagnosticLogger.init(String sessionLabel)               // Open report, write session header
DiagnosticLogger.close()                                 // Write session footer, flush, close file

// Wizard transitions
DiagnosticLogger.logStep1to2(List<SourceFileModel>)      // Step 1→2: source files imported
DiagnosticLogger.logStep2to3(List<TreeItem<String>>, int totalFolders)  // Step 2→3: folder selection
DiagnosticLogger.logStep3to4(Properties filterProps)     // Step 3→4: active filters
DiagnosticLogger.logStep4to5(format, outputPath, attachHandling, exportStructure, naming, Map<String,String> metaFields)

// Conversion session
DiagnosticLogger.logConversionStart(isResume, totalFolders, totalFiles, totalItems, format, threadCount, isMonolithic)
DiagnosticLogger.logFolderStart(folderKey, estimatedItems, fileIndex, totalFiles)
DiagnosticLogger.logFolderComplete(folderKey, totalProcessed, successCount, skippedCount, failedCount, status, elapsedMs)
DiagnosticLogger.logConversionComplete(totalProcessed, successCount, skippedCount, failedCount, stopped, elapsedMs)

// Per-message outcomes
DiagnosticLogger.logMessageSuccess(msgId, subject, from, date, folderKey, durationMs)
DiagnosticLogger.logMessageFailed(msgId, subject, folderKey, durationMs, reason, Throwable ex)
DiagnosticLogger.logMessageSkipped(msgId, subject, folderKey, durationMs, skipReason)  // generic
DiagnosticLogger.logFilterReject(msgId, subject, folderKey, durationMs, reason)         // filter rejection
DiagnosticLogger.logTrialLimitSkip(msgId, subject, folderKey, durationMs)               // trial cap
DiagnosticLogger.logResumeSkip(msgId, subject, folderKey, durationMs)                   // already migrated

// Cloud uploads
DiagnosticLogger.logCloudUploadAttempt(msgId, subject, format, attempt, maxAttempts, success, durationMs, errorMsg)

// Session DB events
DiagnosticLogger.logSessionSaved(sourceFilePath, format, destinationPath, totalFolders, totalMessages)
DiagnosticLogger.logSessionCleared(sourceFilePath, format, destinationPath)

// General purpose
DiagnosticLogger.log(String message)                     // INFO level
DiagnosticLogger.warn(String message)                    // WARN level
DiagnosticLogger.error(String message, Throwable ex)     // ERROR + compact stack trace (15 frames max)
```

### Log Line Format

All log lines follow a consistent prefix pattern for easy `grep`:

```
[SUCCESS] [yyyy-MM-dd HH:mm:ss.SSS] ID=<msgId>  | Duration: <ms> ms | Subject: "..." | ...
[FAILED ] [yyyy-MM-dd HH:mm:ss.SSS] ID=<msgId>  | Duration: <ms> ms | Reason: ... | ...
[SKIP   ] [yyyy-MM-dd HH:mm:ss.SSS] FILTER — <reason> | Eval: <ms> ms | ...
[SKIP   ] [yyyy-MM-dd HH:mm:ss.SSS] RESUME — Already migrated in previous session | ...
[SKIP   ] [yyyy-MM-dd HH:mm:ss.SSS] TRIAL LIMIT — Max 25 items/folder reached | ...
[CLOUD-OK  ] [yyyy-MM-dd HH:mm:ss.SSS] Attempt 1/3 | Format: Office 365 | ...
[CLOUD-FAIL] [yyyy-MM-dd HH:mm:ss.SSS] Attempt 2/3 | Format: Gmail | Error: ... | ...
[SESSION] [yyyy-MM-dd HH:mm:ss.SSS] IN_PROGRESS session saved | ...
[INFO   ] [yyyy-MM-dd HH:mm:ss.SSS] <free text>
[WARN   ] [yyyy-MM-dd HH:mm:ss.SSS] <free text>
[ERROR  ] [yyyy-MM-dd HH:mm:ss.SSS] <free text + stack trace>
```

### What MUST NOT Be Done

- **Do NOT call `DiagnosticLogger.init()` more than once per conversion run** without calling `close()` first — the report file will have interleaved sessions.
- **Do NOT suppress or catch exceptions inside DiagnosticLogger calls** — if the logger fails (e.g. disk full), it prints to `System.err` and silently continues; the conversion must not be interrupted.
- **Do NOT add `synchronized` blocks around DiagnosticLogger calls** — the internal lock already handles concurrency.
- **Do NOT write new per-message log statements using only `System.out.println`** — all per-message outcomes must go through `DiagnosticLogger` so they appear in `testing_report.txt`. Raw `System.out` is acceptable for coarse-grained task lifecycle events but not for individual message traces.
- **Do NOT remove the `DiagnosticLogger.close()` from any of the three task outcome handlers** — all three (`setOnSucceeded`, `setOnFailed`, `setOnCancelled`) must close the logger.

---

## Resume / Skip Feature — Architecture & Invariants

This section documents the **interrupted migration resume system** (also called the "Skip" or "Resume Center" feature). Any future code change that touches conversion flow, destination paths, filter settings, or the database schema MUST follow these rules to keep the feature working correctly.

### How It Works (End-to-End Flow)

1. **Conversion starts** (`Step5ConversionView.launchConversionTask()`):
   - Immediately before the background `Task` begins processing, `saveInProgressSessionsForFiles()` is called.
   - This writes one row per unique source file into the `migration_sessions` SQLite table with `status = 'IN_PROGRESS'`.
   - The row stores: `source_file_path`, `format`, `destination_path`, `export_structure`, `attachment_handling`, `naming_convention`, `filter_settings` (serialized Properties), `output_properties` (serialized Properties), `total_folders`, `total_messages`, `selected_folders` (comma-separated folder key list).

2. **Per-message tracking** (`Step5ConversionView` consumer threads):
   - Every successfully exported message is recorded via `SettingsManager.recordMessageMigration(sourceFilePath, format, destinationPath, messageIdentifier, "SUCCESS")`.
   - This writes to the `migration_progress` table keyed on `(source_file_path, format, destination_path, message_identifier)`.

3. **User stops conversion** (`confirmStop()` → `onConversionFinished(stopRequested=true)`):
   - The `migration_sessions` row is **kept** as `IN_PROGRESS` with updated `total_messages` count.
   - The `migration_progress` rows for already-exported messages are **kept**.
   - Result: On next app start, the Resume Center in Step 1 shows a card for this file.

4. **Conversion completes fully** (`onConversionFinished(stopRequested=false)`):
   - `SettingsManager.clearMigrationProgressForFile()` is called for every source file, which **deletes** both the `migration_sessions` row and all `migration_progress` rows for that `(source, format, destination)` triplet.
   - Result: No stale "incomplete" cards appear for finished jobs.

5. **Resume Center (Step 1 Import View)**:
   - `Step1ImportView.refreshIncompleteMigrations()` queries `getAllMigrationSessions()` and shows cards only for sessions with `status = 'IN_PROGRESS'`.
   - Each card shows: file name, format badge, source path, progress (X/Y messages converted), destination path, and interrupt timestamp.
   - Each card has a **Resume** button and a **Delete** button.

6. **User clicks Resume** (`MainController.resumeMigrationSession(session)`):
   - Clears the file list, adds the source file.
   - Calls `Step2ExplorerView.startAnalysis()`, then restores selected folders via `selectFoldersByKeys()`.
   - Deserializes and restores filter settings (Step 3) via `loadFilterSettings()`.
   - Restores destination settings (Step 4) via `loadDestinationSettings()`.
   - Navigates directly to Step 5 with `setShouldAutoResume(true)`.
   - In `prepareConversionView()`, the resume checkbox is pre-selected, and the user sees a "Found N previously migrated items" status.

7. **Resuming conversion** (skip logic in consumer thread, `isResume = true`):
   - For each message, before exporting, `SettingsManager.isMessageMigrated()` checks if the `message_identifier` already has a `'SUCCESS'` record for that `(source, format, destination)` triplet.
   - If already migrated → message is counted as **skipped** (`skippedItems++`), NOT re-exported.
   - If not yet migrated → proceeds to export normally.

### Critical Database Schema Rules

- **`migration_sessions` Primary Key**: `(source_file_path, format, destination_path)` — This 3-part composite key is the session identity. All lookups MUST use all three fields together.
- **`migration_progress` Primary Key**: `(source_file_path, format, destination_path, message_identifier)` — The `message_identifier` is sourced from `MailMessage.getUniqueIdentifier()`. It must be stable and consistent across runs.
- **Schema version**: Managed via `settings.db_schema_version`. If you alter table schemas, increment the version and add migration DDL in `SettingsManager`'s static initializer.
- **Never change the Primary Key columns** of these tables without migrating all existing data, or the resume lookups will silently fail.

### Rules for Future Code Changes

#### If you change `Step5ConversionView`:
- **MUST call `saveInProgressSessionsForFiles()`** at the start of `launchConversionTask()`, before submitting work to the executor. If this call is removed or moved after the task starts, the Resume Center will show no cards.
- **MUST update session status on finish** in `onConversionFinished()`. The two branches (`stopRequested` vs completed) MUST be maintained:
  - Stopped → keep `IN_PROGRESS` session
  - Completed → call `clearMigrationProgressForFile()` to delete session
- **MUST call `SettingsManager.recordMessageMigration()`** after every successfully exported message (inside the consumer thread, after `currentItemSuccess = true`).
- **MUST call `SettingsManager.isMessageMigrated()`** at the top of the consumer processing loop when `isResume = true` to perform the skip check.
- **MUST pass all three fields** (`sourceFilePath`, `format`, `resolvedOutputPath`) consistently to every `SettingsManager` call. If the output path changes mid-run, the resume lookup will break.

#### If you change `Step4DestinationView` (destination path logic):
- The `resolvedOutputPath` stored in `Step5ConversionView` and the `destination_path` stored in `migration_sessions` MUST be the same string value.
- If you add a new path-building strategy (e.g., sub-folder naming), make sure `getOutputPath()` is updated consistently and the new path is what gets stored in the DB.
- **Resume match check**: When checking for a match (before offering to resume), the current destination path must exactly equal the stored `session.destinationPath()`. String comparison is case-sensitive on macOS/Linux.

#### If you change `Step3FilterView` (filter settings):
- Filter properties are serialized as a newline-separated `key=value` string via `serializePropertiesToString()` in Step5 before being stored in `migration_sessions.filter_settings`.
- Deserialization in `MainController.resumeMigrationSession()` splits on `\n` and `=`. Do NOT use `=` or `\n` inside property values — escape them if needed.
- If you rename any filter property key in `Step3FilterView`, update `loadFilterSettings()` as well to handle the old key for backward compatibility with existing DB records.

#### If you change `SettingsManager`:
- **Define Settings Key Constants**: Always define new settings keys as `public static final String KEY_...` constants at the top of `SettingsManager` instead of using hardcoded string keys in views or controllers.
- `clearMigrationProgressForFile(sourceFilePath, format, destinationPath)` deletes BOTH the `migration_progress` rows AND the `migration_sessions` row for that triplet. This is intentional — do not split these into separate calls without updating all callers.
- `getMigratedCountForFile()` counts `SUCCESS` rows in `migration_progress`. It is used in `prepareConversionView()` to detect resumable progress and show the resume checkbox. Keep its signature stable.
- `getAllMigrationSessions()` is the sole data source for the Resume Center. It returns ALL sessions regardless of status — the caller (`refreshIncompleteMigrations`) filters for `IN_PROGRESS`.
- `clearAllMigrationState()` (triggered by the Settings panel "Reset Saved State" button) must always clear BOTH tables.

#### If you add new output format categories (e.g., new cloud providers):
- For cloud formats, `resolvedOutputPath` is `null`. The session's `destination_path` is stored as an empty string `""`.
- `isMessageMigrated()` and `recordMessageMigration()` both handle an empty `destinationPath` — this is correct behavior for cloud sessions.
- The resume check in `prepareConversionView()` uses `outPath = resolvedOutputPath != null ? resolvedOutputPath : ""`, which correctly handles cloud vs. local.

### What "Match" Means for Resume

A session is only offered to the user as resumable (and the Resume button works) if the **current wizard settings match the stored session exactly** across:
- Source file path (exact absolute path match)
- Output format (case-sensitive)
- Destination path (exact string match, or `""` for cloud)

If the user changes the output folder or format between runs, a new session will be created (old one stays as-is). Do NOT auto-resume if these three fields don't match — it would write to a different destination while skipping messages that were actually never written there.

### Resume Center Card Display — Required Data Fields

Each card in `Step1ImportView.refreshIncompleteMigrations()` requires the following from `MigrationSession`:
- `sourceFilePath()` → file name and size display
- `format()` → format badge
- `destinationPath()` → "Destination:" label
- `totalMessages()` → denominator in progress (X/Y)
- `timestamp()` → "Interrupted on:" label
- Migrated count → fetched via `getMigratedCountForFile(sourceFilePath, format, destinationPath)`

If any of these fields are null or empty, the card may display `null` values. Always ensure `saveMigrationSession()` is called with non-null values for all these fields.

---

## Code Commenting & Logging Rules

### 1. Document Skip/Resume Logic Thoroughly

Any code section that touches resume/skip logic — including calls to `isMessageMigrated()`, `recordMessageMigration()`, `saveInProgressSessionsForFiles()`, `loadDestinationSettings()`, and `loadFilterSettings()` — **MUST have detailed inline comments** explaining:
- Why the check is performed at this exact point in the flow.
- What happens if the condition is true vs. false.
- Which database table is being read/written.

Avoid vague comments like `// skip if migrated`. Write: `// Resume skip: check migration_progress for this (source, format, dest) triplet before export to avoid duplicate output`.

### 2. Use DiagnosticLogger for All Per-Message Tracing

- Every per-message outcome (success, failure, skip) **MUST be logged via `DiagnosticLogger`**, not just via `System.out.println`. The diagnostic file (`testing_report.txt`) is the authoritative trace for debugging.
- The `System.out.println` calls for per-message events should be kept as secondary mirrors but DiagnosticLogger is the primary record.
- The following are MANDATORY per-message log calls (see Diagnostic Logging section above for the full API):
  - `logMessageSuccess()` — after every successful local export
  - `logMessageFailed()` — after every export failure, pass the exception if available
  - `logFilterReject()` — for every FilterEngine rejection, pass the exact rejection reason string
  - `logTrialLimitSkip()` — for every trial cap skip (25 items/folder)
  - `logResumeSkip()` — for every already-migrated skip (log the DB lookup duration)
  - `logCloudUploadAttempt()` — for every cloud upload attempt (both success and failure)

### 3. Log Every Retry, Backoff, and Network Event

- Cloud upload retries: log each attempt via `DiagnosticLogger.logCloudUploadAttempt()` with `success=false` and the error message.
- Throttling backoffs: log the delay duration before sleeping via `DiagnosticLogger.log()`.
- Internet disconnection / reconnection events: log via `DiagnosticLogger.warn()` and `DiagnosticLogger.log()` respectively.
- Cloud adapter connection failures: log via `DiagnosticLogger.error()` with the exception.

### 4. Folder-Level Logging

- When a folder first becomes ACTIVE: call `DiagnosticLogger.logFolderStart()` immediately inside the `!activeFolders.contains(key)` guard.
- When all items in a folder have been processed: call `DiagnosticLogger.logFolderComplete()` with final telemetry (total, success, skipped, failed, status, elapsed ms).

### 5. Session DB Events Must Be Logged

- After every `SettingsManager.saveMigrationSession()` call: call `DiagnosticLogger.logSessionSaved()` with the same parameters.
- After every `SettingsManager.clearMigrationProgressForFile()` call: call `DiagnosticLogger.logSessionCleared()` with the same parameters.

### 6. Granular Timing Is Mandatory

- All per-message log calls MUST include a `durationMs` value measured from `System.nanoTime()` at the start of message processing to `System.nanoTime()` immediately after the outcome is determined.
- Folder elapsed time in `logFolderComplete()` must use the real wall-clock elapsed ms — not an estimate.
- The throughput rate (items/sec) is computed automatically by `logConversionComplete()` from the elapsed ms and total count.

### 7. Exception Logging

- Never silently swallow exceptions inside the consumer thread. If an exception is caught:
  1. Call `DiagnosticLogger.logMessageFailed()` passing the exception as the last argument — the logger will write a compact stack trace (up to 15 frames) to `testing_report.txt`.
  2. Call `System.err.println()` with a brief description for the IDE console.
  3. Record the message as failed via `failedItems.incrementAndGet()`.
- For task-level exceptions (`setOnFailed`): call `DiagnosticLogger.error()` with the full `Throwable`, then call `logConversionComplete()` and `close()`.

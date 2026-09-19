package com.pstconverter.util;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class SettingsManager {
    // Settings Keys
    public static final String KEY_AUTO_CHECK_UPDATES = "auto_check_updates";
    public static final String KEY_IGNORE_EMPTY_FOLDERS = "ignore_empty_folders";
    public static final String KEY_DEEP_ATTACHMENT_SCAN = "deep_attachment_scan";
    public static final String KEY_LAST_FILE_DIRECTORY = "last_file_directory";
    public static final String KEY_LAST_FOLDER_DIRECTORY = "last_folder_directory";
    public static final String KEY_OUTPUT_FOLDER_TEMPLATE = "output_folder_template";
    public static final String KEY_PDF_LAYOUT_TEMPLATE = "pdf_layout_template";
    public static final String KEY_EML_NAMING_CONVENTION = "eml_naming_convention";
    public static final String KEY_PDF_ATTACHMENT_MODE = "pdf_attachment_mode";
    public static final String KEY_SPLIT_PST_SIZE = "split_pst_size";
    public static final String KEY_THREAD_COUNT = "thread_count";
    public static final String KEY_LOG_LEVEL = "log_level";
    public static final String KEY_FALLBACK_ENCODING = "fallback_encoding";
    public static final String KEY_DEFAULT_EXPORT_PATH = "default_export_path";
    public static final String KEY_CUSTOM_DESTINATION_PARENT = "custom_destination_parent";
    public static final String KEY_CUSTOM_EXPORT_FOLDER_NAME = "custom_export_folder_name";
    public static final String KEY_UI_THEME = "ui_theme";

    // Export Fields Settings Keys
    public static final String KEY_EXPORT_FIELD_SUBJECT = "export_field_subject";
    public static final String KEY_EXPORT_FIELD_FROM = "export_field_from";
    public static final String KEY_EXPORT_FIELD_TO = "export_field_to";
    public static final String KEY_EXPORT_FIELD_DATE = "export_field_date";
    public static final String KEY_EXPORT_FIELD_CCBCC = "export_field_ccbcc";
    public static final String KEY_EXPORT_FIELD_BODY = "export_field_body";

    private static String dbUrl;
    private static final java.util.Map<String, String> settingsCache = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        try {
            // Determine Roaming AppData path for Windows, fallback to home directory on other systems
            String appData = System.getenv("APPDATA");
            File appDir;
            if (appData != null) {
                appDir = new File(appData, com.pstconverter.config.BrandConfig.SETTINGS_DB_DIR);
            } else {
                appDir = new File(System.getProperty("user.home"), com.pstconverter.config.BrandConfig.HIDDEN_SETTINGS_DB_DIR);
            }

            // Create directories if they do not exist
            if (!appDir.exists()) {
                appDir.mkdirs();
            }

            File dbFile = new File(appDir, "settings.db");
            dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath() + "?busy_timeout=5000";

            // Load SQLite JDBC Driver
            Class.forName("org.sqlite.JDBC");

            // Initialize database schema
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT);");
                
                String schemaVersion = "1";
                try (ResultSet rs = stmt.executeQuery("SELECT value FROM settings WHERE key = 'db_schema_version';")) {
                    if (rs.next()) {
                        schemaVersion = rs.getString("value");
                    }
                } catch (Exception ignored) {}

                if ("1".equals(schemaVersion)) {
                    stmt.execute("DROP TABLE IF EXISTS migration_progress;");
                    stmt.execute("DROP TABLE IF EXISTS migration_sessions;");
                    stmt.execute("INSERT OR REPLACE INTO settings (key, value) VALUES ('db_schema_version', '3');");
                    schemaVersion = "3";
                } else if ("2".equals(schemaVersion)) {
                    try {
                        stmt.execute("ALTER TABLE migration_progress ADD COLUMN folder_key TEXT;");
                    } catch (Exception ex) {
                        System.err.println("Migration column already exists or alter failed: " + ex.getMessage());
                    }
                    stmt.execute("INSERT OR REPLACE INTO settings (key, value) VALUES ('db_schema_version', '3');");
                    schemaVersion = "3";
                }

                stmt.execute("CREATE TABLE IF NOT EXISTS saved_accounts (" +
                        "email TEXT PRIMARY KEY, " +
                        "format TEXT, " +
                        "auth_mode TEXT, " +
                        "host TEXT, " +
                        "port INTEGER, " +
                        "ssl INTEGER, " +
                        "password TEXT" +
                        ");");
                stmt.execute("CREATE TABLE IF NOT EXISTS migration_progress (" +
                        "source_file_path TEXT, " +
                        "format TEXT, " +
                        "destination_path TEXT, " +
                        "folder_key TEXT, " +
                        "message_identifier TEXT, " +
                        "status TEXT, " +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                        "PRIMARY KEY (source_file_path, format, destination_path, message_identifier)" +
                        ");");
                stmt.execute("CREATE TABLE IF NOT EXISTS migration_sessions (" +
                        "source_file_path TEXT, " +
                        "destination_path TEXT, " +
                        "format TEXT, " +
                        "export_structure TEXT, " +
                        "attachment_handling TEXT, " +
                        "naming_convention TEXT, " +
                        "filter_settings TEXT, " +
                        "output_properties TEXT, " +
                        "total_folders INTEGER, " +
                        "total_messages INTEGER, " +
                        "status TEXT, " +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                        "selected_folders TEXT, " +
                        "PRIMARY KEY (source_file_path, format, destination_path)" +
                        ");");
                stmt.execute("CREATE TABLE IF NOT EXISTS filter_presets (" +
                             "name TEXT PRIMARY KEY, " +
                             "settings TEXT" +
                             ");");
                stmt.execute("CREATE TABLE IF NOT EXISTS google_oauth_accounts (" +
                        "email TEXT PRIMARY KEY, " +
                        "refresh_token TEXT, " +
                        "access_token TEXT, " +
                        "token_expiry INTEGER, " +
                        "last_connected DATETIME DEFAULT CURRENT_TIMESTAMP" +
                        ");");
                stmt.execute("CREATE TABLE IF NOT EXISTS imap_source_accounts (" +
                        "email TEXT PRIMARY KEY, " +
                        "host TEXT, " +
                        "port INTEGER, " +
                        "ssl INTEGER, " +
                        "password TEXT, " +
                        "last_connected DATETIME DEFAULT CURRENT_TIMESTAMP" +
                        ");");
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize Settings database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Connection getConnection() throws Exception {
        return DriverManager.getConnection(dbUrl);
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run(Connection conn) throws Exception;
    }

    private static synchronized void executeWriteWithRetry(SqlRunnable runnable, String opName) {
        int maxAttempts = 3;
        int delayMs = 100;
        Exception lastEx = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try (Connection conn = getConnection()) {
                runnable.run(conn);
                return; // Success!
            } catch (Exception e) {
                lastEx = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("BUSY") || msg.contains("locked") || e instanceof org.sqlite.SQLiteException) {
                    System.err.println("[WARN] SQLite write busy (" + opName + "). Attempt " + attempt + "/" + maxAttempts + ". Retrying in " + delayMs + "ms...");
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    delayMs *= 2;
                } else {
                    // Non-locking exception (e.g. SQL syntax, duplicate key), fail immediately
                    break;
                }
            }
        }
        System.err.println("[ERROR] SQLite write failed after " + maxAttempts + " attempts (" + opName + "): " + (lastEx != null ? lastEx.getMessage() : ""));
        if (lastEx != null) {
            lastEx.printStackTrace();
        }
    }

    /**
     * Retrieve a setting value by key, returning the defaultValue if not found or on error.
     */
    public static String getSetting(String key, String defaultValue) {
        String cached = settingsCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (dbUrl == null) return defaultValue;
        String sql = "SELECT value FROM settings WHERE key = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString("value");
                    String res = val != null ? val : defaultValue;
                    settingsCache.put(key, res);
                    return res;
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading setting " + key + ": " + e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Save/upsert a setting key-value pair.
     */
    public static synchronized void saveSetting(String key, String value) {
        if (value == null) {
            settingsCache.remove(key);
        } else {
            settingsCache.put(key, value);
        }
        if (dbUrl == null) return;
        executeWriteWithRetry(conn -> {
            String sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?);";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, key);
                pstmt.setString(2, value);
                pstmt.executeUpdate();
            }
        }, "saveSetting");
    }

    public record SavedAccount(
        String email,
        String format,
        String authMode,
        String host,
        int port,
        boolean ssl,
        String password
    ) {}

    public static java.util.List<SavedAccount> getSavedAccounts() {
        java.util.List<SavedAccount> list = new java.util.ArrayList<>();
        if (dbUrl == null) return list;
        String sql = "SELECT email, format, auth_mode, host, port, ssl, password FROM saved_accounts;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(new SavedAccount(
                    rs.getString("email"),
                    rs.getString("format"),
                    rs.getString("auth_mode"),
                    rs.getString("host"),
                    rs.getInt("port"),
                    rs.getInt("ssl") == 1,
                    rs.getString("password")
                ));
            }
        } catch (Exception e) {
            System.err.println("Error reading saved accounts: " + e.getMessage());
        }
        return list;
    }

    public static synchronized void saveSavedAccount(SavedAccount account) {
        if (dbUrl == null) return;
        executeWriteWithRetry(conn -> {
            String sql = "INSERT OR REPLACE INTO saved_accounts (email, format, auth_mode, host, port, ssl, password) VALUES (?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, account.email());
                pstmt.setString(2, account.format());
                pstmt.setString(3, account.authMode());
                pstmt.setString(4, account.host());
                pstmt.setInt(5, account.port());
                pstmt.setInt(6, account.ssl() ? 1 : 0);
                pstmt.setString(7, account.password());
                pstmt.executeUpdate();
            }
        }, "saveSavedAccount");
    }

    public static synchronized void deleteSavedAccount(String email) {
        if (dbUrl == null) return;
        executeWriteWithRetry(conn -> {
            String sql = "DELETE FROM saved_accounts WHERE email = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, email);
                pstmt.executeUpdate();
            }
        }, "deleteSavedAccount");
    }

    /**
     * Resets all settings except product licensing.
     */
    public static synchronized void resetAllSettings() {
        settingsCache.clear();
        if (dbUrl == null) return;
        executeWriteWithRetry(conn -> {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM settings WHERE key != 'license_key';");
                stmt.executeUpdate("DELETE FROM saved_accounts;");
                stmt.executeUpdate("DELETE FROM migration_progress;");
                stmt.executeUpdate("DELETE FROM migration_sessions;");
                stmt.executeUpdate("DELETE FROM filter_presets;");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }, "resetAllSettings");
    }

    public static synchronized void saveFilterPreset(String name, String settings) {
        if (dbUrl == null || name == null || settings == null) return;
        executeWriteWithRetry(conn -> {
            String sql = "INSERT OR REPLACE INTO filter_presets (name, settings) VALUES (?, ?);";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, settings);
                pstmt.executeUpdate();
            }
        }, "saveFilterPreset");
    }

    public static synchronized void deleteFilterPreset(String name) {
        if (dbUrl == null || name == null) return;
        executeWriteWithRetry(conn -> {
            String sql = "DELETE FROM filter_presets WHERE name = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }
        }, "deleteFilterPreset");
    }

    public static String getFilterPreset(String name) {
        if (dbUrl == null || name == null) return null;
        String sql = "SELECT settings FROM filter_presets WHERE name = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("settings");
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading filter preset " + name + ": " + e.getMessage());
        }
        return null;
    }

    public static java.util.Map<String, String> getAllFilterPresets() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (dbUrl == null) return map;
        String sql = "SELECT name, settings FROM filter_presets ORDER BY name ASC;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("name"), rs.getString("settings"));
            }
        } catch (Exception e) {
            System.err.println("Error reading all filter presets: " + e.getMessage());
        }
        return map;
    }

    public static java.util.List<String> getRecentFiles() {
        String data = getSetting("recent_imported_files", "");
        java.util.List<String> list = new java.util.ArrayList<>();
        if (data == null || data.trim().isEmpty()) {
            return list;
        }
        String[] paths = data.split(",");
        boolean changed = false;
        for (String p : paths) {
            String clean = p.trim();
            if (!clean.isEmpty()) {
                File f = new File(clean);
                if (f.exists() && f.isFile()) {
                    list.add(clean);
                } else {
                    changed = true;
                }
            }
        }
        if (changed) {
            saveRecentFiles(list);
        }
        return list;
    }

    public static void saveRecentFiles(java.util.List<String> list) {
        String joined = String.join(",", list);
        saveSetting("recent_imported_files", joined);
    }

    public static void addRecentFile(String path) {
        if (path == null || path.trim().isEmpty()) return;
        java.util.List<String> list = getRecentFiles();
        list.remove(path);
        list.add(0, path);
        if (list.size() > 5) {
            list = new java.util.ArrayList<>(list.subList(0, 5));
        }
        saveRecentFiles(list);
    }

    public static boolean isMessageMigrated(String sourceFilePath, String format, String destinationPath, String messageIdentifier) {
        if (dbUrl == null || sourceFilePath == null || format == null || destinationPath == null || messageIdentifier == null) return false;
        long startTime = System.nanoTime();
        String sql = "SELECT 1 FROM migration_progress WHERE source_file_path = ? AND format = ? AND destination_path = ? AND message_identifier = ? AND status = 'SUCCESS';";
        boolean result = false;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sourceFilePath);
            pstmt.setString(2, format);
            pstmt.setString(3, destinationPath);
            pstmt.setString(4, messageIdentifier);
            try (ResultSet rs = pstmt.executeQuery()) {
                result = rs.next();
                return result;
            }
        } catch (Exception e) {
            System.err.println("Error checking migration progress: " + e.getMessage());
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] isMessageMigrated | Duration: %.2f ms | MsgID: %s | Result: %b | File: %s",
                durationMs, messageIdentifier, result, new File(sourceFilePath).getName()));
        }
        return false;
    }

    private static final java.util.concurrent.ConcurrentLinkedQueue<MigrationRecord> pendingWrites = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static class MigrationRecord {
        public final String sourceFilePath;
        public final String format;
        public final String destinationPath;
        public final String folderKey;
        public final String messageIdentifier;
        public final String status;

        public MigrationRecord(String sourceFilePath, String format, String destinationPath, String folderKey, String messageIdentifier, String status) {
            this.sourceFilePath = sourceFilePath;
            this.format = format;
            this.destinationPath = destinationPath;
            this.folderKey = folderKey;
            this.messageIdentifier = messageIdentifier;
            this.status = status;
        }
    }

    public static void recordMessageMigration(String sourceFilePath, String format, String destinationPath, String folderKey, String messageIdentifier, String status) {
        if (dbUrl == null || sourceFilePath == null || format == null || destinationPath == null || messageIdentifier == null) return;
        pendingWrites.add(new MigrationRecord(sourceFilePath, format, destinationPath, folderKey, messageIdentifier, status));
        if (pendingWrites.size() >= 100) {
            flushPendingMigrations();
        }
    }

    public static synchronized void flushPendingMigrations() {
        if (pendingWrites.isEmpty()) return;
        long startTime = System.nanoTime();
        java.util.List<MigrationRecord> recordsToFlush = new java.util.ArrayList<>();
        MigrationRecord record;
        while ((record = pendingWrites.poll()) != null) {
            recordsToFlush.add(record);
        }
        if (recordsToFlush.isEmpty()) return;

        try {
            executeWriteWithRetry(conn -> {
                boolean autoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                String sql = "INSERT OR REPLACE INTO migration_progress (source_file_path, format, destination_path, folder_key, message_identifier, status) VALUES (?, ?, ?, ?, ?, ?);";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    for (MigrationRecord rec : recordsToFlush) {
                        pstmt.setString(1, rec.sourceFilePath);
                        pstmt.setString(2, rec.format);
                        pstmt.setString(3, rec.destinationPath);
                        pstmt.setString(4, rec.folderKey);
                        pstmt.setString(5, rec.messageIdentifier);
                        pstmt.setString(6, rec.status);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            }, "batchRecordMessageMigration");
        } catch (Exception e) {
            System.err.println("Failed to batch write migration progress: " + e.getMessage());
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] batchRecordMessageMigration | Flushed %d records | Duration: %.2f ms",
                recordsToFlush.size(), durationMs));
        }
    }

    public static int getMigratedCountForFolder(String sourceFilePath, String format, String destinationPath, String folderKey) {
        if (dbUrl == null || sourceFilePath == null || format == null || destinationPath == null || folderKey == null) return 0;
        long startTime = System.nanoTime();
        String sql = "SELECT COUNT(*) FROM migration_progress WHERE source_file_path = ? AND format = ? AND destination_path = ? AND folder_key = ? AND status = 'SUCCESS';";
        int count = 0;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sourceFilePath);
            pstmt.setString(2, format);
            pstmt.setString(3, destinationPath);
            pstmt.setString(4, folderKey);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                    return count;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting migrated count for folder: " + e.getMessage());
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] getMigratedCountForFolder | Duration: %.2f ms | Folder: %s | Count: %d | File: %s",
                durationMs, folderKey, count, new File(sourceFilePath).getName()));
        }
        return 0;
    }

    public static java.util.Set<String> getMigratedMessageIdsForFile(String sourceFilePath, String format, String destinationPath) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        if (dbUrl == null || sourceFilePath == null || format == null || destinationPath == null) return ids;
        long startTime = System.nanoTime();
        String sql = "SELECT message_identifier FROM migration_progress WHERE source_file_path = ? AND format = ? AND destination_path = ? AND status = 'SUCCESS';";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sourceFilePath);
            pstmt.setString(2, format);
            pstmt.setString(3, destinationPath);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("message_identifier"));
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting migrated message IDs for file: " + e.getMessage());
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] getMigratedMessageIdsForFile | Duration: %.2f ms | Retrieved: %d IDs | File: %s",
                durationMs, ids.size(), new File(sourceFilePath).getName()));
        }
        return ids;
    }

    public static synchronized void clearMigrationProgressForFile(String sourceFilePath, String format, String destinationPath) {
        if (dbUrl == null || sourceFilePath == null || format == null || destinationPath == null) return;
        long startTime = System.nanoTime();
        try {
            executeWriteWithRetry(conn -> {
                conn.setAutoCommit(false);
                try (PreparedStatement pstmt1 = conn.prepareStatement("DELETE FROM migration_progress WHERE source_file_path = ? AND format = ? AND destination_path = ?;");
                     PreparedStatement pstmt2 = conn.prepareStatement("DELETE FROM migration_sessions WHERE source_file_path = ? AND format = ? AND destination_path = ?;")) {
                    pstmt1.setString(1, sourceFilePath);
                    pstmt1.setString(2, format);
                    pstmt1.setString(3, destinationPath);
                    pstmt1.executeUpdate();
                    pstmt2.setString(1, sourceFilePath);
                    pstmt2.setString(2, format);
                    pstmt2.setString(3, destinationPath);
                    pstmt2.executeUpdate();
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
            }, "clearMigrationProgressForFile");
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] clearMigrationProgressForFile | Duration: %.2f ms | File: %s",
                durationMs, new File(sourceFilePath).getName()));
        }
    }

    public static synchronized void completeMigrationSession(String sourceFilePath, String format, String destinationPath) {
        if (dbUrl == null || sourceFilePath == null || format == null || destinationPath == null) return;
        long startTime = System.nanoTime();
        try {
            executeWriteWithRetry(conn -> {
                conn.setAutoCommit(false);
                try (PreparedStatement pstmt1 = conn.prepareStatement("DELETE FROM migration_progress WHERE source_file_path = ? AND format = ? AND destination_path = ?;");
                     PreparedStatement pstmt2 = conn.prepareStatement("UPDATE migration_sessions SET status = 'COMPLETED', timestamp = ? WHERE source_file_path = ? AND format = ? AND destination_path = ?;")) {
                    pstmt1.setString(1, sourceFilePath);
                    pstmt1.setString(2, format);
                    pstmt1.setString(3, destinationPath);
                    pstmt1.executeUpdate();
                    
                    String now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    pstmt2.setString(1, now);
                    pstmt2.setString(2, sourceFilePath);
                    pstmt2.setString(3, format);
                    pstmt2.setString(4, destinationPath);
                    pstmt2.executeUpdate();
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
            }, "completeMigrationSession");
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] completeMigrationSession | Duration: %.2f ms | File: %s",
                durationMs, new File(sourceFilePath).getName()));
        }
    }

    public static int getMigratedCountForFile(String sourceFilePath, String format, String destinationPath) {
        if (dbUrl == null || sourceFilePath == null || format == null || destinationPath == null) return 0;
        long startTime = System.nanoTime();
        String sql = "SELECT COUNT(*) FROM migration_progress WHERE source_file_path = ? AND format = ? AND destination_path = ? AND status = 'SUCCESS';";
        int count = 0;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sourceFilePath);
            pstmt.setString(2, format);
            pstmt.setString(3, destinationPath);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                    return count;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting migrated count: " + e.getMessage());
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] getMigratedCountForFile | Duration: %.2f ms | Count: %d | File: %s",
                durationMs, count, new File(sourceFilePath).getName()));
        }
        return 0;
    }

    public record MigrationSession(
        String sourceFilePath,
        String destinationPath,
        String format,
        String exportStructure,
        String attachmentHandling,
        String namingConvention,
        String filterSettings,
        String outputProperties,
        int totalFolders,
        int totalMessages,
        String status,
        String timestamp,
        String selectedFolders
    ) {}

    public static synchronized void saveMigrationSession(MigrationSession session) {
        if (dbUrl == null || session == null) return;
        long startTime = System.nanoTime();
        // Capture current local time using system timezone (correct for any region)
        String ts = session.timestamp();
        if (ts == null || ts.trim().isEmpty()) {
            ts = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        final String finalTs = ts;
        try {
            executeWriteWithRetry(conn -> {
                String sql = "INSERT OR REPLACE INTO migration_sessions (" +
                             "source_file_path, destination_path, format, export_structure, " +
                             "attachment_handling, naming_convention, filter_settings, " +
                             "output_properties, total_folders, total_messages, status, timestamp, selected_folders) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, session.sourceFilePath());
                    pstmt.setString(2, session.destinationPath());
                    pstmt.setString(3, session.format());
                    pstmt.setString(4, session.exportStructure());
                    pstmt.setString(5, session.attachmentHandling());
                    pstmt.setString(6, session.namingConvention());
                    pstmt.setString(7, session.filterSettings());
                    pstmt.setString(8, session.outputProperties());
                    pstmt.setInt(9, session.totalFolders());
                    pstmt.setInt(10, session.totalMessages());
                    pstmt.setString(11, session.status());
                    pstmt.setString(12, finalTs);
                    pstmt.setString(13, session.selectedFolders());
                    pstmt.executeUpdate();
                }
            }, "saveMigrationSession");
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] saveMigrationSession | Duration: %.2f ms | Status: %s | Folders: %d | Messages: %d | File: %s",
                durationMs, session.status(), session.totalFolders(), session.totalMessages(), new File(session.sourceFilePath()).getName()));
        }
    }

    public static MigrationSession getMigrationSession(String sourceFilePath, String format, String destinationPath) {
        if (dbUrl == null || sourceFilePath == null || format == null || destinationPath == null) return null;
        long startTime = System.nanoTime();
        String sql = "SELECT source_file_path, destination_path, format, export_structure, " +
                     "attachment_handling, naming_convention, filter_settings, " +
                     "output_properties, total_folders, total_messages, status, timestamp, selected_folders " +
                     "FROM migration_sessions WHERE source_file_path = ? AND format = ? AND destination_path = ?;";
        MigrationSession session = null;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sourceFilePath);
            pstmt.setString(2, format);
            pstmt.setString(3, destinationPath);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    session = new MigrationSession(
                        rs.getString("source_file_path"),
                        rs.getString("destination_path"),
                        rs.getString("format"),
                        rs.getString("export_structure"),
                        rs.getString("attachment_handling"),
                        rs.getString("naming_convention"),
                        rs.getString("filter_settings"),
                        rs.getString("output_properties"),
                        rs.getInt("total_folders"),
                        rs.getInt("total_messages"),
                        rs.getString("status"),
                        rs.getString("timestamp"),
                        rs.getString("selected_folders")
                    );
                    return session;
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading migration session: " + e.getMessage());
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] getMigrationSession | Duration: %.2f ms | Found: %b | File: %s",
                durationMs, (session != null), new File(sourceFilePath).getName()));
        }
        return null;
    }

    public static java.util.List<MigrationSession> getAllMigrationSessions() {
        java.util.List<MigrationSession> list = new java.util.ArrayList<>();
        if (dbUrl == null) return list;
        long startTime = System.nanoTime();
        String sql = "SELECT source_file_path, destination_path, format, export_structure, " +
                     "attachment_handling, naming_convention, filter_settings, " +
                     "output_properties, total_folders, total_messages, status, timestamp, selected_folders " +
                     "FROM migration_sessions ORDER BY timestamp DESC;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new MigrationSession(
                    rs.getString("source_file_path"),
                    rs.getString("destination_path"),
                    rs.getString("format"),
                    rs.getString("export_structure"),
                    rs.getString("attachment_handling"),
                    rs.getString("naming_convention"),
                    rs.getString("filter_settings"),
                    rs.getString("output_properties"),
                    rs.getInt("total_folders"),
                    rs.getInt("total_messages"),
                    rs.getString("status"),
                    rs.getString("timestamp"),
                    rs.getString("selected_folders")
                ));
            }
        } catch (Exception e) {
            System.err.println("Error reading all migration sessions: " + e.getMessage());
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] getAllMigrationSessions | Duration: %.2f ms | Retrieved: %d sessions",
                durationMs, list.size()));
        }
        return list;
    }

    public static String getSavedDestinationPath(String sourceFilePath, String format, String destinationPath) {
        MigrationSession session = getMigrationSession(sourceFilePath, format, destinationPath);
        return session != null ? session.destinationPath() : null;
    }

    public static synchronized void deleteMigrationSession(String sourceFilePath, String format, String destinationPath) {
        if (dbUrl == null || sourceFilePath == null || format == null || destinationPath == null) return;
        long startTime = System.nanoTime();
        try {
            executeWriteWithRetry(conn -> {
                String sql = "DELETE FROM migration_sessions WHERE source_file_path = ? AND format = ? AND destination_path = ?;";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, sourceFilePath);
                    pstmt.setString(2, format);
                    pstmt.setString(3, destinationPath);
                    pstmt.executeUpdate();
                }
            }, "deleteMigrationSession");
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] deleteMigrationSession | Duration: %.2f ms | File: %s",
                durationMs, new File(sourceFilePath).getName()));
        }
    }

    public static synchronized void clearAllMigrationState() {
        if (dbUrl == null) return;
        long startTime = System.nanoTime();
        try {
            executeWriteWithRetry(conn -> {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM migration_progress;");
                    stmt.executeUpdate("DELETE FROM migration_sessions;");
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
            }, "clearAllMigrationState");
        } finally {
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
            DiagnosticLogger.log(String.format("[DB-OP] clearAllMigrationState | Duration: %.2f ms", durationMs));
        }
    }

    public record GoogleOAuthAccountRecord(String email, String refreshToken, String accessToken, long tokenExpiry, String lastConnected) {}

    public static synchronized void saveGoogleOAuthAccount(String email, String refreshToken, String accessToken, long tokenExpiry) {
        if (dbUrl == null || email == null || email.trim().isEmpty()) return;
        String cleanEmail = email.trim().toLowerCase();
        try {
            executeWriteWithRetry(conn -> {
                String sql = "INSERT INTO google_oauth_accounts (email, refresh_token, access_token, token_expiry, last_connected) " +
                             "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                             "ON CONFLICT(email) DO UPDATE SET " +
                             "refresh_token = COALESCE(EXCLUDED.refresh_token, google_oauth_accounts.refresh_token), " +
                             "access_token = EXCLUDED.access_token, " +
                             "token_expiry = EXCLUDED.token_expiry, " +
                             "last_connected = CURRENT_TIMESTAMP;";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, cleanEmail);
                    pstmt.setString(2, refreshToken != null ? refreshToken : "");
                    pstmt.setString(3, accessToken != null ? accessToken : "");
                    pstmt.setLong(4, tokenExpiry);
                    pstmt.executeUpdate();
                }
            }, "saveGoogleOAuthAccount");
            DiagnosticLogger.log("[DB-OP] Saved Google OAuth account: " + cleanEmail);
        } catch (Exception ex) {
            System.err.println("Failed to save Google OAuth account to DB: " + ex.getMessage());
        }
    }

    public static synchronized java.util.List<GoogleOAuthAccountRecord> getSavedGoogleOAuthAccounts() {
        java.util.List<GoogleOAuthAccountRecord> list = new java.util.ArrayList<>();
        if (dbUrl == null) return list;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT email, refresh_token, access_token, token_expiry, last_connected FROM google_oauth_accounts ORDER BY last_connected DESC;")) {
            while (rs.next()) {
                list.add(new GoogleOAuthAccountRecord(
                    rs.getString("email"),
                    rs.getString("refresh_token"),
                    rs.getString("access_token"),
                    rs.getLong("token_expiry"),
                    rs.getString("last_connected")
                ));
            }
        } catch (Exception ex) {
            System.err.println("Failed to fetch Google OAuth accounts from DB: " + ex.getMessage());
        }
        return list;
    }

    public static synchronized void deleteGoogleOAuthAccount(String email) {
        if (dbUrl == null || email == null) return;
        String cleanEmail = email.trim().toLowerCase();
        try {
            executeWriteWithRetry(conn -> {
                String sql = "DELETE FROM google_oauth_accounts WHERE LOWER(email) = ?;";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, cleanEmail);
                    pstmt.executeUpdate();
                }
            }, "deleteGoogleOAuthAccount");
            DiagnosticLogger.log("[DB-OP] Deleted Google OAuth account: " + cleanEmail);
        } catch (Exception ex) {
            System.err.println("Failed to delete Google OAuth account from DB: " + ex.getMessage());
        }
    }

    public static synchronized void deleteAllGoogleOAuthAccounts() {
        if (dbUrl == null) return;
        try {
            executeWriteWithRetry(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM google_oauth_accounts;");
                }
            }, "deleteAllGoogleOAuthAccounts");
            DiagnosticLogger.log("[DB-OP] Deleted all Google OAuth accounts.");
        } catch (Exception ex) {
            System.err.println("Failed to delete all Google OAuth accounts from DB: " + ex.getMessage());
        }
    }

    public record ImapAccountRecord(String email, String host, int port, boolean ssl, String password, String lastConnected) {}

    public static synchronized void saveImapSourceAccount(String email, String host, int port, boolean ssl, String password) {
        if (dbUrl == null || email == null || email.trim().isEmpty()) return;
        String cleanEmail = email.trim().toLowerCase();
        try {
            executeWriteWithRetry(conn -> {
                String sql = "INSERT INTO imap_source_accounts (email, host, port, ssl, password, last_connected) " +
                             "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                             "ON CONFLICT(email) DO UPDATE SET " +
                             "host = EXCLUDED.host, " +
                             "port = EXCLUDED.port, " +
                             "ssl = EXCLUDED.ssl, " +
                             "password = EXCLUDED.password, " +
                             "last_connected = CURRENT_TIMESTAMP;";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, cleanEmail);
                    pstmt.setString(2, host != null ? host.trim() : "");
                    pstmt.setInt(3, port);
                    pstmt.setInt(4, ssl ? 1 : 0);
                    pstmt.setString(5, password != null ? password : "");
                    pstmt.executeUpdate();
                }
            }, "saveImapSourceAccount");
            DiagnosticLogger.log("[DB-OP] Saved IMAP source account: " + cleanEmail);
        } catch (Exception ex) {
            System.err.println("Failed to save IMAP source account to DB: " + ex.getMessage());
        }
    }

    public static synchronized java.util.List<ImapAccountRecord> getSavedImapSourceAccounts() {
        java.util.List<ImapAccountRecord> list = new java.util.ArrayList<>();
        if (dbUrl == null) return list;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT email, host, port, ssl, password, last_connected FROM imap_source_accounts ORDER BY last_connected DESC;")) {
            while (rs.next()) {
                list.add(new ImapAccountRecord(
                    rs.getString("email"),
                    rs.getString("host"),
                    rs.getInt("port"),
                    rs.getInt("ssl") == 1,
                    rs.getString("password"),
                    rs.getString("last_connected")
                ));
            }
        } catch (Exception ex) {
            System.err.println("Failed to fetch IMAP source accounts from DB: " + ex.getMessage());
        }
        return list;
    }

    public static synchronized void deleteImapSourceAccount(String email) {
        if (dbUrl == null || email == null) return;
        String cleanEmail = email.trim().toLowerCase();
        try {
            executeWriteWithRetry(conn -> {
                String sql = "DELETE FROM imap_source_accounts WHERE LOWER(email) = ?;";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, cleanEmail);
                    pstmt.executeUpdate();
                }
            }, "deleteImapSourceAccount");
            DiagnosticLogger.log("[DB-OP] Deleted IMAP source account: " + cleanEmail);
        } catch (Exception ex) {
            System.err.println("Failed to delete IMAP source account from DB: " + ex.getMessage());
        }
    }

    public static synchronized void deleteAllImapSourceAccounts() {
        if (dbUrl == null) return;
        try {
            executeWriteWithRetry(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM imap_source_accounts;");
                }
            }, "deleteAllImapSourceAccounts");
            DiagnosticLogger.log("[DB-OP] Deleted all IMAP source accounts.");
        } catch (Exception ex) {
            System.err.println("Failed to delete all IMAP source accounts from DB: " + ex.getMessage());
        }
    }
}

package com.pstconverter.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DiagnosticsExporter {

    private DiagnosticsExporter() {}

    public static File exportDiagnostics(File sessionDir) {
        try {
            File documentsDir = new File(System.getProperty("user.home"), "Documents");
            File toolDir = new File(documentsDir, com.pstconverter.config.BrandConfig.REPORT_DIR_NAME);
            if (!toolDir.exists()) {
                toolDir.mkdirs();
            }
            File zipFile = new File(toolDir, "diagnostics_report.zip");
            
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                // 1. Add System Info
                ZipEntry infoEntry = new ZipEntry("system_info.txt");
                zos.putNextEntry(infoEntry);
                String systemInfo = getSystemInfoString(sessionDir);
                zos.write(systemInfo.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
 
                // 2. Add settings database if it exists
                String appData = System.getenv("APPDATA");
                File appDir = (appData != null) ? 
                        new File(appData, com.pstconverter.config.BrandConfig.SETTINGS_DB_DIR) : 
                        new File(System.getProperty("user.home"), com.pstconverter.config.BrandConfig.HIDDEN_SETTINGS_DB_DIR);
                File dbFile = new File(appDir, "settings.db");
                if (dbFile.exists()) {
                    try {
                        addFileToZip(dbFile, "settings.db", zos);
                    } catch (IOException ex) {
                        System.err.println("Could not zip settings database: " + ex.getMessage());
                    }
                }

                // 3. Add session log files
                if (sessionDir != null && sessionDir.exists()) {
                    File logsDir = new File(sessionDir, "logs");
                    if (logsDir.exists()) {
                        File[] logs = logsDir.listFiles();
                        if (logs != null) {
                            for (File logFile : logs) {
                                if (logFile.isFile()) {
                                    try {
                                        addFileToZip(logFile, "logs/" + logFile.getName(), zos);
                                    } catch (IOException ex) {
                                        System.err.println("Could not zip log file " + logFile.getName() + ": " + ex.getMessage());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return zipFile;
        } catch (Exception e) {
            System.err.println("Failed to export diagnostics ZIP: " + e.getMessage());
            return null;
        }
    }

    private static void addFileToZip(File file, String entryName, ZipOutputStream zos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
        }
    }

    private static String getSystemInfoString(File sessionDir) {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append(com.pstconverter.config.BrandConfig.TOOL_NAME.toUpperCase()).append(" - DIAGNOSTIC SYSTEM INFO\n");
        sb.append("====================================================\n\n");
        sb.append("OS Name:       ").append(System.getProperty("os.name")).append("\n");
        sb.append("OS Version:    ").append(System.getProperty("os.version")).append("\n");
        sb.append("OS Arch:       ").append(System.getProperty("os.arch")).append("\n");
        sb.append("Java Version:  ").append(System.getProperty("java.version")).append("\n");
        sb.append("Java Vendor:   ").append(System.getProperty("java.vendor")).append("\n");
        sb.append("Available Cores: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        sb.append("Max Memory:    ").append(maxMemory / (1024 * 1024)).append(" MB\n");
        sb.append("Total Memory:  ").append(totalMemory / (1024 * 1024)).append(" MB\n");
        sb.append("Free Memory:   ").append(freeMemory / (1024 * 1024)).append(" MB\n");
        if (sessionDir != null) {
            sb.append("Session Dir:   ").append(sessionDir.getAbsolutePath()).append("\n");
        }
        sb.append("Timestamp:     ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date())).append("\n");
        return sb.toString();
    }
}

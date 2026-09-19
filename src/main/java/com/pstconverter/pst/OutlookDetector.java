package com.pstconverter.pst;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OutlookDetector {

    public static List<File> detectPstFiles() {
        List<File> detectedFiles = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows common paths
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null) {
                // Path 1: AppData\Local\Microsoft\Outlook
                File appDataPath = new File(userProfile, "AppData\\Local\\Microsoft\\Outlook");
                scanDirectory(appDataPath, detectedFiles);

                // Path 2: Documents\Outlook Files
                File docsPath = new File(userProfile, "Documents\\Outlook Files");
                scanDirectory(docsPath, detectedFiles);
            }
        } else if (os.contains("mac")) {
            // macOS common paths
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                // Path 1: Documents/Outlook Files
                File docsPath = new File(userHome, "Documents/Outlook Files");
                scanDirectory(docsPath, detectedFiles);

                // Path 2: Group Containers for Outlook profiles
                File groupContainers = new File(userHome, "Library/Group Containers/UBF8T346G9.Office/Outlook/Outlook 15 Profiles");
                if (groupContainers.exists()) {
                    scanDirectoryRecursively(groupContainers, detectedFiles, 3); // Max depth 3 to avoid deep traversal
                }

                // Path 3: Application Support
                File appSupport = new File(userHome, "Library/Application Support/Microsoft/Outlook");
                scanDirectory(appSupport, detectedFiles);
            }
        } else {
            // Generic Unix fallback - check Documents folder
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                File docsPath = new File(userHome, "Documents");
                scanDirectory(docsPath, detectedFiles);
            }
        }
        return detectedFiles;
    }

    private static void scanDirectory(File directory, List<File> detectedFiles) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".pst")) {
                        detectedFiles.add(file);
                    }
                }
            }
        }
    }

    private static void scanDirectoryRecursively(File directory, List<File> detectedFiles, int maxDepth) {
        if (maxDepth < 0) return;
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        scanDirectoryRecursively(file, detectedFiles, maxDepth - 1);
                    } else if (file.isFile() && file.getName().toLowerCase().endsWith(".pst")) {
                        detectedFiles.add(file);
                    }
                }
            }
        }
    }
}

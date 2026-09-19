package com.pstconverter.util;

import com.pstconverter.util.SettingsManager;

/**
 * Centralized utility for resolving the active UI theme (dark/light)
 * and providing the correct CSS stylesheet path.
 * 
 * All theme logic is static — no instance state needed since everything
 * is backed by SettingsManager.
 */
public final class ThemeManager {

    private ThemeManager() {} // Static utility, no instances

    /**
     * Returns true if the current effective theme is dark mode.
     */
    public static boolean isDarkMode() {
        String activeTheme = SettingsManager.getSetting("ui_theme", "System Default");
        if ("Dark Mode".equalsIgnoreCase(activeTheme)) {
            return true;
        }
        if ("Light Mode".equalsIgnoreCase(activeTheme)) {
            return false;
        }
        return isSystemDarkMode();
    }

    /**
     * Returns the full external-form URL for the active theme CSS file.
     */
    public static String getActiveThemeStylesheet() {
        String activeTheme = SettingsManager.getSetting("ui_theme", "System Default");
        String cssFile = "/style-dark.css";
        if ("Light Mode".equalsIgnoreCase(activeTheme)) {
            cssFile = "/style-light.css";
        } else if ("System Default".equalsIgnoreCase(activeTheme)) {
            cssFile = isSystemDarkMode() ? "/style-dark.css" : "/style-light.css";
        }
        try {
            return ThemeManager.class.getResource(cssFile).toExternalForm();
        } catch (Exception e) {
            System.err.println("Failed to resolve theme stylesheet: " + cssFile + " - " + e.getMessage());
            try {
                return ThemeManager.class.getResource("/style-dark.css").toExternalForm();
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    /**
     * Applies the given theme name to the provided scene, swapping stylesheets.
     */
    public static void applyTheme(javafx.scene.Scene scene, String themeName) {
        if (scene == null) return;
        scene.getStylesheets().clear();
        String cssFile = "/style-dark.css";
        if ("Light Mode".equalsIgnoreCase(themeName)) {
            cssFile = "/style-light.css";
        } else if ("System Default".equalsIgnoreCase(themeName)) {
            cssFile = isSystemDarkMode() ? "/style-dark.css" : "/style-light.css";
        }

        try {
            String cssPath = ThemeManager.class.getResource(cssFile).toExternalForm();
            scene.getStylesheets().add(cssPath);
        } catch (Exception e) {
            System.err.println("Failed to load stylesheet " + cssFile + ": " + e.getMessage());
        }
    }

    /**
     * Detects whether the OS-level UI theme is set to dark mode.
     */
    private static boolean isSystemDarkMode() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"defaults", "read", "-g", "AppleInterfaceStyle"});
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && line.trim().equalsIgnoreCase("Dark")) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        } else if (os.contains("win")) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme"});
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("AppsUseLightTheme") && line.contains("REG_DWORD") && line.contains("0x0")) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }
}

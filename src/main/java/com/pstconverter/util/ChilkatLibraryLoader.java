package com.pstconverter.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts and loads the Chilkat native library from the JAR's resource/ directory
 * at runtime, so the fat JAR is self-contained.
 *
 * Windows: chilkat.dll
 * macOS:   libchilkat.jnilib
 * Linux:   libchilkat.so
 */
public final class ChilkatLibraryLoader {

    private static volatile boolean loaded = false;
    private static volatile boolean unlocked = false;

    private ChilkatLibraryLoader() {}

    public static synchronized void loadAndUnlock() {
        loadChilkatLibrary();
        if (unlocked) return;

        try {
            com.chilkatsoft.CkGlobal glob = new com.chilkatsoft.CkGlobal();
            try {
                boolean success = glob.UnlockBundle("XYG34P.CBX082025_SMAOT5VDmR6I");
                if (success) {
                    System.out.println("[INFO] Chilkat Native Components Unlocked Successfully via Lazy Loading.");
                    unlocked = true;
                } else {
                    System.err.println("[ERROR] Failed to unlock Chilkat components. Full log:");
                    System.err.println(glob.lastErrorText());
                }
            } finally {
                glob.delete();
            }
        } catch (Throwable t) {
            System.err.println("[ERROR] Could not instantiate CkGlobal to unlock: " + t.getMessage());
        }
    }

    public static synchronized void loadChilkatLibrary() {
        if (loaded) return;

        String os = System.getProperty("os.name").toLowerCase();
        String resourcePath;
        if (os.contains("windows") || os.contains("win")) {
            resourcePath = "/chilkat.dll";
        } else if (os.contains("mac")) {
            resourcePath = "/libchilkat.jnilib";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            resourcePath = "/libchilkat.so";
        } else {
            throw new UnsupportedOperationException("Unsupported OS for Chilkat: " + os);
        }

        loadLibraryFromResource(resourcePath);
        loaded = true;
    }

    private static void loadLibraryFromResource(String resourcePath) {
        try (InputStream in = ChilkatLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Could not find resource: " + resourcePath);
            }

            String fileSuffix = resourcePath.substring(resourcePath.lastIndexOf('.'));
            File tempFile = File.createTempFile("chilkat_", fileSuffix);
            tempFile.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            System.out.println("[INFO] Dynamic loading Chilkat native library from temp file: " + tempFile.getAbsolutePath());
            System.load(tempFile.getAbsolutePath());

        } catch (IOException | UnsatisfiedLinkError e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load Chilkat native library: " + resourcePath, e);
        }
    }
}

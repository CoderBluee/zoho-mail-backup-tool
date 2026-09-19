package com.pstconverter.core.auth;

import com.pstconverter.util.SettingsManager;

/**
 * Thread-safe utility for managing and caching OAuth 2.0 access and refresh tokens.
 * Persists token data securely using SettingsManager.
 */
public class TokenManager {

    /**
     * Saves active tokens and corresponding expiry timestamp for an account.
     */
    public static synchronized void saveTokens(String account, String accessToken, String refreshToken, long expiryTimeMs) {
        if (account == null || account.trim().isEmpty()) return;
        String prefix = "oauth." + account.trim().toLowerCase() + ".";
        SettingsManager.saveSetting(prefix + "accessToken", accessToken != null ? accessToken : "");
        if (refreshToken != null && !refreshToken.isEmpty()) {
            SettingsManager.saveSetting(prefix + "refreshToken", refreshToken);
        }
        SettingsManager.saveSetting(prefix + "expiryTimeMs", String.valueOf(expiryTimeMs));
    }

    /**
     * Returns the cached access token for the given account.
     */
    public static synchronized String getAccessToken(String account) {
        if (account == null || account.trim().isEmpty()) return null;
        String val = SettingsManager.getSetting("oauth." + account.trim().toLowerCase() + ".accessToken", "");
        return val.isEmpty() ? null : val;
    }

    /**
     * Returns the cached refresh token for the given account.
     */
    public static synchronized String getRefreshToken(String account) {
        if (account == null || account.trim().isEmpty()) return null;
        String val = SettingsManager.getSetting("oauth." + account.trim().toLowerCase() + ".refreshToken", "");
        return val.isEmpty() ? null : val;
    }

    /**
     * Returns the absolute expiry timestamp (ms) for the given account.
     */
    public static synchronized long getExpiryTimeMs(String account) {
        if (account == null || account.trim().isEmpty()) return 0L;
        String val = SettingsManager.getSetting("oauth." + account.trim().toLowerCase() + ".expiryTimeMs", "0");
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Evaluates if the access token for the given account is expired or near expiration.
     */
    public static synchronized boolean isTokenExpired(String account) {
        long expiry = getExpiryTimeMs(account);
        // Expiry verification with 30-second buffer
        return System.currentTimeMillis() + 30000 >= expiry;
    }

    /**
     * Clears all token credentials associated with the account.
     */
    public static synchronized void clearTokens(String account) {
        if (account == null || account.trim().isEmpty()) return;
        String prefix = "oauth." + account.trim().toLowerCase() + ".";
        SettingsManager.saveSetting(prefix + "accessToken", "");
        SettingsManager.saveSetting(prefix + "refreshToken", "");
        SettingsManager.saveSetting(prefix + "expiryTimeMs", "0");
    }
}

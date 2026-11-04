package com.github.tvbox.osc.util;

import android.net.Uri;
import com.orhanobut.hawk.Hawk;

public class LiveSecretManager {
    private static String sToken = null;
    private static long sUnlockTime = 0;
    private static final long SESSION_EXPIRE_MS = 4 * 3600 * 1000L; // 4小时有效

    public static boolean isSecretUnlocked() {
        if (sToken == null || sToken.isEmpty()) {
            return false;
        }
        if (System.currentTimeMillis() - sUnlockTime > SESSION_EXPIRE_MS) {
            lock();
            return false;
        }
        return true;
    }

    public static String getSessionToken() {
        if (!isSecretUnlocked()) {
            return "";
        }
        return sToken;
    }

    public static void unlock(String token) {
        sToken = token;
        sUnlockTime = System.currentTimeMillis();
    }

    public static void lock() {
        sToken = null;
        sUnlockTime = 0;
    }

    public static String getBaseServerUrl() {
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = Hawk.get(HawkConfig.LIVE_URL, "");
        }
        if (apiUrl == null || apiUrl.isEmpty()) {
            return "https://xyzweb.cyou/tvbox";
        }
        try {
            Uri uri = Uri.parse(apiUrl);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return "https://xyzweb.cyou/tvbox";
            }
            int port = uri.getPort();
            String portPart = (port > 0 && port != 80 && port != 443) ? (":" + port) : "";
            String path = uri.getPath();
            if (path != null && path.contains("/tvbox")) {
                return scheme + "://" + host + portPart + "/tvbox";
            }
            return scheme + "://" + host + portPart;
        } catch (Exception e) {
            return "https://xyzweb.cyou/tvbox";
        }
    }
}

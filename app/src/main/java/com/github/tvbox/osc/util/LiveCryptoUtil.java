package com.github.tvbox.osc.util;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class LiveCryptoUtil {
    private static final byte[] AES_KEY = "258258tvboxlive!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_IV  = "1234567890abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String PREFIX = "enc://";

    public static String decryptUrl(String url) {
        if (url == null || !url.startsWith(PREFIX)) {
            return url;
        }
        try {
            String base64Cipher = url.substring(PREFIX.length());
            byte[] cipherBytes = Base64.decode(base64Cipher, Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(AES_IV);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return url;
        }
    }

    public static boolean isAdultGroup(String groupName) {
        if (groupName == null) return false;
        String lower = groupName.toLowerCase();
        return lower.contains("成人") || lower.contains("福利") || lower.contains("私密") || lower.contains("18+") || lower.contains("adult") || lower.contains("x-live");
    }
}

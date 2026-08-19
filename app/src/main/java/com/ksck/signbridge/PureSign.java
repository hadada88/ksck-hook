package com.ksck.signbridge;

import java.net.URLDecoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 纯算签名 - sig (MD5) 和 __NStokensig (SHA256)
 * 移植自 ks_pure_sign.js
 */
public class PureSign {

    private static final String SALT = "772867c19925";

    public static String buildPlainText(String queryString) {
        try {
            List<Pair> pairs = new ArrayList<>();
            if (queryString != null && !queryString.isEmpty()) {
                for (String part : queryString.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) {
                        String key = urlDecode(part.substring(0, eq));
                        String val = urlDecode(part.substring(eq + 1));
                        if (!key.startsWith("__NS") && !"sig".equals(key)) {
                            pairs.add(new Pair(key, val));
                        }
                    }
                }
            }

            // App-side itm.b.a() sorts complete "key=value" strings and
            // concatenates them with no separator; duplicate keys are kept.
            Collections.sort(pairs, (a, b) -> {
                int byKey = a.key.compareTo(b.key);
                return byKey != 0 ? byKey : a.value.compareTo(b.value);
            });
            StringBuilder sb = new StringBuilder();
            for (Pair p : pairs) {
                sb.append(p.key).append("=").append(p.value);
            }
            return sb.toString();
        } catch (Exception e) {
            android.util.Log.e("PureSign", "buildPlainText: " + e);
            return "";
        }
    }

    public static String computeSig(String queryString) {
        try {
            String plain = buildPlainText(queryString) + SALT;
            MessageDigest md = MessageDigest.getInstance("MD5");
            return bytesToHex(md.digest(plain.getBytes("UTF-8")));
        } catch (Exception e) {
            android.util.Log.e("PureSign", "computeSig: " + e);
            return "";
        }
    }

    public static String computeTokenSig(String sig, String tokenClientSalt) {
        try {
            if (tokenClientSalt == null || tokenClientSalt.isEmpty()) return "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest((sig + tokenClientSalt).getBytes("UTF-8")));
        } catch (Exception e) {
            android.util.Log.e("PureSign", "computeTokenSig: " + e);
            return "";
        }
    }

    private static String urlDecode(String s) {
        try { return URLDecoder.decode(s, "UTF-8").replace("+", " "); }
        catch (Exception e) { return s; }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    private static class Pair {
        String key, value;
        Pair(String k, String v) { key = k; value = v; }
    }
}

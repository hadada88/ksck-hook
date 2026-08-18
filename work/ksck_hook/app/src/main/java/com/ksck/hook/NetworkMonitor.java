package com.ksck.hook;

import android.net.Uri;
import java.lang.reflect.Method;
import java.util.Set;

final class NetworkMonitor {
    private NetworkMonitor() {}

    static void inspectRequest(Object request) {
        if (request == null) return;
        try {
            Method urlMethod = request.getClass().getMethod("url");
            Object urlObj = urlMethod.invoke(request);
            if (urlObj == null) return;
            String url = String.valueOf(urlObj);
            if (url.length() == 0) return;
            ParamStore.put("lastUrlSeen", trim(url, 240));
            if (url.contains("egid=") || url.contains("newOc=") || url.contains("rdid=")) {
                parseQuery(url);
            }
        } catch (Throwable t) {
            HookEntry.log("inspectRequest failed: " + t);
        }
    }

    private static void parseQuery(String url) {
        try {
            Uri uri = Uri.parse(url);
            Set<String> names = uri.getQueryParameterNames();
            for (String name : names) {
                String value = uri.getQueryParameter(name);
                if (value == null) continue;
                if ("egid".equals(name) || "newOc".equals(name) || "rdid".equals(name) ||
                        "did".equals(name) || "kuaishou.api_st".equals(name) || "did_gt".equals(name)) {
                    ParamStore.put(name, value);
                }
            }
        } catch (Throwable t) {
            HookEntry.log("parseQuery failed: " + t);
        }
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}

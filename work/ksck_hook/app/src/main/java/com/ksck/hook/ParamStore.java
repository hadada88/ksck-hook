package com.ksck.hook;

import java.util.LinkedHashMap;
import java.util.Map;

final class ParamStore {
    private static final LinkedHashMap<String, String> DATA = new LinkedHashMap<>();

    private ParamStore() {}

    static synchronized void put(String key, String value) {
        if (key == null || value == null || value.length() == 0 || "null".equals(value)) return;
        DATA.put(key, value);
    }

    static synchronized String buildText() {
        String[] preferred = {"id", "tokenClientSalt", "did", "egid", "newOc", "rdid", "kuaishou.api_st", "securityToken"};
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String k : preferred) if (DATA.containsKey(k)) out.put(k, DATA.get(k));
        for (Map.Entry<String, String> e : DATA.entrySet()) if (!out.containsKey(e.getKey())) out.put(e.getKey(), e.getValue());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : out.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    static synchronized boolean hasUsefulData() {
        return DATA.containsKey("id") || DATA.containsKey("tokenClientSalt") || DATA.containsKey("egid") || DATA.containsKey("kuaishou.api_st");
    }
}

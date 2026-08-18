package com.ksck.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import de.robv.android.xposed.XposedHelpers;

final class FieldCollector {
    private static final String Q_CURRENT_USER = "com.kwai.framework.model.user.QCurrentUser";
    private static final List<String> FIELD_CANDIDATES = Arrays.asList(
            "tokenClientSalt", "mTokenClientSalt", "mNewTokenClientSalt",
            "id", "mId", "userId", "mUserId",
            "did", "deviceId", "mDeviceId", "egid", "mEgid",
            "kuaishou.api_st", "apiServiceToken", "mApiServiceToken", "securityToken");

    private FieldCollector() {}

    static void collectQCurrentUser(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClass(Q_CURRENT_USER, cl);
            Object me = callStatic(cls, "me");
            collectFromUserObject(me);
        } catch (Throwable t) {
            HookEntry.log("collectQCurrentUser failed: " + t);
        }
    }

    static void collectFromUserObject(Object user) {
        if (user == null) return;
        putCall(user, "getTokenClientSalt", "tokenClientSalt");
        putCall(user, "getId", "id");
        putCall(user, "getApiServiceToken", "kuaishou.api_st");
        putCall(user, "getSecurityToken", "securityToken");
        putCall(user, "getDid", "did");
        putCall(user, "getDeviceId", "did");
        for (String name : FIELD_CANDIDATES) putField(user, name, normalizeKey(name));
    }

    private static String normalizeKey(String name) {
        if ("mTokenClientSalt".equals(name) || "mNewTokenClientSalt".equals(name)) return "tokenClientSalt";
        if ("mId".equals(name) || "userId".equals(name) || "mUserId".equals(name)) return "id";
        if ("deviceId".equals(name) || "mDeviceId".equals(name)) return "did";
        if ("mEgid".equals(name)) return "egid";
        if ("apiServiceToken".equals(name) || "mApiServiceToken".equals(name)) return "kuaishou.api_st";
        return name;
    }

    private static Object callStatic(Class<?> cls, String method) throws Exception {
        Method m = cls.getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(null);
    }

    private static void putCall(Object target, String method, String key) {
        try {
            Method m = target.getClass().getDeclaredMethod(method);
            m.setAccessible(true);
            Object v = m.invoke(target);
            if (v != null) ParamStore.put(key, String.valueOf(v));
        } catch (Throwable ignored) {}
    }

    private static void putField(Object target, String fieldName, String key) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object v = f.get(target);
                if (v != null) ParamStore.put(key, String.valueOf(v));
                return;
            } catch (Throwable ignored) {
                c = c.getSuperclass();
            }
        }
    }
}

package com.ksck.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import com.ksck.signbridge.AtlasSign;
import com.ksck.http.SimpleHttpServer;

/**
 * KS_CK - 快手极速版一键提取 Cookie 模块
 * 移植自旧版 KwaiHook，适配新版快手极速版 (com.kuaishou.nebula)
 * 移除所有 Telegram 外链和网络上传代码，仅日志输出
 * 零 Android SDK 依赖（运行时反射调用）
 */
public class MainHook implements IXposedHookLoadPackage {

    public static android.content.Context appContext = null;

    private static final String TAG = "KS_CK";
    private static final String TARGET_PACKAGE = "com.kuaishou.nebula";

    private String tokenClientSalt;
    private String userId;
    private String kuaishouApiSt;
    private String egid;
    private String did;
    private String oDid;
    private String kpn;
    private String kpf;
    private String newOc;
    private String rdid;
    private String appVer;
    private String clientKey;
    private String os;
    private String ud;

    private boolean dialogShown = false;
    private boolean shouldStopHooking = false;
    private ClassLoader appClassLoader;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)
                && !lpparam.packageName.equals("com.smile.gifmaker")) {
            return;
        }

        XposedBridge.log(TAG + " 加载到: " + lpparam.packageName);
        appClassLoader = lpparam.classLoader;
        AtlasSign.init(appClassLoader);
        SimpleHttpServer.start();
        try {
            // Use XposedHelpers to get Application from ActivityThread
            Object at = XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread");
            if (at != null) {
                android.content.Context ctx = (android.content.Context) XposedHelpers.callMethod(at, "getApplication");
                if (ctx != null) {
                    appContext = ctx;
                    AtlasSign.setContext(ctx);
                }
            }
        } catch (Throwable t) { XposedBridge.log("KS_CK getAppContext: " + t); }
        ClassLoader cl = appClassLoader;

        // 1. Hook QCurrentUser 获取用户信息
        hookQCurrentUser(cl);

        // 2. Hook 网络参数类 (eClass)
        hookEClass(cl);

        // 3. Hook Retrofit 参数类 (dClass)
        hookDClass(cl);

        // 4. Hook OkHttpClient.newCall 捕获请求参数
        hookOkHttpNewCall(cl);

        // 5. Hook Activity.onResume 激活输出
        hookActivity(cl);
    }

    private void hookQCurrentUser(ClassLoader cl) {
        try {
            final Class<?> qcuClass = XposedHelpers.findClass(
                    "com.kwai.framework.model.user.QCurrentUser", cl);

            // Hook getTokenClientSalt
            try {
                XposedHelpers.findAndHookMethod(qcuClass, "getTokenClientSalt",
                        new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getResult() != null)
                            tokenClientSalt = param.getResult().toString();
                    }
                });
            } catch (Throwable ignored) {}

            // Hook getId
            try {
                XposedHelpers.findAndHookMethod(qcuClass, "getId",
                        new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getResult() != null) {
                            userId = param.getResult().toString();
                            ud = userId;
                        }
                    }
                });
            } catch (Throwable ignored) {}

            XposedBridge.log(TAG + " QCurrentUser Hook 就绪");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hookQCurrentUser 失败: " + t);
        }
    }

    private void hookEClass(ClassLoader cl) {
        try {
            Class<?> eClass = XposedHelpers.findClass(
                    "com.kwai.framework.network.access.params.e", cl);

            hookGetter(eClass, "getKpn", v -> kpn = v);
            hookGetter(eClass, "getKpf", v -> kpf = v);
            hookGetter(eClass, "getDeviceId", v -> did = v);
            hookGetter(eClass, "getODid", v -> oDid = v);

            XposedBridge.log(TAG + " eClass Hook 就绪");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hookEClass 失败: " + t);
        }
    }

    private void hookDClass(ClassLoader cl) {
        try {
            Class<?> dClass = XposedHelpers.findClass(
                    "com.yxcorp.retrofit.d", cl);

            hookGetter(dClass, "getOs", v -> os = v);
            hookGetter(dClass, "getClientKey", v -> clientKey = v);
            hookGetter(dClass, "getKuaishouApiSt", v -> kuaishouApiSt = v);

            XposedBridge.log(TAG + " dClass Hook 就绪");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hookDClass 失败: " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private void hookOkHttpNewCall(ClassLoader cl) {
        try {
            Class<?> okHttpClient = XposedHelpers.findClass("okhttp3.OkHttpClient", cl);
            Class<?> requestClass = XposedHelpers.findClass("okhttp3.Request", cl);

            XposedHelpers.findAndHookMethod(okHttpClient, "newCall", requestClass,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (shouldStopHooking) return;

                    try {
                        Object request = param.args[0];
                        Method urlMethod = request.getClass().getMethod("url");
                        Object httpUrl = urlMethod.invoke(request);
                        String urlStr = httpUrl.toString();

                        if (urlStr.contains("egid=")) {
                            String query = (String) httpUrl.getClass()
                                    .getMethod("query").invoke(httpUrl);
                            extractParamsFromQuery(query);

                            try {
                                Method hMethod = request.getClass().getMethod("headers");
                                Object headers = hMethod.invoke(request);
                                String cookie = (String) headers.getClass()
                                        .getMethod("get", String.class).invoke(headers, "Cookie");
                                if (cookie != null) extractParamsFromCookie(cookie);
                            } catch (Throwable ignored) {}

                            refreshCurrentUser();
                            if (hasAllRequiredParams()) buildAndOutput();
                        }
                    } catch (Throwable ignored) {}
                }
            });

            XposedBridge.log(TAG + " OkHttpClient.newCall Hook 就绪");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hookOkHttpNewCall 失败: " + t);
        }
    }

    private void hookActivity(ClassLoader cl) {
        try {
            Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", cl);
            XposedHelpers.findAndHookMethod(activityClass, "onResume",
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (dialogShown) return;
                    try {
                        final Object activity = param.thisObject;
                        Class<?> handlerClass = Class.forName("android.os.Handler");
                        Class<?> looperClass = Class.forName("android.os.Looper");
                        Method getMainLooper = looperClass.getMethod("getMainLooper");
                        Object mainLooper = getMainLooper.invoke(null);
                        Object handler = handlerClass.getConstructor(looperClass)
                                .newInstance(mainLooper);
                        Method postDelayed = handlerClass.getMethod("postDelayed",
                                Runnable.class, long.class);
                        postDelayed.invoke(handler, new Runnable() {
                            @Override
                            public void run() {
                                if (appContext == null) {
                                    try {
                                        android.content.Context ctx = (android.content.Context) activity;
                                        if (ctx != null) {
                                            appContext = ctx.getApplicationContext();
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                refreshCurrentUser();
                                if (hasAllRequiredParams()) {
                                    try {
                                        Class<?> qcuClass;
                                        try {
                                            qcuClass = Class.forName("com.kwai.framework.model.user.QCurrentUser", false, appClassLoader);
                                        } catch (Throwable classLoaderError) {
                                            XposedBridge.log(TAG + " tokenClientSalt Class.forName(appClassLoader) failed: " + classLoaderError);
                                            qcuClass = Class.forName("com.kwai.framework.model.user.QCurrentUser");
                                        }

                                        Method m = qcuClass.getMethod("me");
                                        Object me = m.invoke(null);
                                        if (me == null) {
                                            XposedBridge.log(TAG + " tokenClientSalt QCurrentUser.me() returned null");
                                        } else {
                                            String[] saltFieldNames = new String[] {
                                                    "mNewTokenClientSalt",
                                                    "tokenClientSalt",
                                                    "mTokenClientSalt",
                                                    "M_TOKEN_CLIENT_SALT"
                                            };
                                            for (int i = 0; i < saltFieldNames.length && tokenClientSalt == null; i++) {
                                                String fieldName = saltFieldNames[i];
                                                XposedBridge.log(TAG + " tokenClientSalt trying field: " + fieldName
                                                        + " on " + me.getClass().getName());
                                                try {
                                                    Field f = me.getClass().getDeclaredField(fieldName);
                                                    f.setAccessible(true);
                                                    Object v = f.get(me);
                                                    if (v != null) {
                                                        tokenClientSalt = v.toString();
                                                        XposedBridge.log(TAG + " tokenClientSalt read from field " + fieldName);
                                                    } else {
                                                        XposedBridge.log(TAG + " tokenClientSalt field " + fieldName + " is null");
                                                    }
                                                } catch (Throwable fieldError) {
                                                    XposedBridge.log(TAG + " tokenClientSalt field " + fieldName
                                                            + " failed: " + fieldError);
                                                }
                                            }

                                            if (tokenClientSalt == null) {
                                                XposedBridge.log(TAG + " tokenClientSalt trying method: getTokenClientSalt");
                                                try {
                                                    Method saltGetter = me.getClass().getMethod("getTokenClientSalt");
                                                    Object v = saltGetter.invoke(me);
                                                    if (v != null) {
                                                        tokenClientSalt = v.toString();
                                                        XposedBridge.log(TAG + " tokenClientSalt read from getTokenClientSalt()");
                                                    } else {
                                                        XposedBridge.log(TAG + " tokenClientSalt getTokenClientSalt() returned null");
                                                    }
                                                } catch (Throwable getterError) {
                                                    XposedBridge.log(TAG + " tokenClientSalt getTokenClientSalt() failed: " + getterError);
                                                }
                                            }

                                            try {
                                                Field f2 = me.getClass().getDeclaredField("mUserId");
                                                f2.setAccessible(true);
                                                Object v2 = f2.get(me);
                                                if (v2 != null) {
                                                    userId = v2.toString();
                                                    ud = userId;
                                                }
                                            } catch (Throwable userIdError) {
                                                XposedBridge.log(TAG + " mUserId read failed: " + userIdError);
                                            }
                                        }
                                    } catch (Throwable t) {
                                        XposedBridge.log(TAG + " tokenClientSalt QCurrentUser.me()/reflection failed: " + t);
                                    }
                                    buildAndOutput();
                                    dialogShown = true;
                                }
                            }
                        }, 5000L);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hookActivity 失败: " + t);
        }
    }

    private void hookGetter(Class<?> clazz, String methodName, final ValueConsumer consumer) {
        try {
            XposedHelpers.findAndHookMethod(clazz, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getResult() != null)
                        consumer.accept(param.getResult().toString());
                }
            });
        } catch (Throwable ignored) {}
    }


    private void refreshCurrentUser() {
        try {
            Class<?> qcuClass;
            try {
                qcuClass = Class.forName("com.kwai.framework.model.user.QCurrentUser", false, appClassLoader);
            } catch (Throwable classLoaderError) {
                XposedBridge.log(TAG + " QCurrentUser Class.forName(appClassLoader) failed: " + classLoaderError);
                qcuClass = Class.forName("com.kwai.framework.model.user.QCurrentUser");
            }

            Object me = null;
            try {
                Method m = qcuClass.getMethod("me");
                me = m.invoke(null);
            } catch (Throwable meError) {
                XposedBridge.log(TAG + " QCurrentUser.me() failed: " + meError);
            }
            if (me == null) {
                try {
                    Field meField = qcuClass.getDeclaredField("ME");
                    meField.setAccessible(true);
                    me = meField.get(null);
                } catch (Throwable meFieldError) {
                    XposedBridge.log(TAG + " QCurrentUser.ME failed: " + meFieldError);
                }
            }
            if (me == null) return;

            if (tokenClientSalt == null || tokenClientSalt.isEmpty()) {
                readFirstString(me, new String[] {
                        "mTokenClientSalt",
                        "mNewTokenClientSalt",
                        "tokenClientSalt",
                        "M_TOKEN_CLIENT_SALT"
                }, v -> tokenClientSalt = v);
                if (tokenClientSalt == null || tokenClientSalt.isEmpty()) {
                    callFirstString(me, new String[] {"getTokenClientSalt"}, v -> tokenClientSalt = v);
                }
            }
            if (userId == null || userId.isEmpty()) {
                readFirstString(me, new String[] {"mUserId", "mId", "id", "userId"}, v -> { userId = v; ud = v; });
                if (userId == null || userId.isEmpty()) {
                    callFirstString(me, new String[] {"getId", "getUserId"}, v -> { userId = v; ud = v; });
                }
            }
            if (kuaishouApiSt == null || kuaishouApiSt.isEmpty()) {
                readFirstString(me, new String[] {"mKuaishouApiSt", "kuaishouApiSt", "apiServiceToken", "mApiServiceToken"}, v -> kuaishouApiSt = v);
                if (kuaishouApiSt == null || kuaishouApiSt.isEmpty()) {
                    callFirstString(me, new String[] {"getApiServiceToken"}, v -> kuaishouApiSt = v);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " refreshCurrentUser failed: " + t);
        }
    }

    private void extractParamsFromQuery(String query) {
        if (query == null) return;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length < 2) continue;
            String k = kv[0], v = kv[1];
            switch (k) {
                case "egid": egid = v; break;
                case "kuaishou.api_st": kuaishouApiSt = v; break;
                case "did": did = v; break;
                case "oDid": oDid = v; break;
                case "kpn": kpn = v; break;
                case "kpf": kpf = v; break;
                case "newOc": newOc = v; break;
                case "rdid": rdid = v; break;
                case "client_key": clientKey = v; break;
                case "os": os = v; break;
                case "ud": ud = v; if (userId == null) userId = v; break;
            }
        }
    }

    private void extractParamsFromCookie(String cookie) {
        if (cookie == null) return;
        for (String pair : cookie.split(";")) {
            pair = pair.trim();
            String[] kv = pair.split("=", 2);
            if (kv.length < 2) continue;
            String k = kv[0].trim(), v = kv[1].trim();
            switch (k) {
                case "egid": egid = v; break;
                case "did": did = v; break;
                case "userId": userId = v; break;
                case "kuaishou.api_st": kuaishouApiSt = v; break;
                case "appver": appVer = v; break;
            }
        }
    }

    private boolean hasAllRequiredParams() {
        return egid != null && did != null && userId != null
                && tokenClientSalt != null && !tokenClientSalt.isEmpty();
    }

    private void buildAndOutput() {
        if (dialogShown) return;
        dialogShown = true;
        shouldStopHooking = true;

        StringBuilder sb = new StringBuilder();
        appendParam(sb, "egid", egid);
        appendParam(sb, "did", did);
        appendParam(sb, "userId", userId);
        appendParam(sb, "kuaishou.api_st", kuaishouApiSt);
        appendParam(sb, "tokenClientSalt", tokenClientSalt);
        appendParam(sb, "oDid", oDid);
        appendParam(sb, "kpn", kpn);
        appendParam(sb, "kpf", kpf);
        appendParam(sb, "newOc", newOc);
        appendParam(sb, "rdid", rdid);
        appendParam(sb, "appver", appVer);
        appendParam(sb, "client_key", clientKey);
        appendParam(sb, "os", os);
        if (tokenClientSalt != null) {
            sb.append(";#=").append(tokenClientSalt);
        }

        String result = sb.toString();

        // 日志输出
        XposedBridge.log(TAG + " ===== KS_CK 提取结果 =====");
        XposedBridge.log(TAG + " " + result);
        XposedBridge.log(TAG + " ===== 提取结束 =====");

        // 反射调用剪贴板（不需要 android.jar 编译）
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApp = activityThread.getMethod("currentApplication");
            Object context = currentApp.invoke(null);
            Method getSystemService = context.getClass().getMethod("getSystemService", String.class);

            // ClipboardManager
            Object clipboard = getSystemService.invoke(context, "clipboard");
            Class<?> clipDataClass = Class.forName("android.content.ClipData");
            Method newPlainText = clipDataClass.getMethod("newPlainText", CharSequence.class, CharSequence.class);
            Object clipData = newPlainText.invoke(null, "KS_CK", result);
            Method setPrimaryClip = clipboard.getClass().getMethod("setPrimaryClip", clipDataClass);
            setPrimaryClip.invoke(clipboard, clipData);

            // Toast
            try {
                Object toast = getSystemService.invoke(context, "toast");
                Class<?> toastClass = Class.forName("android.widget.Toast");
                Method makeText = toastClass.getMethod("makeText",
                        Class.forName("android.content.Context"),
                        CharSequence.class, int.class);
                Object toastObj = makeText.invoke(null, context, "KS_CK 已复制", 1);
                Method show = toastClass.getMethod("show");
                show.invoke(toastObj);
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            XposedBridge.log(TAG + " 剪贴板输出失败: " + t);
        }
    }

    private void appendParam(StringBuilder sb, String key, String value) {
        if (value != null && !value.isEmpty()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(key).append("=").append(value);
        }
    }

    private void readField(Object obj, String fieldName, ValueConsumer consumer) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(obj);
            if (val != null) consumer.accept(val.toString());
        } catch (Throwable ignored) {}
    }


    private void readFirstString(Object obj, String[] fieldNames, ValueConsumer consumer) {
        for (String fieldName : fieldNames) {
            try {
                Field f = obj.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object val = f.get(obj);
                if (val != null && !val.toString().isEmpty()) {
                    consumer.accept(val.toString());
                    XposedBridge.log(TAG + " read " + fieldName + " from " + obj.getClass().getName());
                    return;
                }
            } catch (Throwable ignored) {}
        }
    }

    private void callFirstString(Object obj, String[] methodNames, ValueConsumer consumer) {
        for (String methodName : methodNames) {
            try {
                Method m = obj.getClass().getMethod(methodName);
                Object val = m.invoke(obj);
                if (val != null && !val.toString().isEmpty()) {
                    consumer.accept(val.toString());
                    XposedBridge.log(TAG + " read " + methodName + "() from " + obj.getClass().getName());
                    return;
                }
            } catch (Throwable ignored) {}
        }
    }

    @FunctionalInterface
    interface ValueConsumer {
        void accept(String value);
    }
}

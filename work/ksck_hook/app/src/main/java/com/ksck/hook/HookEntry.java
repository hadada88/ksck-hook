package com.ksck.hook;

import android.app.Application;
import android.content.Context;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    static final String MODULE = "KS_CK";
    static final String TARGET_PACKAGE = "com.kuaishou.nebula";
    private static volatile boolean appHooked;
    private static volatile boolean qUserHooked;
    private static volatile boolean okHttpHooked;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;
        log("loaded target " + lpparam.packageName);
        hookApplicationOnCreate(lpparam.classLoader);
        hookApplicationAttach(lpparam.classLoader);
        hookQCurrentUser(lpparam.classLoader);
        hookOkHttpNewCall(lpparam.classLoader);
    }

    private static void hookApplicationAttach(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Context ctx = (Context) param.args[0];
                    ClipboardUtil.setContext(ctx);
                    hookQCurrentUser(cl);
                    hookOkHttpNewCall(cl);
                    FieldCollector.collectQCurrentUser(cl);
                    ClipboardUtil.copyIfReady();
                }
            });
        } catch (Throwable t) {
            log("Application.attach hook skipped: " + t);
        }
    }

    private static void hookApplicationOnCreate(final ClassLoader cl) {
        if (appHooked) return;
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    appHooked = true;
                    ClipboardUtil.setContext((Context) param.thisObject);
                    log("Application.onCreate");
                    hookQCurrentUser(cl);
                    hookOkHttpNewCall(cl);
                    FieldCollector.collectQCurrentUser(cl);
                    ClipboardUtil.copyIfReady();
                }
            });
        } catch (Throwable t) {
            log("Application.onCreate hook failed: " + t);
        }
    }

    static void hookQCurrentUser(final ClassLoader cl) {
        if (qUserHooked) return;
        try {
            Class<?> qCls = XposedHelpers.findClass("com.kwai.framework.model.user.QCurrentUser", cl);
            qUserHooked = true;
            XposedBridge.hookAllMethods(qCls, "me", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    FieldCollector.collectFromUserObject(param.getResult());
                    ClipboardUtil.copyIfReady();
                }
            });
            hookNoArgGetter(qCls, "getTokenClientSalt", "tokenClientSalt");
            hookNoArgGetter(qCls, "getId", "id");
            hookNoArgGetter(qCls, "getApiServiceToken", "kuaishou.api_st");
            hookNoArgGetter(qCls, "getSecurityToken", "securityToken");
            FieldCollector.collectQCurrentUser(cl);
            log("QCurrentUser hook installed");
        } catch (Throwable t) {
            log("QCurrentUser hook pending: " + t);
        }
    }

    private static void hookNoArgGetter(Class<?> cls, final String method, final String key) {
        try {
            XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Object v = param.getResult();
                    if (v != null) ParamStore.put(key, String.valueOf(v));
                    FieldCollector.collectFromUserObject(param.thisObject);
                    ClipboardUtil.copyIfReady();
                }
            });
        } catch (Throwable t) {
            log("getter hook skipped " + method + ": " + t);
        }
    }

    static void hookOkHttpNewCall(final ClassLoader cl) {
        if (okHttpHooked) return;
        try {
            Class<?> okHttp = XposedHelpers.findClass("okhttp3.OkHttpClient", cl);
            Class<?> request = XposedHelpers.findClass("okhttp3.Request", cl);
            XposedHelpers.findAndHookMethod(okHttp, "newCall", request, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    NetworkMonitor.inspectRequest(param.args != null && param.args.length > 0 ? param.args[0] : null);
                    ClipboardUtil.copyIfReady();
                }
            });
            okHttpHooked = true;
            log("OkHttpClient.newCall hook installed");
        } catch (Throwable t) {
            log("OkHttpClient.newCall hook pending: " + t);
        }
    }

    static void log(String msg) {
        XposedBridge.log(MODULE + ": " + msg);
    }
}

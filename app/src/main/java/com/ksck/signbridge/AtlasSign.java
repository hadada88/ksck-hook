package com.ksck.signbridge;

import java.lang.reflect.Method;
import android.app.Application;
import android.os.Build;

/**
 * Atlas 签名 - 调用 KSecurity 和 MXSec API
 * 运行在真机 App 进程内，直接 Java API 调用（不需要 Frida）
 */
public class AtlasSign {

    private static ClassLoader appClassLoader;

    private static final String AD_NAMESPACE = "KwaiAdAwardVideo";
    private static final String AD_APP_KEY = "95147564-9763-4413-a937-6f0e3c12caf1";
    private static final int AD_SDK_TYPE = 0;

    public static void init(ClassLoader appClassLoader) {
        AtlasSign.appClassLoader = appClassLoader;
    }

    /**
     * 获取状态
     */
    public static String getStatus() {
        try {
            Class<?> KSecurity = appClassLoader.loadClass("com.kuaishou.android.security.KSecurity");
            Method isInit = KSecurity.getMethod("isInitialize");
            boolean initialized = (Boolean) isInit.invoke(null);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"initialized\":").append(initialized);

            // wrapper
            try {
                Class<?> MXSec = appClassLoader.loadClass("com.middleware.security.MXSec");
                Method get = MXSec.getMethod("get");
                Object mx = get.invoke(null);
                Method getWrapper = mx.getClass().getMethod("getWrapper");
                Object w = getWrapper.invoke(mx);
                sb.append(",\"wrapper\":\"").append(w != null ? w.getClass().getName() : "null").append("\"");
                Method getMXWrapper = mx.getClass().getMethod("getMXWrapper");
                Object mw = getMXWrapper.invoke(mx);
                sb.append(",\"mxWrapper\":\"").append(mw != null ? mw.getClass().getName() : "null").append("\"");
            } catch (Exception e) {
                sb.append(",\"wrapperError\":\"").append(e.getMessage().replace("\"", "'")).append("\"");
            }

            // wbKeyLength
            try {
                Class<?> configCls = appClassLoader.loadClass("com.kuaishou.android.security.KSecurity");
                Method getConfig = configCls.getMethod("getConfig");
                Object config = getConfig.invoke(null);
                if (config != null) {
                    // 反射找 h() 方法
                    for (Method m : config.getClass().getDeclaredMethods()) {
                        if (m.getName().equals("h") && m.getParameterTypes().length == 0) {
                            m.setAccessible(true);
                            Object wbKey = m.invoke(config);
                            String wbKeyStr = wbKey != null ? wbKey.toString() : "";
                            sb.append(",\"wbKeyLength\":").append(wbKeyStr.length());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                sb.append(",\"wbKeyError\":\"").append(e.getMessage().replace("\"", "'")).append("\"");
            }

            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    /**
     * 计算 __NS_sig3
     * path: API 路径 (如 /rest/e/reward/mixed/ad)
     * sig: 32 位 MD5 hex
     */
    public static String sig3(String path, String sig) {
        try {
            Class<?> KSecurity = appClassLoader.loadClass("com.kuaishou.android.security.KSecurity");
            Method atlasSign = KSecurity.getMethod("atlasSign", String.class);
            Object result = atlasSign.invoke(null, path + sig);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            android.util.Log.e("AtlasSign", "sig3 error: " + e);
            return "";
        }
    }

    /**
     * 计算新版 access sig：与 App 内 KwaiSignSupplierImpl 一致，
     * 对排序后的 key=value 明文调用 com.yxcorp.gifshow.util.CPU.getClock。
     */
    public static String accessSig(String plainText) {
        try {
            if (plainText == null || plainText.length() == 0) return "";
            Class<?> cpu = appClassLoader.loadClass("com.yxcorp.gifshow.util.CPU");
            Method getClock = cpu.getMethod("getClock",
                    Application.class, byte[].class, int.class);
            Application app = currentApplication();
            Object result = getClock.invoke(null,
                    app, plainText.getBytes("UTF-8"), Build.VERSION.SDK_INT);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            android.util.Log.e("AtlasSign", "accessSig error: " + e);
            return "";
        }
    }

    private static Application currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApp = activityThread.getMethod("currentApplication");
            Object app = currentApp.invoke(null);
            return (Application) app;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 计算广告 encData 和 sign
     * base64Data: Base64 编码的广告数据
     * 返回 {encdata, sign}
     */
    public static String[] encsign(String base64Data) {
        try {
            Class<?> MXSec = appClassLoader.loadClass("com.middleware.security.MXSec");
            Method get = MXSec.getMethod("get");
            Object mx = get.invoke(null);
            Method getMXWrapper = mx.getClass().getMethod("getMXWrapper");
            Object wrapper = getMXWrapper.invoke(mx);

            // 获取字节数组
            Class<?> stringClass = String.class;
            Class<?> base64Class = appClassLoader.loadClass("android.util.Base64");
            byte[] rawBytes = base64Data.getBytes("UTF-8");

            // 加密: wrapper.a(namespace, appKey, sdkType, rawBytes)
            Method encMethod = wrapper.getClass().getMethod("a",
                    String.class, String.class, int.class, byte[].class);
            byte[] encrypted = (byte[]) encMethod.invoke(wrapper,
                    AD_NAMESPACE, AD_APP_KEY, AD_SDK_TYPE, rawBytes);

            // 签名: wrapper.b(namespace, appKey, sdkType, input)
            Method signMethod = wrapper.getClass().getMethod("b",
                    String.class, String.class, int.class, String.class);
            String sign = (String) signMethod.invoke(wrapper,
                    AD_NAMESPACE, AD_APP_KEY, AD_SDK_TYPE, base64Data);

            // Base64 编码加密结果
            Method encodeToString = base64Class.getMethod("encodeToString", byte[].class, int.class);
            String encdata = (String) encodeToString.invoke(null, encrypted, 0);

            return new String[]{encdata, sign};
        } catch (Exception e) {
            android.util.Log.e("AtlasSign", "encsign error: " + e);
            return new String[]{"", ""};
        }
    }
}

package com.ksck.extractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * KS_CK APK 内嵌提取器 - 无 Xposed 依赖，纯反射
 * 由 Application.onCreate 调用，提取 QCurrentUser 字段并写入文件
 */
public class KSCKExtractor {

    private static boolean extracted = false;

    public static void extract(Object context) {
        if (extracted) return;
        extracted = true;

        try {
            StringBuilder sb = new StringBuilder();

            // 1. 反射获取 QCurrentUser.me()
            Class<?> qcuClass = Class.forName("com.kwai.framework.model.user.QCurrentUser");
            Method meMethod = qcuClass.getMethod("me");
            Object me = meMethod.invoke(null);
            if (me == null) {
                writeLog(context, "KS_CK ERROR: QCurrentUser.me() returned null");
                return;
            }

            // 2. 读取各字段
            String tokenClientSalt = readFieldStr(me, "mTokenClientSalt");
            if (tokenClientSalt == null) tokenClientSalt = readFieldStr(me, "mNewTokenClientSalt");
            String userId = readFieldStr(me, "mUserId");
            if (userId == null) {
                try {
                    Method getId = qcuClass.getMethod("getId");
                    Object idVal = getId.invoke(me);
                    if (idVal != null) userId = idVal.toString();
                } catch (Throwable ignored) {}
            }
            String kuaishouApiSt = readFieldStr(me, "mKuaishouApiSt");
            String egid = readFieldStr(me, "mEgid");
            String did = readFieldStr(me, "mDid");
            String oDid = readFieldStr(me, "mODid");
            String newOc = readFieldStr(me, "mNewOc");
            String rdid = readFieldStr(me, "mRdid");

            // 3. 组装
            append(sb, "egid", egid);
            append(sb, "did", did);
            append(sb, "userId", userId);
            append(sb, "kuaishou.api_st", kuaishouApiSt);
            append(sb, "tokenClientSalt", tokenClientSalt);
            append(sb, "oDid", oDid);
            append(sb, "newOc", newOc);
            append(sb, "rdid", rdid);
            if (tokenClientSalt != null) {
                sb.append(";#=").append(tokenClientSalt);
            }

            // 4. 写入文件 + 日志
            String result = sb.toString();
            writeLog(context, "KS_CK: " + result);
            writeFile(context, result);

            // 5. 尝试写入外部存储
            try {
                File extDir = new File("/sdcard");
                if (extDir.exists() && extDir.canWrite()) {
                    File outFile = new File(extDir, "KS_CK.txt");
                    FileOutputStream fos = new FileOutputStream(outFile);
                    OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
                    writer.write(result);
                    writer.close();
                    fos.close();
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            try {
                writeLog(context, "KS_CK ERROR: " + t.toString());
            } catch (Throwable ignored) {}
        }
    }

    private static void append(StringBuilder sb, String key, String value) {
        if (value != null && !value.isEmpty()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(key).append("=").append(value);
        }
    }

    private static String readFieldStr(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(obj);
            return val != null ? val.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeLog(Object context, String msg) {
        try {
            // 通过反射调用 android.util.Log.i
            Class<?> logClass = Class.forName("android.util.Log");
            logClass.getMethod("i", String.class, String.class)
                    .invoke(null, "KS_CK", msg);
        } catch (Throwable ignored) {}
    }

    private static void writeFile(Object context, String content) {
        try {
            // 尝试写入 data/data/ 目录
            if (context != null) {
                Method getFilesDir = context.getClass().getMethod("getFilesDir");
                Object filesDir = getFilesDir.invoke(context);
                if (filesDir != null) {
                    File f = new File(filesDir.toString(), "KS_CK.txt");
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(content.getBytes("UTF-8"));
                    fos.close();
                }
            }
        } catch (Throwable ignored) {}
    }
}
package com.ksck.hook;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

final class ClipboardUtil {
    private static volatile Context appContext;
    private static volatile String lastCopied = "";

    private ClipboardUtil() {}

    static void setContext(Context context) {
        if (context != null) appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }

    static void copyIfReady() {
        Context ctx = appContext;
        if (ctx == null || !ParamStore.hasUsefulData()) return;
        String text = ParamStore.buildText();
        if (text.length() == 0 || text.equals(lastCopied)) return;
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("KS_CK", text));
                lastCopied = text;
                HookEntry.log("copied KS_CK fields to clipboard, chars=" + text.length());
            }
        } catch (Throwable t) {
            HookEntry.log("clipboard copy failed: " + t);
        }
    }
}

package de.robv.android.xposed;

public class XposedBridge {
    public static void log(String text) {
        System.out.println("Xposed: " + text);
    }
}
package de.robv.android.xposed;

public abstract class XCallback {
    public static final int PRIORITY_DEFAULT = 50;
    public final int priority;

    public XCallback() {
        this.priority = PRIORITY_DEFAULT;
    }

    public XCallback(int priority) {
        this.priority = priority;
    }
}
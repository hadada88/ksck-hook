package de.robv.android.xposed;

public class XC_MethodHook extends XCallback {
    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Object getResult() { return null; }
        public void setResult(Object result) {}
        public Throwable getThrowable() { return null; }
        public void setThrowable(Throwable throwable) {}
        public boolean hasThrowable() { return false; }
    }

    public static class Unhook {
        // stub
    }

    public XC_MethodHook() {
        super();
    }

    public XC_MethodHook(int priority) {
        super(priority);
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
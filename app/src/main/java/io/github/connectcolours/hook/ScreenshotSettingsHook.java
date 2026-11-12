package io.github.connectcolours.hook;

import android.content.Context;
import android.widget.Toast;
import java.lang.reflect.Proxy;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.connectcolours.screenshot.ScreenshotConfig;

final class ScreenshotSettingsHook {
    static void install(ClassLoader loader) {
        XposedHelpers.findAndHookMethod("com.oplus.screenshot.setting.ScreenshotSettingFragment",
                loader, "onResume", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) return;
                        try { add(param.thisObject, loader); }
                        catch (Throwable error) { HookLog.error("Screenshot options", error); }
                    }
                });
        HookLog.info("Screenshot 14.8.24 options installed");
    }

    private static void add(Object fragment, ClassLoader loader) {
        Object dual = XposedHelpers.callMethod(fragment, "findPreference", "pref_key_physical_keys");
        if (dual == null) return;
        Context context = (Context) XposedHelpers.callMethod(dual, "p");
        Object group = XposedHelpers.callMethod(dual, "z");
        Object triple = XposedHelpers.callMethod(group, "P0", ScreenshotConfig.TRIPLE);
        if (triple == null) {
            triple = XposedHelpers.newInstance(XposedHelpers.findClass(
                    "com.coui.appcompat.preference.COUISwitchPreference", loader), context);
            XposedHelpers.callMethod(triple, "x0", ScreenshotConfig.TRIPLE);
            XposedHelpers.callMethod(triple, "G0", "电源键+音量减键+音量加键");
            XposedHelpers.callMethod(triple, "D0", "截屏 · 与双键截图互斥");
            XposedHelpers.setBooleanField(triple, "t", false); // Preference.mPersistent, verified DEX field.
            int order = (int) XposedHelpers.callMethod(dual, "y");
            int size = (int) XposedHelpers.callMethod(group, "T0");
            for (int i = 0; i < size; i++) {
                Object sibling = XposedHelpers.callMethod(group, "S0", i);
                int siblingOrder = (int) XposedHelpers.callMethod(sibling, "y");
                if (siblingOrder > order && siblingOrder < Integer.MAX_VALUE) {
                    XposedHelpers.callMethod(sibling, "C0", siblingOrder + 1);
                }
            }
            XposedHelpers.callMethod(triple, "C0", order + 1);
            XposedHelpers.callMethod(group, "O0", triple);
            final Object added = triple;
            Class<?> listener = XposedHelpers.findClass("androidx.preference.Preference$b", loader);
            Object callback = Proxy.newProxyInstance(loader, new Class<?>[] {listener}, (self, method, args) -> {
                switch (method.getName()) {
                    case "equals": return self == args[0];
                    case "hashCode": return System.identityHashCode(self);
                    case "toString": return "ConnectColoursScreenshotChange";
                    case "onPreferenceChange":
                        try {
                            boolean checked = (boolean) args[1];
                            ScreenshotConfig.setTriple(context, checked);
                            if (checked) {
                                XposedHelpers.callMethod(fragment, "onPreferenceChange", dual, false);
                                XposedHelpers.callMethod(dual, "O0", false);
                            }
                            return true;
                        } catch (Throwable error) {
                            HookLog.error("Save screenshot options", error);
                            Toast.makeText(context, "保存失败，请查看模块日志", Toast.LENGTH_LONG).show();
                            return false;
                        }
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
            XposedHelpers.callMethod(added, "A0", callback);
            // Preserve the native listener and its data-manager updates.
            Object original = XposedHelpers.callMethod(dual, "x");
            XposedHelpers.callMethod(dual, "A0", Proxy.newProxyInstance(loader, new Class<?>[] {listener},
                    (self, method, args) -> {
                        if ("onPreferenceChange".equals(method.getName())) {
                            try {
                                if (Boolean.TRUE.equals(args[1])) {
                                    ScreenshotConfig.setTriple(context, false);
                                    XposedHelpers.callMethod(added, "O0", false);
                                }
                            } catch (Throwable error) {
                                HookLog.error("Disable three-key screenshot", error);
                                return false;
                            }
                        }
                        return method.invoke(original, args);
                    }));
        }
        XposedHelpers.callMethod(triple, "O0", ScreenshotConfig.enabled(context));
    }
}

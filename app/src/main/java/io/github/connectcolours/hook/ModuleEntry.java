package io.github.connectcolours.hook;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XC_MethodReplacement;
import io.github.connectcolours.Feature;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ModuleEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if ("io.github.connectcolours".equals(load.packageName)) {
            XposedHelpers.findAndHookMethod("io.github.connectcolours.ModuleStatus", load.classLoader,
                    "frameworkApi", XC_MethodReplacement.returnConstant(XposedBridge.getXposedVersion()));
            return;
        }
        String expected;
        if ("android".equals(load.packageName) && Build.VERSION.SDK_INT == 33) {
            try {
                FeatureConfig config = new FeatureConfig(new XSharedPreferences("io.github.connectcolours", Feature.PREFS));
                if (config.enabled(Feature.SCREENSHOT)) ScreenshotKeysHook.install(load.classLoader);
            } catch (Throwable error) { HookLog.error("Install screenshot system hooks", error); }
            try {
                FeatureConfig config = new FeatureConfig(new XSharedPreferences("io.github.connectcolours", Feature.PREFS));
                if (config.enabled(Feature.SIDEBAR)) SidebarRelayHook.install(load.classLoader);
            } catch (Throwable error) { HookLog.error("Install sidebar system relay", error); }
            return;
        }
        switch (load.packageName) {
            case "com.android.launcher": expected = "13.0.44"; break;
            case "com.coloros.smartsidebar": expected = "15.2.7"; break;
            case "com.oplus.screenshot": expected = "14.8.24"; break;
            case "com.android.systemui": expected = "13.01.21"; break;
            case "com.android.settings": expected = "13.0.5"; break;
            case "com.oplus.wirelesssettings": expected = "13.8.1"; break;
            case "com.oplus.ota": expected = "3.16.17"; break;
            case "com.android.stk":
            case "com.android.stk2": expected = null; break;
            default: return;
        }
        if (Build.VERSION.SDK_INT != 33) {
            HookLog.info("Unsupported Android SDK " + Build.VERSION.SDK_INT);
            return;
        }
        // STK is a secondary package in com.android.phone. Its Application.attach
        // may not run again after this load callback, so install using its loader now.
        if (expected == null) {
            FeatureConfig config = new FeatureConfig(
                    new XSharedPreferences("io.github.connectcolours", Feature.PREFS));
            if (config.enabled(Feature.SIM_WELCOME)) SimWelcomeHook.install(load.classLoader);
            else HookLog.info("SIM welcome disabled");
            HookLog.info("Loaded " + load.packageName + " process=" + load.processName);
            return;
        }
        final String expectedVersion = expected;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            private boolean installed;

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (installed) return;
                installed = true;
                Context context = (Context) param.args[0];
                try {
                    FeatureConfig config = new FeatureConfig(
                            new XSharedPreferences("io.github.connectcolours", Feature.PREFS));
                    String actual = context.getPackageManager()
                            .getPackageInfo(load.packageName, 0).versionName;
                    if (!expectedVersion.equals(actual)) {
                        HookLog.info("Unsupported " + load.packageName + " version " + actual);
                        return;
                    }
                    if ("com.android.launcher".equals(load.packageName)) {
                        if ("com.android.launcher".equals(load.processName)) HiddenRecentsHook.install(load.classLoader);
                    } else if ("com.coloros.smartsidebar".equals(load.packageName)) {
                        if (config.enabled(Feature.SIDEBAR)) SidebarProviderHook.install(load.classLoader);
                    } else if ("com.oplus.screenshot".equals(load.packageName)) {
                        if (config.enabled(Feature.SCREENSHOT)) ScreenshotSettingsHook.install(load.classLoader);
                    } else if ("com.android.systemui".equals(load.packageName)) {
                        if ("com.android.systemui".equals(load.processName) && config.enabled(Feature.SIDEBAR)) {
                            try { SidebarGestureHook.install(load.classLoader, config.sidebarHoldMillis()); }
                            catch (Throwable error) { HookLog.error("Install sidebar gestures", error); }
                        }
                        if ("com.android.systemui".equals(load.processName) && config.enabled(Feature.BLUETOOTH)) {
                            BluetoothReconnectHook.install(context, load.classLoader,
                                    config.enabled(Feature.BLUETOOTH_PROMPTS));
                        }
                        if (config.enabled(Feature.TRAFFIC)) TrafficTileHook.install(load.classLoader);
                    } else if ("com.oplus.wirelesssettings".equals(load.packageName)) {
                        if (config.enabled(Feature.BLUETOOTH)) BluetoothSettingsHook.install(load.classLoader, context);
                    } else if ("com.android.settings".equals(load.packageName)) {
                        SettingsHook.install(load.classLoader, config);
                    } else {
                        OtaHook.install(load.classLoader, context, config);
                    }
                    HookLog.info("Loaded " + load.packageName + " " + actual
                            + " process=" + load.processName);
                } catch (Throwable error) {
                    HookLog.error("Install " + load.packageName, error);
                }
            }
        });
    }
}

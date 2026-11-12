package io.github.connectcolours.hook;

import de.robv.android.xposed.XposedBridge;

final class HookLog {
    private HookLog() {}

    static void info(String message) {
        XposedBridge.log("[Connect Colours] " + message);
    }

    static void error(String feature, Throwable error) {
        info(feature + ": " + error);
    }
}

package io.github.connectcolours.screenshot;

import android.content.Context;
import android.provider.Settings;

public final class ScreenshotConfig {
    public static final String TRIPLE = "connect_colours_screenshot_three_keys";
    public static final String DUAL = "oplus_customize_screenshot_enable_physical_keys";
    private ScreenshotConfig() {}

    public static boolean enabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), TRIPLE, 0) == 1
                && Settings.System.getInt(context.getContentResolver(), DUAL, 1) == 0;
    }

    public static void setTriple(Context context, boolean enabled) {
        // Disable the competing mode first: a failed write must never enable both.
        if (enabled && !Settings.System.putInt(context.getContentResolver(), DUAL, 0)) {
            throw new IllegalStateException("Cannot disable two-key screenshot");
        }
        if (!Settings.Secure.putInt(context.getContentResolver(), TRIPLE, enabled ? 1 : 0)) {
            throw new IllegalStateException("Cannot save three-key screenshot");
        }
    }
}

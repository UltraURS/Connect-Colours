package io.github.connectcolours.hook;

import android.content.SharedPreferences;
import io.github.connectcolours.Feature;

final class FeatureConfig {
    private final SharedPreferences preferences;

    FeatureConfig(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    int sidebarHoldMillis() {
        return Math.max(200, Math.min(1000, preferences.getInt(
                io.github.connectcolours.sidebar.SidebarGesture.HOLD_KEY,
                io.github.connectcolours.sidebar.SidebarGesture.DEFAULT_HOLD_MILLIS)));
    }

    boolean enabled(Feature feature) {
        return preferences.getBoolean(feature.key, true);
    }
}

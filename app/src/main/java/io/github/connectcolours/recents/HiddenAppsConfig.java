package io.github.connectcolours.recents;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class HiddenAppsConfig {
    public static final String KEY = "hidden_recent_apps";

    public static Set<String> read(SharedPreferences preferences) {
        return new HashSet<>(preferences.getStringSet(KEY, Collections.emptySet()));
    }

    public static boolean setHidden(SharedPreferences preferences, String packageName, boolean hidden) {
        Set<String> packages = read(preferences);
        if (hidden) packages.add(packageName);
        else packages.remove(packageName);
        return preferences.edit().putStringSet(KEY, packages).commit();
    }
}

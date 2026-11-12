package io.github.connectcolours.hook;

public final class UpdatePolicy {
    private UpdatePolicy() {}

    public static final String OTA_PACKAGE = "com.oplus.ota";
    public static final String[] HIDDEN_KEYS = {
            "key_ota_update", "ota_settings", "system_update_settings",
            "additional_system_update_settings"
    };

    public static boolean isUpdateEntry(String key, String targetPackage, String action) {
        if (OTA_PACKAGE.equals(targetPackage) || "com.oplus.ota.MAIN".equals(action)
                || "android.settings.SYSTEM_UPDATE_SETTINGS".equals(action)) return true;
        for (String hidden : HIDDEN_KEYS) {
            if (hidden.equals(key)) return true;
        }
        return false;
    }
}

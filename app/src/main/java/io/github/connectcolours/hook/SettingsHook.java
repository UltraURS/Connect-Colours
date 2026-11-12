package io.github.connectcolours.hook;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import io.github.connectcolours.Feature;

final class SettingsHook {
    static void install(ClassLoader loader, FeatureConfig config) {
        if (config.enabled(Feature.UPDATE_ENTRIES)) installEntries(loader);
        if (config.enabled(Feature.UPDATE_DOTS)) installDots(loader);
    }

    private static void installEntries(ClassLoader loader) {
        for (String name : new String[] {
                "com.oplus.settings.feature.deviceinfo.controller.OtaUpdateController",
                "com.oplus.settings.feature.deviceinfo.controller.TopLevelOtaPreferenceController",
                "com.android.settings.system.SystemUpdatePreferenceController",
                "com.android.settings.system.AdditionalSystemUpdatePreferenceController"
        }) {
            XposedHelpers.findAndHookMethod(name, loader, "getAvailabilityStatus",
                    XC_MethodReplacement.returnConstant(3));
        }
        XposedHelpers.findAndHookMethod(
                "com.oplus.settings.feature.homepage.messageentry.MessageEntryController",
                loader, "e0", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.getResult() instanceof List)) return;
                        List<Object> filtered = new ArrayList<>();
                        for (Object entry : (List<?>) param.getResult()) {
                            // Original DEX fields; jadx display aliases are not runtime names.
                            String owner = (String) XposedHelpers.getObjectField(entry, "k");
                            String target = (String) XposedHelpers.getObjectField(entry, "m");
                            String action = (String) XposedHelpers.getObjectField(entry, "n");
                            if (!UpdatePolicy.OTA_PACKAGE.equals(owner)
                                    && !UpdatePolicy.isUpdateEntry(null, target, action)) {
                                filtered.add(entry);
                            }
                        }
                        param.setResult(filtered);
                    }
                });
        String provider = "com.android.settings.search.SettingsSearchIndexablesProvider";
        for (String method : new String[] {"queryRawData", "queryDynamicRawData"}) {
            XposedHelpers.findAndHookMethod(provider, loader, method, String[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.getResult() instanceof Cursor) {
                                param.setResult(filterSearch((Cursor) param.getResult()));
                            }
                        }
                    });
        }
        XposedHelpers.findAndHookMethod(provider, loader, "queryNonIndexableKeys",
                String[].class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getResult() instanceof MatrixCursor) {
                            MatrixCursor cursor = (MatrixCursor) param.getResult();
                            for (String key : UpdatePolicy.HIDDEN_KEYS) cursor.addRow(new Object[] {key});
                        }
                    }
                });
        HookLog.info("Settings entry, message card and search hooks installed");
    }

    private static void installDots(ClassLoader loader) {
        // The same OTA count feeds the About phone red dot and its observers.
        XposedHelpers.findAndHookMethod("xi.n3", loader, "p0", Context.class,
                XC_MethodReplacement.returnConstant(0));
        XposedHelpers.findAndHookMethod(
                "com.oplus.settings.feature.homepage.TopLevelAboutPhonePreferenceController",
                loader, "updatePreferenceMark", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.args[0] = 0;
                    }
                });
        HookLog.info("Settings red dot hooks installed");
    }

    static MatrixCursor filterSearch(Cursor source) {
        MatrixCursor result = new MatrixCursor(source.getColumnNames());
        try {
            while (source.moveToNext()) {
                if (UpdatePolicy.isUpdateEntry(column(source, "key"),
                        column(source, "intentTargetPackage"), column(source, "intentAction"))) continue;
                Object[] row = new Object[source.getColumnCount()];
                for (int i = 0; i < row.length; i++) {
                    switch (source.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL: row[i] = null; break;
                        case Cursor.FIELD_TYPE_INTEGER: row[i] = source.getLong(i); break;
                        case Cursor.FIELD_TYPE_FLOAT: row[i] = source.getDouble(i); break;
                        case Cursor.FIELD_TYPE_BLOB: row[i] = source.getBlob(i); break;
                        default: row[i] = source.getString(i);
                    }
                }
                result.addRow(row);
            }
        } finally {
            source.close();
        }
        return result;
    }

    private static String column(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 ? null : cursor.getString(index);
    }
}

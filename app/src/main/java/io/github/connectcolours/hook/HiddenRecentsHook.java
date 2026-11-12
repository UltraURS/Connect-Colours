package io.github.connectcolours.hook;

import android.os.Process;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import io.github.connectcolours.Feature;
import io.github.connectcolours.recents.HiddenAppsConfig;
import io.github.connectcolours.recents.RecentTaskFilter;

final class HiddenRecentsHook {
    private static final String LAST_SELECTION = "cc_recents_selection";

    static void install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> recents = XposedHelpers.findClass("com.android.quickstep.views.RecentsView", loader);
        Class<?> oplus = XposedHelpers.findClass("com.android.quickstep.views.OplusRecentsViewImpl", loader);
        Class<?> task = XposedHelpers.findClass("com.android.systemui.shared.recents.model.Task", loader);
        RecentTaskFilter filter = new RecentTaskFilter(
                XposedHelpers.findClass("com.android.quickstep.util.GroupTask", loader), task,
                XposedHelpers.findClass("com.android.launcher3.util.SplitConfigurationOptions$StagedSplitBounds", loader),
                Process.myUid() / 100000);
        XposedHelpers.findField(recents, "mTaskListChangeId");
        XposedHelpers.findMethodExact(oplus, "getDockView");
        XposedHelpers.findMethodExact(XposedHelpers.findClass("com.oplus.quickstep.dock.DockView", loader),
                "applyLoadPlan", ArrayList.class);
        XSharedPreferences preferences = new XSharedPreferences("io.github.connectcolours", Feature.PREFS);

        XposedHelpers.findAndHookMethod(recents, "reloadIfNeeded", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Set<String> selected = selection(preferences);
                if (!selected.equals(XposedHelpers.getAdditionalInstanceField(param.thisObject, LAST_SELECTION))) {
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, LAST_SELECTION, selected);
                    XposedHelpers.setIntField(param.thisObject, "mTaskListChangeId", -1);
                }
            }
        });
        XposedHelpers.findAndHookMethod(recents, "setOverviewStateEnabled", boolean.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(param.args[0])) XposedHelpers.callMethod(param.thisObject, "reloadIfNeeded");
            }
        });
        XposedHelpers.findAndHookMethod(oplus, "applyLoadPlan", ArrayList.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0] == null) return;
                ArrayList<Object> visible = filter.groups((ArrayList<?>) param.args[0], selection(preferences));
                param.args[0] = visible;
                // ColorOS skips DockView.applyLoadPlan for empty lists; clear it explicitly.
                if (visible.isEmpty()) {
                    Object dock = XposedHelpers.callMethod(param.thisObject, "getDockView");
                    if (dock != null) XposedHelpers.callMethod(dock, "applyLoadPlan", new ArrayList<>());
                }
            }
        });
        XposedHelpers.findAndHookMethod(recents, "showCurrentTask", Array.newInstance(task, 0).getClass(), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0] == null) return;
                param.args[0] = filter.running(param.args[0], selection(preferences));
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                // The native empty-array branch returns before reloading cached cards.
                if (param.args[0] != null && Array.getLength(param.args[0]) == 0)
                    XposedHelpers.callMethod(param.thisObject, "reloadIfNeeded");
            }
        });
        HookLog.info("Hidden recents installed (Launcher 13.0.44, build 15)");
    }

    private static Set<String> selection(XSharedPreferences preferences) {
        preferences.reload();
        return HiddenAppsConfig.read(preferences);
    }
}

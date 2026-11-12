package io.github.connectcolours;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MainActivityTest {
    @Test
    public void homeHasEnvironmentInfoAndOnlyTwoTabs() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            View root = controller.get().findViewById(android.R.id.content);
            assertEquals(0, switches(root).size());
            assertNotNull(root.findViewWithTag("nav_home"));
            assertNotNull(root.findViewWithTag("nav_settings"));
            assertTrue(hasText(root, "ColorOS 版本"));
            assertTrue(hasText(root, "模块版本"));
            assertFalse(hasText(root, "已适配"));
            assertFalse(hasText(root, "已启用"));
            assertFalse(hasText(root, "应用"));
        }
    }

    @Test
    public void settingsGroupsAllSwitchesAndPersistsChanges() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            SharedPreferences prefs = activity.getSharedPreferences(Feature.PREFS, Context.MODE_PRIVATE);
            prefs.edit().clear().commit();
            // A writable test store; this does not simulate LSPosed IPC.
            ReflectionHelpers.setField(activity, "preferences", prefs);
            View root = activity.findViewById(android.R.id.content);
            root.findViewWithTag("nav_settings").performClick();
            assertTrue(hasText(root, "功能类"));
            assertFalse(hasText(root, "屏蔽类"));
            android.widget.SeekBar slider = root.findViewWithTag("sidebar_hold_slider");
            assertEquals(8, slider.getMax());
            assertEquals(3, slider.getProgress());
            assertTrue(hasText(root, "隐藏类"));
            assertNotNull(root.findViewWithTag("hidden_apps"));
            assertEquals(11, switches(root).size());
            for (Feature feature : Feature.values()) {
                Switch toggle = root.findViewWithTag(feature.key);
                assertTrue(toggle.isChecked());
                toggle.performClick();
                assertFalse(prefs.getBoolean(feature.key, true));
            }
            root.findViewWithTag("nav_home").performClick();
            root.findViewWithTag("nav_settings").performClick();
            for (Switch toggle : switches(root)) assertFalse(toggle.isChecked());
            activity.onBackPressed();
            assertEquals(0, switches(root).size());
        }
    }

    @Test
    public void settingsSelectionSurvivesRecreation() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.get().findViewById(android.R.id.content).findViewWithTag("nav_settings").performClick();
            controller.recreate();
            assertEquals(11, switches(controller.get().findViewById(android.R.id.content)).size());
        }
    }

    @Test public void sidebarSliderPersistsForGestureConfiguration() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            SharedPreferences prefs = activity.getSharedPreferences(Feature.PREFS, Context.MODE_PRIVATE);
            prefs.edit().clear().commit();
            ReflectionHelpers.setField(activity, "preferences", prefs);
            View root = activity.findViewById(android.R.id.content);
            root.findViewWithTag("nav_settings").performClick();
            android.widget.SeekBar slider = root.findViewWithTag("sidebar_hold_slider");
            slider.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                    new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT));
            assertEquals(600, prefs.getInt(io.github.connectcolours.sidebar.SidebarGesture.HOLD_KEY, 0));
            root.findViewWithTag("nav_home").performClick();
            root.findViewWithTag("nav_settings").performClick();
            slider = root.findViewWithTag("sidebar_hold_slider");
            assertEquals(4, slider.getProgress());
            assertTrue(hasText(root, "侧边栏触发时间：0.6 秒"));
        }
    }

    @Test public void hiddenAppRowsPersistAndRecycleWithoutChangingOtherSelection() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            SharedPreferences prefs = activity.getSharedPreferences(Feature.PREFS, Context.MODE_PRIVATE);
            prefs.edit().clear().commit();
            ReflectionHelpers.setField(activity, "preferences", prefs);
            android.content.pm.ApplicationInfo a = new android.content.pm.ApplicationInfo();
            a.packageName = "example.a"; a.nonLocalizedLabel = "Same label";
            android.content.pm.ApplicationInfo b = new android.content.pm.ApplicationInfo();
            b.packageName = "example.b"; b.nonLocalizedLabel = "Same label";
            java.lang.reflect.Constructor<?> constructor = Class.forName(
                    "io.github.connectcolours.MainActivity$HiddenAppsAdapter")
                    .getDeclaredConstructor(MainActivity.class, List.class);
            constructor.setAccessible(true);
            android.widget.BaseAdapter adapter = (android.widget.BaseAdapter) constructor.newInstance(
                    activity, java.util.Arrays.asList(a, b));
            ViewGroup parent = activity.findViewById(android.R.id.content);
            View row = adapter.getView(0, null, parent);
            android.widget.CheckBox first = row.findViewWithTag("example.a");
            assertFalse(first.isChecked());
            first.performClick();
            assertTrue(io.github.connectcolours.recents.HiddenAppsConfig.read(prefs).contains("example.a"));
            View recycled = adapter.getView(1, row, parent);
            android.widget.CheckBox second = recycled.findViewWithTag("example.b");
            assertFalse(second.isChecked());
            assertTrue(io.github.connectcolours.recents.HiddenAppsConfig.read(prefs).contains("example.a"));
            second.performClick();
            row = adapter.getView(0, recycled, parent);
            first = row.findViewWithTag("example.a");
            assertTrue(first.isChecked());
            first.performClick();
            assertFalse(io.github.connectcolours.recents.HiddenAppsConfig.read(prefs).contains("example.a"));
            assertTrue(io.github.connectcolours.recents.HiddenAppsConfig.read(prefs).contains("example.b"));
        }
    }

    @Test public void hiddenAppsSearchKeepsSelectionAndSelectedAppsFirst() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            SharedPreferences prefs = activity.getSharedPreferences(Feature.PREFS, Context.MODE_PRIVATE);
            prefs.edit().clear().commit();
            ReflectionHelpers.setField(activity, "preferences", prefs);
            android.content.pm.ApplicationInfo a = new android.content.pm.ApplicationInfo();
            a.packageName = "example.a"; a.nonLocalizedLabel = "QQ 音乐";
            android.content.pm.ApplicationInfo b = new android.content.pm.ApplicationInfo();
            b.packageName = "example.b"; b.nonLocalizedLabel = "QQ 邮箱";
            io.github.connectcolours.recents.HiddenAppsConfig.setHidden(prefs, b.packageName, true);
            java.lang.reflect.Constructor<?> constructor = Class.forName(
                    "io.github.connectcolours.MainActivity$HiddenAppsAdapter")
                    .getDeclaredConstructor(MainActivity.class, List.class);
            constructor.setAccessible(true);
            android.widget.BaseAdapter adapter = (android.widget.BaseAdapter) constructor.newInstance(
                    activity, java.util.Arrays.asList(a, b));
            java.lang.reflect.Method search = adapter.getClass().getDeclaredMethod("setQuery", String.class);
            search.setAccessible(true);
            assertSame(b, adapter.getItem(0));
            search.invoke(adapter, " qq ");
            assertEquals(2, adapter.getCount());
            assertSame(b, adapter.getItem(0));
            search.invoke(adapter, "音乐");
            assertEquals(1, adapter.getCount());
            assertSame(a, adapter.getItem(0));
            ViewGroup parent = activity.findViewById(android.R.id.content);
            adapter.getView(0, null, parent).findViewWithTag(a.packageName).performClick();
            assertEquals(2, io.github.connectcolours.recents.HiddenAppsConfig.read(prefs).size());
            search.invoke(adapter, "不存在");
            assertEquals(0, adapter.getCount());
            search.invoke(adapter, "");
            assertEquals(2, adapter.getCount());
            assertSame(a, adapter.getItem(0));
            adapter.getView(0, null, parent).findViewWithTag(a.packageName).performClick();
            assertSame(b, adapter.getItem(0));
            assertSame(a, adapter.getItem(1));
            assertFalse(io.github.connectcolours.recents.HiddenAppsConfig.read(prefs).contains(a.packageName));
        }
    }

    private static List<Switch> switches(View view) {
        List<Switch> result = new ArrayList<>();
        if (view instanceof Switch) result.add((Switch) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) result.addAll(switches(group.getChildAt(i)));
        }
        return result;
    }

    private static boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), text)) return true;
        }
        return false;
    }
}

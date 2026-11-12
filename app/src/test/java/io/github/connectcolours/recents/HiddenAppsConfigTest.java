package io.github.connectcolours.recents;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HiddenAppsConfigTest {
    @Test public void startsEmptyAndPersistsIndependentSelections() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("selection", Context.MODE_PRIVATE);
        assertTrue(HiddenAppsConfig.read(prefs).isEmpty());
        assertTrue(HiddenAppsConfig.setHidden(prefs, "a", true));
        assertTrue(HiddenAppsConfig.setHidden(prefs, "b", true));
        assertTrue(HiddenAppsConfig.setHidden(prefs, "a", false));
        SharedPreferences reopened = RuntimeEnvironment.getApplication().getSharedPreferences("selection", Context.MODE_PRIVATE);
        assertFalse(HiddenAppsConfig.read(reopened).contains("a"));
        assertTrue(HiddenAppsConfig.read(reopened).contains("b"));
    }

    @Test public void snapshotsAndExistingFeatureSettingsAreNotMutated() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("snapshot", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("sidebar", false).commit();
        HiddenAppsConfig.setHidden(prefs, "a", true);
        HiddenAppsConfig.read(prefs).clear();
        assertTrue(HiddenAppsConfig.read(prefs).contains("a"));
        assertFalse(prefs.getBoolean("sidebar", true));
    }
}

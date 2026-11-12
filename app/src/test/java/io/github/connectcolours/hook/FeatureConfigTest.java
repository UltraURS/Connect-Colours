package io.github.connectcolours.hook;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.connectcolours.Feature;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class FeatureConfigTest {
    @Test public void upgradeEnablesSidebarWithoutChangingExistingChoices() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences(Feature.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Feature feature : Feature.values()) {
            if (feature != Feature.SIDEBAR) editor.putBoolean(feature.key, false);
        }
        assertTrue(editor.commit());
        FeatureConfig config = new FeatureConfig(prefs);
        for (Feature feature : Feature.values()) {
            assertEquals(feature == Feature.SIDEBAR, config.enabled(feature));
        }
    }

    @Test
    public void defaultOnAndEachSwitchPersistsIndependently() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = context.getSharedPreferences(Feature.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        FeatureConfig config = new FeatureConfig(prefs);
        for (Feature feature : Feature.values()) assertTrue(config.enabled(feature));
        for (Feature selected : Feature.values()) {
            assertTrue(prefs.edit().putBoolean(selected.key, false).commit());
            FeatureConfig reopened = new FeatureConfig(
                    context.getSharedPreferences(Feature.PREFS, Context.MODE_PRIVATE));
            for (Feature feature : Feature.values()) {
                assertEquals(feature != selected, reopened.enabled(feature));
            }
            assertTrue(prefs.edit().putBoolean(selected.key, true).commit());
        }
    }

    @Test
    public void allSwitchesCanBeDisabled() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences(Feature.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (Feature feature : Feature.values()) editor.putBoolean(feature.key, false);
        assertTrue(editor.commit());
        FeatureConfig config = new FeatureConfig(prefs);
        for (Feature feature : Feature.values()) assertFalse(config.enabled(feature));
    }
}

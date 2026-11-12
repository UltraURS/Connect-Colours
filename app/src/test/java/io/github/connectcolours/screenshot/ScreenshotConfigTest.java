package io.github.connectcolours.screenshot;

import android.content.Context;
import android.provider.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ScreenshotConfigTest {
    @Test public void tripleDefaultsOffAndEnablingItDisablesDual() {
        Context c = RuntimeEnvironment.getApplication();
        assertFalse(ScreenshotConfig.enabled(c));
        Settings.System.putInt(c.getContentResolver(), ScreenshotConfig.DUAL, 1);
        ScreenshotConfig.setTriple(c, true);
        assertTrue(ScreenshotConfig.enabled(c));
        assertEquals(0, Settings.System.getInt(c.getContentResolver(), ScreenshotConfig.DUAL, -1));
    }
    @Test public void bothCanBeOffWithoutRestoringDual() {
        Context c = RuntimeEnvironment.getApplication();
        ScreenshotConfig.setTriple(c, true);
        ScreenshotConfig.setTriple(c, false);
        assertFalse(ScreenshotConfig.enabled(c));
        assertEquals(0, Settings.System.getInt(c.getContentResolver(), ScreenshotConfig.DUAL, -1));
    }
    @Test public void externalDualEnableAlwaysWinsOverStaleTripleFlag() {
        Context c = RuntimeEnvironment.getApplication();
        ScreenshotConfig.setTriple(c, true);
        Settings.System.putInt(c.getContentResolver(), ScreenshotConfig.DUAL, 1);
        assertFalse(ScreenshotConfig.enabled(c));
    }
}

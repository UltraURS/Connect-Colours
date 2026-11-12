package io.github.connectcolours.bluetooth;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BluetoothConfigTest {
    private static final String WATCH = "C4:57:81:06:19:A2";
    private static final String OTHER = "00:11:22:33:44:55";
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 1);
    }

    @Test
    public void storesIndependentDevicesWithoutAModuleProvider() {
        // Regression: the old path could not even read defaults when ColorOS blocked startup.
        assertNull(context.getPackageManager().resolveContentProvider("io.github.connectcolours.bluetooth", 0));
        assertEquals("settings", BluetoothConfig.URI.getAuthority());
        Bundle defaults = BluetoothConfig.read(context, WATCH);
        assertFalse(defaults.getBoolean("enabled"));
        assertEquals(1, defaults.getInt("attempts"));
        assertFalse(defaults.getBoolean("paused"));
        defaults.putBoolean("enabled", true);
        defaults.putInt("attempts", 5);
        BluetoothConfig.save(context, WATCH, defaults);
        Context reopened = context.createDeviceProtectedStorageContext();
        assertEquals(5, BluetoothConfig.read(reopened, WATCH).getInt("attempts"));
        assertTrue(BluetoothConfig.read(reopened, WATCH).getBoolean("enabled"));
        assertFalse(BluetoothConfig.read(reopened, OTHER).getBoolean("enabled"));
        assertEquals(1, BluetoothConfig.read(reopened, OTHER).getInt("attempts"));
    }

    @Test
    public void pauseSurvivesNewContextUntilBluetoothResetOrReboot() {
        BluetoothConfig.save(context, WATCH, new Bundle());
        BluetoothConfig.pause(context, WATCH);
        Context reopened = context.createDeviceProtectedStorageContext();
        assertTrue(BluetoothConfig.read(reopened, WATCH).getBoolean("paused"));
        BluetoothConfig.reset(reopened);
        assertFalse(BluetoothConfig.read(context, WATCH).getBoolean("paused"));
        BluetoothConfig.pause(context, WATCH);
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 2);
        assertFalse(BluetoothConfig.read(context, WATCH).getBoolean("paused"));
    }

    @Test
    public void savingStaleUiValuesDoesNotOverwriteAManualPause() {
        BluetoothConfig.save(context, WATCH, new Bundle());
        Bundle old = BluetoothConfig.read(context, WATCH);
        BluetoothConfig.pause(context, WATCH);
        old.putInt("attempts", 3);
        BluetoothConfig.save(context, WATCH, old);
        Bundle saved = BluetoothConfig.read(context, WATCH);
        assertTrue(saved.getBoolean("paused"));
        assertEquals(3, saved.getInt("attempts"));
    }

    @Test
    public void unpairClearsOnlyThatDeviceIncludingItsPause() {
        Bundle values = new Bundle();
        values.putBoolean("enabled", true);
        values.putInt("attempts", 4);
        BluetoothConfig.save(context, WATCH, values);
        BluetoothConfig.save(context, OTHER, values);
        BluetoothConfig.pause(context, WATCH);
        BluetoothConfig.remove(context, WATCH);
        Bundle removed = BluetoothConfig.read(context, WATCH);
        assertFalse(removed.getBoolean("enabled"));
        assertFalse(removed.getBoolean("paused"));
        assertEquals(1, removed.getInt("attempts"));
        assertTrue(BluetoothConfig.read(context, OTHER).getBoolean("enabled"));
    }

    @Test
    public void observerIgnoresUnrelatedSystemSettings() {
        assertTrue(BluetoothConfig.isConfigChange(null));
        assertTrue(BluetoothConfig.isConfigChange(Settings.Secure.CONTENT_URI));
        assertTrue(BluetoothConfig.isConfigChange(Settings.Secure.getUriFor("connect_colours_bluetooth_session")));
        assertTrue(BluetoothConfig.isConfigChange(Settings.Secure.getUriFor("connect_colours_bluetooth_C457810619A2")));
        assertFalse(BluetoothConfig.isConfigChange(Settings.Secure.getUriFor("accessibility_enabled")));
        assertFalse(BluetoothConfig.isConfigChange(Uri.parse("content://other/secure/connect_colours_bluetooth_session")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCountIsRejected() {
        Bundle values = new Bundle();
        values.putInt("attempts", 0);
        BluetoothConfig.save(context, WATCH, values);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidAddressIsRejected() {
        BluetoothConfig.read(context, "not-a-device");
    }
}

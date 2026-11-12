package io.github.connectcolours.hook;

import android.database.MatrixCursor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class SettingsHookTest {
    @Test
    public void removesOnlyUpdateResultsAndPreservesOtherTypes() {
        MatrixCursor source = new MatrixCursor(new String[] {
                "key", "intentTargetPackage", "intentAction", "title", "rank", "blob", "score"
        });
        source.addRow(new Object[] {"key_ota_update", null, null, "软件更新", 1, null, null});
        source.addRow(new Object[] {"other", "com.oplus.ota", null, "更新", 2, null, null});
        source.addRow(new Object[] {"third", null, "com.oplus.ota.MAIN", "更新", 3, null, null});
        source.addRow(new Object[] {"wifi", "com.android.settings", null, "WLAN", 4L,
                new byte[] {1, 2}, 1.25});
        MatrixCursor filtered = SettingsHook.filterSearch(source);
        assertTrue(source.isClosed());
        assertEquals(1, filtered.getCount());
        assertTrue(filtered.moveToFirst());
        assertEquals("wifi", filtered.getString(0));
        assertNull(filtered.getString(2));
        assertEquals("WLAN", filtered.getString(3));
        assertEquals(4L, filtered.getLong(4));
        assertArrayEquals(new byte[] {1, 2}, filtered.getBlob(5));
        assertEquals(1.25, filtered.getDouble(6), 0);
        filtered.close();
    }

    @Test
    public void missingOptionalColumnsDoNotHideOrdinarySettings() {
        MatrixCursor source = new MatrixCursor(new String[] {"title"});
        source.addRow(new Object[] {"系统应用更新"});
        MatrixCursor filtered = SettingsHook.filterSearch(source);
        assertEquals(1, filtered.getCount());
        filtered.close();
    }

    @Test
    public void emptySearchKeepsItsSchema() {
        MatrixCursor source = new MatrixCursor(new String[] {"key", "title"});
        MatrixCursor filtered = SettingsHook.filterSearch(source);
        assertArrayEquals(new String[] {"key", "title"}, filtered.getColumnNames());
        assertEquals(0, filtered.getCount());
        filtered.close();
    }

    @Test
    public void unrelatedUpdatesAndAboutPhoneRemainAvailable() {
        assertFalse(UpdatePolicy.isUpdateEntry("my_device_info_pref_screen", null, null));
        assertFalse(UpdatePolicy.isUpdateEntry("app_update", "com.heytap.market", null));
        assertFalse(UpdatePolicy.isUpdateEntry("firmware_version", "com.android.settings", null));
        assertFalse(UpdatePolicy.isUpdateEntry(null, null, null));
        assertTrue(UpdatePolicy.isUpdateEntry("additional_system_update_settings", null, null));
        assertTrue(UpdatePolicy.isUpdateEntry(null, null, "android.settings.SYSTEM_UPDATE_SETTINGS"));
    }
}

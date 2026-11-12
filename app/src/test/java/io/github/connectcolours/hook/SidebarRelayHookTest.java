package io.github.connectcolours.hook;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SidebarRelayHookTest {
    private final FakeSidebar provider = new FakeSidebar();

    @Before public void registerProvider() {
        ShadowContentResolver.registerProviderInternal("com.coloros.sidebar", provider);
    }

    @Test public void forwardsBothDirectionsToFixedNativeMethod() {
        for (int side = 0; side <= 1; side++) {
            assertTrue(SidebarRelayHook.forward(RuntimeEnvironment.getApplication(), side));
            assertEquals(side, provider.side);
            assertEquals("connect_colours_open_sidebar", provider.method);
        }
    }

    @Test public void rejectsInvalidDirectionsBeforeCallingProvider() {
        assertFalse(SidebarRelayHook.forward(RuntimeEnvironment.getApplication(), -1));
        assertFalse(SidebarRelayHook.forward(RuntimeEnvironment.getApplication(), 2));
        assertNull(provider.method);
    }

    @Test public void missingHookOrRejectedRequestIsNotReportedAsAccepted() {
        provider.response = null;
        assertFalse(SidebarRelayHook.forward(RuntimeEnvironment.getApplication(), 0));
        provider.response = new Bundle();
        assertFalse(SidebarRelayHook.forward(RuntimeEnvironment.getApplication(), 1));
    }

    @Test(expected = SecurityException.class) public void permissionFailureIsNotReportedAsAccepted() {
        provider.deny = true;
        SidebarRelayHook.forward(RuntimeEnvironment.getApplication(), 0);
    }

    private static final class FakeSidebar extends ContentProvider {
        Bundle response = new Bundle();
        String method;
        int side;
        boolean deny;
        FakeSidebar() { response.putBoolean("accepted", true); }
        @Override public Bundle call(String method, String arg, Bundle extras) {
            if (deny) throw new SecurityException("Permission Denial");
            this.method = method;
            side = extras.getInt("side", -1);
            return response;
        }
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String s, String[] a) { return 0; }
        @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
    }
}

package io.github.connectcolours.hook;

import android.content.ComponentName;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SidebarLauncherTest {
    private final ComponentName home = new ComponentName("com.android.launcher", "com.android.launcher.Launcher");

    @Test public void allowsOnlyFocusedLauncherHome() {
        assertTrue(SidebarGestureHook.isHomeForeground(home, "com.android.launcher"));
        assertFalse(SidebarGestureHook.isHomeForeground(home, "com.android.systemui"));
        assertFalse(SidebarGestureHook.isHomeForeground(home, "com.coloros.smartsidebar"));
    }

    @Test public void doesNotOverrideGuideOrOtherApplications() {
        assertFalse(SidebarGestureHook.isHomeForeground(new ComponentName("com.android.launcher",
                "com.android.launcher.guide.side.SideSlipGesturesGuideActivity"), "com.android.launcher"));
        assertFalse(SidebarGestureHook.isHomeForeground(new ComponentName("com.example", "com.example.Main"),
                "com.example"));
    }

    @Test public void unknownForegroundDoesNotEnableDesktopException() {
        assertFalse(SidebarGestureHook.isHomeForeground(null, "com.android.launcher"));
        assertFalse(SidebarGestureHook.isHomeForeground(home, null));
        assertFalse(SidebarGestureHook.isHomeForeground(home, ""));
    }
}

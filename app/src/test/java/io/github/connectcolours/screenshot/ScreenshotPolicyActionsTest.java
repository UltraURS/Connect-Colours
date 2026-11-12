package io.github.connectcolours.screenshot;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenshotPolicyActionsTest {
    private static class Policy {
        final StringBuilder calls = new StringBuilder();
        Runnable duringScreenshot;
        private void cancelPendingScreenshotChordAction() { calls.append("s"); }
        private void cancelPendingAccessibilityShortcutAction() { calls.append("a"); }
        private void cancelPendingRingerToggleChordAction() { calls.append("r"); }
        private void cancelGlobalActionsAction() { calls.append("g"); }
        private void handleScreenShot(int type, int source) {
            calls.append(type).append(source);
            if (duringScreenshot != null) duringScreenshot.run();
        }
    }

    private static class SubPolicy extends Policy {
        private void handleScreenShot(int type, int source) { fail("Must invoke base policy method"); }
    }

    @Test public void privateBaseActionsWorkOnVendorSubclass() throws Exception {
        SubPolicy target = new SubPolicy();
        ScreenshotPolicyActions actions = new ScreenshotPolicyActions(Policy.class);
        actions.cancelPending(target);
        actions.takeScreenshot(target);
        assertEquals("sarg12", target.calls.toString());
    }

    @Test(expected = NoSuchMethodException.class)
    public void incompatiblePolicyFailsBeforeHooksAreInstalled() throws Exception {
        new ScreenshotPolicyActions(SubPolicy.class);
    }

    @Test public void customSourceIsLimitedToRequestThreadAndLifetime() throws Exception {
        ScreenshotPolicyActions actions = new ScreenshotPolicyActions(Policy.class);
        Policy target = new SubPolicy();
        java.util.concurrent.atomic.AtomicBoolean otherThread = new java.util.concurrent.atomic.AtomicBoolean(true);
        target.duringScreenshot = () -> {
            assertTrue(actions.isTakingScreenshot());
            Thread thread = new Thread(() -> otherThread.set(actions.isTakingScreenshot()));
            thread.start();
            try { thread.join(); }
            catch (InterruptedException error) { throw new AssertionError(error); }
        };
        assertFalse(actions.isTakingScreenshot());
        actions.takeScreenshot(target);
        assertFalse(otherThread.get());
        assertFalse(actions.isTakingScreenshot());
    }

    @Test public void failedScreenshotDoesNotRelabelLaterNativeRequests() throws Exception {
        ScreenshotPolicyActions actions = new ScreenshotPolicyActions(Policy.class);
        Policy target = new SubPolicy();
        target.duringScreenshot = () -> { throw new IllegalStateException("test failure"); };
        try {
            actions.takeScreenshot(target);
            fail("Expected screenshot failure");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        }
        assertFalse(actions.isTakingScreenshot());
    }
}

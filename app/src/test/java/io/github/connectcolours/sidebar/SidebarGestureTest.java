package io.github.connectcolours.sidebar;

import org.junit.Test;
import static org.junit.Assert.*;

public class SidebarGestureTest {
    @Test public void configurableHoldResetsOnRetreatAndHonorsBounds() {
        for (int duration : new int[] {200, 500, 1000}) {
            SidebarGesture gesture = new SidebarGesture(duration);
            gesture.begin(true);
            assertFalse(gesture.advance(1000, true));
            assertFalse(gesture.advance(1000 + duration - 1, true));
            gesture.advance(1000 + duration - 1, false);
            assertFalse(gesture.advance(2000, true));
            assertEquals(duration, gesture.remainingMillis(2000));
            assertTrue(gesture.advance(2000 + duration, true));
        }
        SidebarGesture low = new SidebarGesture(0);
        low.begin(false); low.advance(0, true);
        assertEquals(200, low.remainingMillis(0));
        SidebarGesture high = new SidebarGesture(5000);
        high.begin(false); high.advance(0, true);
        assertEquals(1000, high.remainingMillis(0));
    }

    @Test public void armsAfter500MillisecondsAtMaximumOnBothSides() {
        for (boolean left : new boolean[] {true, false}) {
            SidebarGesture gesture = new SidebarGesture();
            gesture.begin(left);
            assertFalse(gesture.advance(100, false));
            assertFalse(gesture.advance(2000, false));
            assertEquals(-1, gesture.remainingMillis(2000));
            assertFalse(gesture.advance(3000, true));
            assertEquals(500, gesture.remainingMillis(3000));
            assertFalse(gesture.advance(3499, true));
            assertTrue(gesture.advance(3500, true));
            assertFalse(gesture.advance(4000, true));
            assertEquals(left, gesture.fromLeft());
            assertTrue(gesture.release());
            assertFalse(gesture.release());
        }
    }

    @Test public void retreatClearsElapsedTimeAndReentryStartsFresh() {
        SidebarGesture gesture = new SidebarGesture();
        gesture.begin(true);
        assertFalse(gesture.advance(100, true));
        gesture.begin(false);
        assertFalse(gesture.advance(599, false));
        assertEquals(-1, gesture.remainingMillis(599));
        assertFalse(gesture.advance(600, true));
        assertFalse(gesture.advance(1099, true));
        assertTrue(gesture.advance(1100, true));
        assertTrue(gesture.fromLeft());
    }

    @Test public void reverseCannotRearmUntilNewTouch() {
        SidebarGesture gesture = new SidebarGesture();
        gesture.begin(true);
        gesture.advance(100, true);
        assertTrue(gesture.advance(600, true));
        gesture.reverse();
        gesture.begin(false);
        assertFalse(gesture.advance(2000, true));
        assertFalse(gesture.shouldReturnHome(true, true));
        assertFalse(gesture.release());
        gesture.reset();
        gesture.begin(false);
        gesture.advance(2100, true);
        assertTrue(gesture.advance(2600, true));
        assertTrue(gesture.release());
        assertFalse(gesture.fromLeft());
    }

    @Test public void interruptionsCannotOpenSidebarOrReturnHome() {
        SidebarGesture gesture = new SidebarGesture();
        gesture.begin(true);
        gesture.advance(100, true);
        gesture.cancel();
        assertFalse(gesture.advance(600, true));
        assertFalse(gesture.shouldReturnHome(true, true));
        assertFalse(gesture.release());
        gesture.reset();
        assertFalse(gesture.advance(1000, true));
        assertFalse(gesture.shouldReturnHome(true, true));
    }

    @Test public void onlyValidUnarmedHomeGestureCanReturnHome() {
        SidebarGesture gesture = new SidebarGesture();
        gesture.begin(true);
        assertTrue(gesture.shouldReturnHome(true, true));
        assertFalse(gesture.shouldReturnHome(false, true));
        assertFalse(gesture.shouldReturnHome(true, false));
        gesture.advance(100, true);
        gesture.advance(599, true);
        assertTrue(gesture.shouldReturnHome(true, true));
        gesture.advance(600, true);
        assertFalse(gesture.shouldReturnHome(true, true));
        assertTrue(gesture.release());
        assertFalse(gesture.shouldReturnHome(true, true));
    }

    @Test public void maximumMatchesNativeLeftAndRightVisualLimits() {
        assertFalse(SidebarGesture.atMaximum(0, 1000, 100, true));
        assertFalse(SidebarGesture.atMaximum(309, 1000, 100, true));
        assertTrue(SidebarGesture.atMaximum(310, 1000, 100, true));
        assertFalse(SidebarGesture.atMaximum(1000, 1000, 100, false));
        assertFalse(SidebarGesture.atMaximum(694, 1000, 100, false));
        assertTrue(SidebarGesture.atMaximum(690, 1000, 100, false));
    }
}

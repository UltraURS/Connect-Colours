package io.github.connectcolours.screenshot;

import org.junit.Test;
import static org.junit.Assert.*;

public class TripleChordTest {
    @Test public void sharedReleaseDownTimeAllowsRepeatedChords() {
        TripleChord chord = new TripleChord();
        for (int cycle = 0; cycle < 3; cycle++) {
            long start = 100 + cycle * 300;
            chord.event(26, true, 0, false, 2, start, start);
            chord.event(25, true, 0, false, 2, start + 10, start + 10);
            assertTrue(chord.event(24, true, 0, false, 2, start + 20, start + 20));
            for (int key = 24; key <= 26; key++) {
                chord.event(key, false, 0, false, 2, start + 20, start + 100);
                assertTrue(chord.consumes(key, 2, start + 20));
            }
            assertTrue(chord.consumes(26, 2, start));
        }
    }

    @Test public void sharedReleaseBeforeCompletionStillInvalidatesChord() {
        TripleChord chord = new TripleChord();
        chord.event(26, true, 0, false, 2, 100, 100);
        chord.event(25, true, 0, false, 2, 110, 110);
        chord.event(26, false, 0, false, 2, 110, 120);
        assertFalse(chord.event(24, true, 0, false, 2, 130, 130));
    }
    @Test public void allSixOrdersTriggerOnceAndConsumeMatchingReleases() {
        int[][] orders = {{24,25,26},{24,26,25},{25,24,26},{25,26,24},{26,24,25},{26,25,24}};
        for (int[] order : orders) {
            TripleChord chord = new TripleChord();
            for (int i = 0; i < 3; i++) {
                assertEquals(i == 2, chord.event(order[i], true, 0, false, 2, 100 + i * 30, 100 + i * 30));
            }
            assertTrue(chord.triggered());
            assertFalse(chord.event(order[2], true, 1, false, 2, 160, 200));
            for (int i = 0; i < 3; i++) {
                assertTrue(chord.consumes(order[i], 2, 100 + i * 30));
                assertFalse(chord.event(order[i], false, 0, false, 2, 100 + i * 30, 220));
                assertTrue(chord.consumes(order[i], 2, 100 + i * 30));
                assertFalse(chord.consumes(order[i], 3, 100 + i * 30));
            }
        }
    }
    @Test public void lateThirdKeyDoesNotTrigger() {
        TripleChord chord = new TripleChord();
        chord.event(26, true, 0, false, 1, 100, 100);
        chord.event(25, true, 0, false, 1, 120, 120);
        assertFalse(chord.event(24, true, 0, false, 1, 251, 251));
        assertFalse(chord.triggered());
        assertEquals(0, chord.remaining(252));
    }
    @Test public void releaseBeforeThirdKeyPreventsTrigger() {
        TripleChord chord = new TripleChord();
        chord.event(26, true, 0, false, 1, 100, 100);
        chord.event(25, true, 0, false, 1, 110, 110);
        chord.event(26, false, 0, false, 1, 100, 120);
        assertFalse(chord.event(24, true, 0, false, 1, 130, 130));
        assertFalse(chord.event(26, true, 0, false, 1, 140, 140));
    }
    @Test public void separateBuiltInDriversCanCompleteChord() {
        TripleChord chord = new TripleChord();
        chord.event(26, true, 0, false, 1, 100, 100);
        chord.event(25, true, 0, false, 2, 110, 110);
        assertTrue(chord.event(24, true, 0, false, 2, 120, 120));
        assertTrue(chord.consumes(26, 1, 100));
        assertTrue(chord.consumes(25, 2, 110));
        assertFalse(chord.consumes(26, 2, 100));
    }
    @Test public void unrelatedReleaseCannotClearHeldKey() {
        TripleChord chord = new TripleChord();
        chord.event(26, true, 0, false, 1, 100, 100);
        chord.event(26, false, 0, false, 2, 100, 105);
        chord.event(26, false, 0, false, 1, 99, 106);
        chord.event(25, true, 0, false, 2, 110, 110);
        assertTrue(chord.event(24, true, 0, false, 2, 120, 120));
    }
    @Test public void canceledKeysCannotCompleteChord() {
        TripleChord chord = new TripleChord();
        chord.event(26, true, 0, false, 1, 100, 100);
        chord.event(25, true, 0, true, 2, 110, 110);
        assertFalse(chord.event(24, true, 0, false, 2, 120, 120));
    }
    @Test public void resetAndFullReleaseAllowAnotherChord() {
        TripleChord chord = new TripleChord();
        for (int cycle = 0; cycle < 2; cycle++) {
            for (int key = 24; key <= 26; key++) {
                assertEquals(key == 26, chord.event(key, true, 0, false, 1, 100 + cycle * 300, 100 + cycle * 300));
            }
            for (int key = 24; key <= 26; key++) chord.event(key, false, 0, false, 1, 100 + cycle * 300, 200 + cycle * 300);
        }
        chord.reset();
        assertFalse(chord.triggered());
        assertFalse(chord.consumes(24, 1, 400));
    }
    @Test public void twoKeysOnlyHaveABoundedDecisionWindow() {
        TripleChord chord = new TripleChord();
        chord.event(24, true, 0, false, 1, 100, 100);
        chord.event(25, true, 0, false, 1, 120, 120);
        assertEquals(130, chord.remaining(120));
        assertEquals(0, chord.remaining(251));
        assertFalse(chord.triggered());
    }
}

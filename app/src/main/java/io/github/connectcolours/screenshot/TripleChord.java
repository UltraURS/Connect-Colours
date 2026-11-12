package io.github.connectcolours.screenshot;

/** Tracks built-in keys, which may be reported by separate input drivers. */
public final class TripleChord {
    public static final long WINDOW = 150;
    private final long[] pressed = {-1, -1, -1};
    private final long[] consumed = {-1, -1, -1};
    private final long[] consumedRelease = {-1, -1, -1};
    private int mask;
    private final int[] devices = new int[3];
    private final int[] consumedDevices = new int[3];
    private long first;
    private boolean failed;
    private boolean fired;

    private int index(int key) { return key >= 24 && key <= 26 ? key - 24 : -1; }

    public synchronized boolean event(int key, boolean down, int repeat, boolean canceled,
                                      int deviceId, long downTime, long now) {
        int index = index(key);
        if (index < 0) return false;
        if (mask == 0 && down && repeat == 0) {
            first = now;
            failed = false;
            fired = false;
        }
        if (!down) {
            if ((mask & (1 << index)) == 0 || devices[index] != deviceId
                    || downTime < pressed[index]) return false;
            // A keyboard driver can report the latest chord key's downTime on UP.
            if (fired) consumedRelease[index] = downTime;
            mask &= ~(1 << index);
            pressed[index] = -1;
            failed = true;
            return false;
        }
        if (canceled) failed = true;
        if (repeat != 0 || (mask & (1 << index)) != 0) return false;
        pressed[index] = downTime;
        devices[index] = deviceId;
        mask |= 1 << index;
        if (now - first > WINDOW) failed = true;
        if (mask == 7 && !failed && !fired) {
            fired = true;
            System.arraycopy(devices, 0, consumedDevices, 0, 3);
            System.arraycopy(pressed, 0, consumed, 0, 3);
            for (int i = 0; i < 3; i++) consumedRelease[i] = -1;
            return true;
        }
        return false;
    }

    public synchronized boolean consumes(int key, int deviceId, long downTime) {
        int index = index(key);
        return index >= 0 && consumedDevices[index] == deviceId
                && (consumed[index] == downTime || consumedRelease[index] == downTime);
    }

    public synchronized long remaining(long now) {
        return mask != 0 && !failed && !fired ? Math.max(0, first + WINDOW - now) : 0;
    }

    public synchronized boolean triggered() { return fired; }

    public synchronized void reset() {
        mask = 0;
        failed = true;
        fired = false;
        for (int i = 0; i < 3; i++) { pressed[i] = -1; consumed[i] = -1; consumedRelease[i] = -1; }
    }
}

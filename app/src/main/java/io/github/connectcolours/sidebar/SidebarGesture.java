package io.github.connectcolours.sidebar;

/** One edge gesture, driven on the gesture handler thread with monotonic time. */
public final class SidebarGesture {
    public static final String HOLD_KEY = "sidebar_hold_millis";
    public static final int DEFAULT_HOLD_MILLIS = 500;
    private final long holdMillis;

    public SidebarGesture() { this(DEFAULT_HOLD_MILLIS); }
    public SidebarGesture(long holdMillis) {
        this.holdMillis = Math.max(200, Math.min(1000, holdMillis));
    }
    public enum State { IDLE, TRACKING, READY, CANCELED, FINISHED }

    private State state = State.IDLE;
    private long startedAt = -1;
    private boolean left;

    /** Call on the first inward movement, not on the initial stationary touch. */
    public void begin(boolean fromLeft) {
        if (state == State.TRACKING || state == State.READY || state == State.CANCELED) return;
        startedAt = -1;
        left = fromLeft;
        state = State.TRACKING;
    }

    /** Called by the timer or motion handler; true requests one haptic/icon update. */
    public boolean advance(long now, boolean atMaximum) {
        if (state != State.TRACKING) return false;
        if (!atMaximum) {
            startedAt = -1;
            return false;
        }
        if (startedAt < 0) startedAt = now;
        if (now - startedAt < holdMillis) return false;
        state = State.READY;
        return true;
    }

    public long remainingMillis(long now) {
        return state == State.TRACKING && startedAt >= 0
                ? Math.max(0, holdMillis - (now - startedAt)) : -1;
    }

    /** Mirror the native nav view's integer rounding and maximum visual displacement. */
    public static boolean atMaximum(float x, int width, int maxAnimationDistance, boolean left) {
        return left ? (int) (x * 0.32299998f) >= maxAnimationDistance
                : (int) (width - (width - x) * 0.32299998f) <= width - maxAnimationDistance;
    }

    public boolean shouldReturnHome(boolean home, boolean validBackPosition) {
        return home && validBackPosition && state == State.TRACKING;
    }

    /** Reverse movement after READY cancels the whole touch, even if moved inward again. */
    public void reverse() {
        if (state == State.READY) state = State.CANCELED;
    }

    public void cancel() {
        if (state == State.TRACKING || state == State.READY) state = State.CANCELED;
    }

    /** True only once, when releasing a ready gesture. Read fromLeft before launching. */
    public boolean release() {
        boolean open = state == State.READY;
        state = State.FINISHED;
        return open;
    }

    /** Reset on a fresh ACTION_DOWN or when detaching the gesture detector. */
    public void reset() { state = State.IDLE; startedAt = -1; }
    public State state() { return state; }
    public boolean fromLeft() { return left; }
}

package io.github.connectcolours.bluetooth;

/** One bounded reconnection round, driven by elapsed realtime. */
public final class RetryRound {
    public enum State { READY, WAITING, DELAY, CONNECTED, EXHAUSTED, PAUSED }
    public static final long TIMEOUT = 15_000;
    public static final long INTERVAL = 10_000;
    private State state;
    private int attempts;
    private final int limit;
    private long deadline;

    public RetryRound(int limit, long now, boolean connected) {
        if (limit < 1 || limit > 5) throw new IllegalArgumentException("Invalid attempt limit");
        this.limit = limit;
        state = connected ? State.CONNECTED : State.READY;
        deadline = now;
    }

    public boolean poll(long now, boolean connected) {
        if (state == State.PAUSED) return false;
        if (connected) { state = State.CONNECTED; return false; }
        if (state == State.WAITING && now >= deadline) {
            state = attempts >= limit ? State.EXHAUSTED : State.DELAY;
            deadline = now + INTERVAL;
        }
        if ((state == State.READY || state == State.DELAY) && now >= deadline) {
            attempts++;
            state = State.WAITING;
            deadline = now + TIMEOUT;
            return true;
        }
        return false;
    }

    public void disconnected(long now) {
        // Failed connection broadcasts cannot reset a round or its exhausted budget.
        if (state == State.CONNECTED) {
            attempts = 0;
            state = State.READY;
            deadline = now;
        }
    }

    public void pause() { state = State.PAUSED; }
    public void failed(long now) {
        if (state != State.WAITING) return;
        state = attempts >= limit ? State.EXHAUSTED : State.DELAY;
        deadline = now + INTERVAL;
    }
    public State state() { return state; }
    public int attempts() { return attempts; }
    public long nextDeadline() {
        return state == State.READY || state == State.WAITING || state == State.DELAY ? deadline : Long.MAX_VALUE;
    }
}

package io.github.connectcolours.bluetooth;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReconnectMessagesTest {
    @Test
    public void attemptsIncludeDeviceAndProgress() {
        assertEquals("正在重连 手表（2/5）", ReconnectMessages.attempt("手表", 2, 5));
        assertEquals("正在重连 蓝牙设备（1/1）", ReconnectMessages.attempt(null, 1, 1));
    }

    @Test
    public void intermediateFailuresAndRepeatedEventsRemainQuiet() {
        assertNull(ReconnectMessages.outcome(RetryRound.State.WAITING, RetryRound.State.DELAY, 1, "手表"));
        assertNull(ReconnectMessages.outcome(RetryRound.State.CONNECTED, RetryRound.State.CONNECTED, 1, "手表"));
        assertNull(ReconnectMessages.outcome(RetryRound.State.EXHAUSTED, RetryRound.State.EXHAUSTED, 5, "手表"));
        assertNull(ReconnectMessages.outcome(RetryRound.State.READY, RetryRound.State.CONNECTED, 0, "手表"));
        assertNull(ReconnectMessages.outcome(RetryRound.State.PAUSED, RetryRound.State.CONNECTED, 1, "手表"));
    }

    @Test
    public void successAndFinalFailureProduceOneResult() {
        assertEquals("手表：重连成功", ReconnectMessages.outcome(
                RetryRound.State.WAITING, RetryRound.State.CONNECTED, 1, "手表"));
        assertEquals("手表：重连失败，已尝试 5 次", ReconnectMessages.outcome(
                RetryRound.State.WAITING, RetryRound.State.EXHAUSTED, 5, "手表"));
    }
}

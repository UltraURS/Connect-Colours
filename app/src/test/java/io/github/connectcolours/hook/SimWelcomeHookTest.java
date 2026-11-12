package io.github.connectcolours.hook;

import org.junit.Test;
import static org.junit.Assert.*;

public class SimWelcomeHookTest {
    @Test
    public void recognizesWelcomeInBothScripts() {
        assertTrue(SimWelcomeHook.isWelcome("感谢您使用中国移动"));
        assertTrue(SimWelcomeHook.isWelcome(" 感谢您使用\n中国联通 "));
        assertTrue(SimWelcomeHook.isWelcome("感謝您使用中國電信"));
    }

    @Test
    public void recognizesReportedChinaMobileWelcome() {
        assertTrue(SimWelcomeHook.isWelcome("尊敬的客户：感谢您选择中国移动通信！"));
        assertTrue(SimWelcomeHook.isWelcome("尊敬的客户： 感谢您选择中国移动通信！"));
        assertTrue(SimWelcomeHook.isWelcome("尊敬的客户:\n感谢您选择中国移动通信!"));
        assertFalse(SimWelcomeHook.isWelcome("尊敬的客户：感谢您选择中国移动通信！是否订购套餐？"));
        assertFalse(SimWelcomeHook.isWelcome("尊敬的客户：请输入验证码"));
    }

    @Test
    public void leavesOtherSimMessagesUntouched() {
        assertFalse(SimWelcomeHook.isWelcome(null));
        assertFalse(SimWelcomeHook.isWelcome(""));
        assertFalse(SimWelcomeHook.isWelcome("请输入 PIN 码"));
        assertFalse(SimWelcomeHook.isWelcome("是否订购套餐？"));
        assertFalse(SimWelcomeHook.isWelcome("余额不足"));
        assertFalse(SimWelcomeHook.isWelcome("验证码：123456"));
    }
}

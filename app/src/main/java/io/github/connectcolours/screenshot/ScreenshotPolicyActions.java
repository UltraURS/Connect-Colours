package io.github.connectcolours.screenshot;

import java.lang.reflect.Method;

public final class ScreenshotPolicyActions {
    private final Method[] cancel;
    private final Method screenshot;
    private final ThreadLocal<Boolean> takingScreenshot = new ThreadLocal<>();

    public ScreenshotPolicyActions(Class<?> policy) throws NoSuchMethodException {
        cancel = new Method[] {
                resolve(policy, "cancelPendingScreenshotChordAction"),
                resolve(policy, "cancelPendingAccessibilityShortcutAction"),
                resolve(policy, "cancelPendingRingerToggleChordAction"),
                resolve(policy, "cancelGlobalActionsAction")
        };
        screenshot = resolve(policy, "handleScreenShot", int.class, int.class);
    }

    private static Method resolve(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        // These private base methods are not inherited by SubPhoneWindowManager.
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    public void cancelPending(Object target) throws ReflectiveOperationException {
        for (Method method : cancel) method.invoke(target);
    }

    public void takeScreenshot(Object target) throws ReflectiveOperationException {
        takingScreenshot.set(true);
        try { screenshot.invoke(target, 1, 2); }
        finally { takingScreenshot.remove(); }
    }

    public boolean isTakingScreenshot() {
        return Boolean.TRUE.equals(takingScreenshot.get());
    }
}

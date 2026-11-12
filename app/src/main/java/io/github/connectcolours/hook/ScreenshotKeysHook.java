package io.github.connectcolours.hook;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.InputDevice;
import java.lang.reflect.Member;
import java.util.IdentityHashMap;
import java.util.Map;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.connectcolours.screenshot.ScreenshotConfig;
import io.github.connectcolours.screenshot.ScreenshotPolicyActions;
import io.github.connectcolours.screenshot.TripleChord;

final class ScreenshotKeysHook {
    private static final TripleChord chord = new TripleChord();
    private static final Map<Object, Runnable> pending = new IdentityHashMap<>();
    private static volatile boolean enabled;
    private static Handler handler;
    private static Context context;
    private static int diagnosticEvents;

    static void install(ClassLoader loader) throws NoSuchMethodException {
        if (!"PHJ110_11_C.13".equals(Build.DISPLAY)) {
            HookLog.info("Unsupported screenshot key firmware " + Build.DISPLAY);
            return;
        }
        String base = "com.android.server.policy.PhoneWindowManager";
        String sub = "com.android.server.policy.SubPhoneWindowManager";
        // Resolve required targets before installing anything in system_server.
        Class<?> policy = XposedHelpers.findClass(base, loader);
        ScreenshotPolicyActions actions = new ScreenshotPolicyActions(policy);
        Class<?> screenshotHandler = XposedHelpers.findClass(
                "com.android.server.wm.OplusDisplayPolicy$OplusScreenShotHandler", loader);
        XposedHelpers.findMethodExact(screenshotHandler, "getSource");
        XposedHelpers.findMethodExact(policy, "handleKeyGesture", KeyEvent.class, boolean.class);
        XposedHelpers.findClass(sub, loader);
        for (int number = 7; number <= 9; number++) {
            Class<?> rule = XposedHelpers.findClass(base + "$" + number, loader);
            XposedHelpers.findMethodExact(rule, "execute");
            XposedHelpers.findMethodExact(rule, "cancel");
        }
        // Only our synchronous native request gets a distinct source. KeyPress is
        // rejected by the screenshot app when the mutually exclusive dual switch is off.
        XposedHelpers.findAndHookMethod(screenshotHandler, "getSource", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (actions.isTakingScreenshot()) {
                    param.setResult("ConnectColoursThreeKeys");
                    HookLog.info("Three-key screenshot source applied");
                }
            }
        });
        XposedHelpers.findAndHookMethod(sub, loader, "init", Context.class,
                "android.view.IWindowManager", "com.android.server.policy.WindowManagerPolicy$WindowManagerFuncs",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) return;
                        context = (Context) param.args[0];
                        handler = (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler");
                        ContentObserver observer = new ContentObserver(handler) {
                            @Override public void onChange(boolean selfChange) { refresh(); }
                        };
                        context.getContentResolver().registerContentObserver(
                                Settings.Secure.getUriFor(ScreenshotConfig.TRIPLE), false, observer);
                        context.getContentResolver().registerContentObserver(
                                Settings.System.getUriFor(ScreenshotConfig.DUAL), false, observer);
                        refresh();
                        HookLog.info("Screenshot key policy initialized");
                    }
                });
        XposedHelpers.findAndHookMethod(sub, loader, "interceptKeyBeforeQueueing", KeyEvent.class, int.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!enabled || context == null) return;
                        KeyEvent event = (KeyEvent) param.args[0];
                        if (event.getKeyCode() < 24 || event.getKeyCode() > 26) return;
                        try {
                            InputDevice input = event.getDevice();
                            if (event.getRepeatCount() == 0 && diagnosticEvents++ < 18) {
                                HookLog.info("Screenshot key: code=" + event.getKeyCode()
                                        + " action=" + event.getAction() + " device=" + event.getDeviceId()
                                        + " external=" + (input == null ? "unknown" : input.isExternal())
                                        + " downTime=" + event.getDownTime()
                                        + " time=" + event.getEventTime());
                            }
                            if (!context.getSystemService(PowerManager.class).isInteractive()
                                    || XposedHelpers.getIntField(param.thisObject, "mCurrentUserId") != 0) {
                                chord.reset();
                                return;
                            }
                            if ((event.getFlags() & KeyEvent.FLAG_FALLBACK) != 0 || event.getDeviceId() < 0
                                    || input == null || input.isExternal()) return;
                            boolean fire = chord.event(event.getKeyCode(), event.getAction() == KeyEvent.ACTION_DOWN,
                                    event.getRepeatCount(), event.isCanceled(), event.getDeviceId(),
                                    event.getDownTime(), event.getEventTime());
                            boolean consumed = chord.consumes(event.getKeyCode(), event.getDeviceId(), event.getDownTime());
                            param.setObjectExtra("cc_consumed", consumed);
                            if (fire || (consumed && event.getKeyCode() == KeyEvent.KEYCODE_POWER)) {
                                XposedHelpers.setBooleanField(param.thisObject, "mPowerKeyHandled", true);
                                XposedHelpers.callMethod(XposedHelpers.getObjectField(param.thisObject,
                                        "mSingleKeyGestureDetector"), "reset");
                            }
                            if (fire) {
                                HookLog.info("Three-key chord recognized");
                                cancelDeferred();
                                actions.cancelPending(param.thisObject);
                                Object target = param.thisObject;
                                handler.post(() -> {
                                    try {
                                        actions.takeScreenshot(target);
                                        HookLog.info("Three-key screenshot request sent");
                                    }
                                    catch (Throwable error) { HookLog.error("Three-key screenshot", error); }
                                });
                            }
                        } catch (Throwable error) {
                            chord.reset();
                            HookLog.error("Screenshot key event", error);
                        }
                    }
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        // Still run native key-up handling to release wake locks and reset native state.
                        if (!param.hasThrowable() && Boolean.TRUE.equals(param.getObjectExtra("cc_consumed"))) param.setResult(0);
                    }
                });
        XposedHelpers.findAndHookMethod(policy, "handleKeyGesture", KeyEvent.class, boolean.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!enabled || param.hasThrowable()) return;
                        KeyEvent event = (KeyEvent) param.args[0];
                        if (!chord.consumes(event.getKeyCode(), event.getDeviceId(), event.getDownTime())) return;
                        // The power-last path can overwrite mPowerKeyHandled in handleCameraGesture.
                        // Restore it after native gesture bookkeeping, before power down/up handling.
                        if (event.getKeyCode() == KeyEvent.KEYCODE_POWER) {
                            XposedHelpers.setBooleanField(param.thisObject, "mPowerKeyHandled", true);
                        }
                        XposedHelpers.callMethod(XposedHelpers.getObjectField(param.thisObject,
                                "mSingleKeyGestureDetector"), "reset");
                    }
                });
        XposedHelpers.findAndHookMethod(sub, loader, "interceptKeyBeforeDispatching", IBinder.class,
                KeyEvent.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!enabled) return;
                        KeyEvent event = (KeyEvent) param.args[1];
                        if (event.getKeyCode() < 24 || event.getKeyCode() > 26) return;
                        if (chord.consumes(event.getKeyCode(), event.getDeviceId(), event.getDownTime())) {
                            param.setResult(-1L);
                        } else {
                            long delay = chord.remaining(SystemClock.uptimeMillis());
                            if (delay > 0) param.setResult(delay);
                        }
                    }
                });
        for (int number = 7; number <= 9; number++) {
            final boolean screenshot = number == 7;
            String rule = base + "$" + number;
            XposedHelpers.findAndHookMethod(rule, loader, "execute", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!enabled) return;
                    if (screenshot || chord.triggered()) { param.setResult(null); return; }
                    long delay = chord.remaining(SystemClock.uptimeMillis());
                    if (delay <= 0) return;
                    Object target = param.thisObject;
                    Member method = param.method;
                    synchronized (pending) {
                        Runnable task = () -> {
                            synchronized (pending) { pending.remove(target); }
                            if (chord.triggered()) return;
                            try { XposedBridge.invokeOriginalMethod(method, target, new Object[0]); }
                            catch (Throwable error) { HookLog.error("Deferred native key chord", error); }
                        };
                        Runnable old = pending.put(target, task);
                        if (old != null) handler.removeCallbacks(old);
                        handler.postDelayed(task, delay + 1);
                    }
                    param.setResult(null);
                }
            });
            XposedHelpers.findAndHookMethod(rule, loader, "cancel", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    synchronized (pending) {
                        Runnable task = pending.remove(param.thisObject);
                        if (task != null) handler.removeCallbacks(task);
                    }
                }
            });
        }
        HookLog.info("Three-key screenshot hooks installed (ColorOS 13)");
    }

    private static void refresh() {
        try {
            enabled = ScreenshotConfig.enabled(context);
            HookLog.info("Screenshot keys configuration: enabled=" + enabled
                    + " triple=" + Settings.Secure.getInt(context.getContentResolver(), ScreenshotConfig.TRIPLE, 0)
                    + " dual=" + Settings.System.getInt(context.getContentResolver(), ScreenshotConfig.DUAL, 1));
            diagnosticEvents = 0;
        }
        catch (Throwable error) { enabled = false; HookLog.error("Read screenshot configuration", error); }
        chord.reset();
        cancelDeferred();
    }

    private static void cancelDeferred() {
        synchronized (pending) {
            for (Runnable task : pending.values()) handler.removeCallbacks(task);
            pending.clear();
        }
    }
}

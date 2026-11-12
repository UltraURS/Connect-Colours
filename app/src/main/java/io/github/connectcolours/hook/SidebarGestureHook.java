package io.github.connectcolours.hook;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Region;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import io.github.connectcolours.sidebar.SidebarGesture;

final class SidebarGestureHook {
    private static final String PREFIX = "com.oplusos.systemui.navigationbar.gesture.sidegesture.";
    private static final String STATE = "cc_sidebar_state";
    private static final String ICON = "cc_sidebar_icon";
    private static final String READY = "cc_sidebar_ready";
    private static Class<?> util;
    private static int holdMillis;
    private static int diagnosticStarts;
    private static final ThreadLocal<Boolean> homeCheck = new ThreadLocal<>();

    static void install(ClassLoader loader, int configuredHoldMillis) {
        holdMillis = configuredHoldMillis;
        Class<?> detector = XposedHelpers.findClass(PREFIX + "SideGestureDetector", loader);
        Class<?> config = XposedHelpers.findClass(PREFIX + "SideGestureConfiguration", loader);
        Class<?> navView = XposedHelpers.findClass(PREFIX + "SideGestureNavView", loader);
        XposedHelpers.findMethodExact(detector, "onMotionEventImpl", MotionEvent.class);
        XposedHelpers.findMethodExact(config, "getCancelDistance");
        XposedHelpers.findMethodExact(config, "getAnimSwitchDistance");
        XposedHelpers.findField(navView, "mBackIcon");
        XposedHelpers.findMethodExact(navView, "onDraw", Canvas.class);
        XposedHelpers.findMethodExact(detector, "switchAppDelay");
        XposedHelpers.findMethodExact(detector, "unregisterSmartSideBarRegion");
        for (String field : new String[] {"mContext", "mGestureUIHandler", "mTopActivity", "mIsOnLeftEdge",
                "mAllowGesture", "mThresholdCrossed", "mDisplay", "mDisplaySize", "mSideGestureViewManager"}) {
            XposedHelpers.findField(detector, field);
        }
        Class<?> preprocess = XposedHelpers.findClass(PREFIX + "SideGesturePreProcess", loader);
        XposedHelpers.findMethodExact(preprocess, "disableGestureInLauncher");
        XposedHelpers.findField(preprocess, "mCurrentActivityName");
        XposedHelpers.findField(preprocess, "mFocusWindowPkgName");
        Class<?> manager = XposedHelpers.findClass(PREFIX + "SideGestureViewManager", loader);
        XposedHelpers.findField(manager, "mNavLeftView");
        XposedHelpers.findField(manager, "mNavRightView");
        util = XposedHelpers.findClass("com.oplusos.systemui.common.util.Util", loader);
        XposedHelpers.findMethodExact(util, "getTopActivityComponentName");
        XposedHelpers.findMethodExact(util, "getFocusWindowPkgName");
        XposedHelpers.findField(detector, "mExcludeRegion");

        // Launcher excludes Back before the launcher-specific preprocessor is reached.
        // Only ignore its application exclusion region, not PiP/sidebar/overlay regions.
        XposedHelpers.findAndHookMethod(detector, "edgeContains", Region.class,
                int.class, int.class, int.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        Session current = (Session) XposedHelpers.getAdditionalInstanceField(param.thisObject, STATE);
                        if (current != null && current.home && Boolean.TRUE.equals(param.getResult())
                                && param.args[0] == XposedHelpers.getObjectField(param.thisObject, "mExcludeRegion")) {
                            param.setResult(false);
                            if (diagnosticStarts < 40) HookLog.info("Sidebar launcher application exclusion ignored");
                        }
                    }
                });
        XposedHelpers.findAndHookMethod("com.oplusos.systemui.navigationbar.gesture.AppSettingsHandler",
                loader, "disableBack", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (Boolean.TRUE.equals(homeCheck.get()) && Boolean.TRUE.equals(param.getResult())) {
                            param.setResult(false);
                            if (diagnosticStarts < 40) HookLog.info("Sidebar launcher Back-only restriction ignored");
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(PREFIX + "SideGesturePreProcess", loader,
                "disableGestureInLauncher", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (diagnosticStarts < 40) HookLog.info("Sidebar launcher gate: activity="
                                + XposedHelpers.getObjectField(param.thisObject, "mCurrentActivityName")
                                + " focus=" + XposedHelpers.getObjectField(param.thisObject, "mFocusWindowPkgName")
                                + " nativeDisabled=" + param.getResult());
                        if (isHome(topActivity())
                                && "com.android.launcher".equals(XposedHelpers.getObjectField(param.thisObject, "mFocusWindowPkgName"))) {
                            param.setResult(false);
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(detector, "switchAppDelay", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) { param.setResult(null); }
        });
        XposedHelpers.findAndHookMethod(detector, "checkAllowGesture", int.class, int.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                param.setObjectExtra("cc_previous_home_check", homeCheck.get());
                Session current = (Session) XposedHelpers.getAdditionalInstanceField(param.thisObject, STATE);
                homeCheck.set(current != null && current.home);
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Boolean previous = (Boolean) param.getObjectExtra("cc_previous_home_check");
                if (previous == null) homeCheck.remove();
                else homeCheck.set(previous);
                if (diagnosticStarts++ >= 40) return;
                Object target = param.thisObject;
                HookLog.info("Sidebar allow check: result=" + param.getResult()
                        + " top=" + topActivity()
                        + " flags=" + XposedHelpers.getIntField(target, "mSysUiFlags")
                        + " quickstepDisabled=" + XposedHelpers.getBooleanField(target, "mDisabledForQuickstep")
                        + " backAllowed=" + XposedHelpers.getBooleanField(target, "mIsBackGestureAllowed")
                        + " blockingActivity=" + XposedHelpers.getBooleanField(target, "mGestureBlockingActivityRunning"));
            }
        });
        XposedHelpers.findAndHookMethod(detector, "onMotionEventImpl", MotionEvent.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                MotionEvent event = (MotionEvent) param.args[0];
                Session session = session(param.thisObject, config);
                try {
                    if (session.before(event)) {
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        param.args[0] = cancel;
                        param.setObjectExtra("cc_sidebar_cancel", cancel);
                    }
                } catch (Throwable error) {
                    session.clear();
                    HookLog.error("Sidebar gesture before", error);
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Session session = session(param.thisObject, config);
                try {
                    if (param.hasThrowable()) session.clear();
                    else session.after((MotionEvent) param.args[0]);
                } catch (Throwable error) {
                    session.clear();
                    HookLog.error("Sidebar gesture after", error);
                } finally {
                    MotionEvent cancel = (MotionEvent) param.getObjectExtra("cc_sidebar_cancel");
                    if (cancel != null) cancel.recycle();
                }
            }
        });
        XposedHelpers.findAndHookMethod(detector, "unregisterSmartSideBarRegion", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Session session = (Session) XposedHelpers.getAdditionalInstanceField(param.thisObject, STATE);
                if (session != null) session.clear();
            }
        });
        XposedHelpers.findAndHookMethod(navView, "onDraw", Canvas.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(param.thisObject, READY))) return;
                Bitmap original = (Bitmap) XposedHelpers.getObjectField(param.thisObject, "mBackIcon");
                if (original == null || original.isRecycled()) return;
                Bitmap icon = (Bitmap) XposedHelpers.getAdditionalInstanceField(param.thisObject, ICON);
                if (icon == null || icon.getWidth() != original.getWidth() || icon.getHeight() != original.getHeight()) {
                    icon = sidebarIcon(original.getWidth(), original.getHeight());
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, ICON, icon);
                }
                param.setObjectExtra("cc_original_icon", original);
                XposedHelpers.setObjectField(param.thisObject, "mBackIcon", icon);
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Object original = param.getObjectExtra("cc_original_icon");
                if (original != null) XposedHelpers.setObjectField(param.thisObject, "mBackIcon", original);
            }
        });
        HookLog.info("Sidebar gesture hooks installed (build 17, configurable hold at maximum, launcher Home)");
    }

    private static ComponentName topActivity() {
        return (ComponentName) XposedHelpers.callStaticMethod(util, "getTopActivityComponentName");
    }

    private static boolean isHome(ComponentName top) {
        return top != null && "com.android.launcher".equals(top.getPackageName())
                && "com.android.launcher.Launcher".equals(top.getClassName());
    }

    static boolean isHomeForeground(ComponentName top, String focus) {
        return isHome(top) && "com.android.launcher".equals(focus);
    }

    private static Session session(Object detector, Class<?> config) {
        Session session = (Session) XposedHelpers.getAdditionalInstanceField(detector, STATE);
        if (session == null) {
            session = new Session(detector, config);
            XposedHelpers.setAdditionalInstanceField(detector, STATE, session);
        }
        return session;
    }

    private static Bitmap sidebarIcon(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xffffffff);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, Math.min(width, height) / 12f));
        canvas.drawRoundRect(width * .12f, height * .12f, width * .88f, height * .88f, 2, 2, paint);
        canvas.drawLine(width * .55f, height * .12f, width * .55f, height * .88f, paint);
        return bitmap;
    }

    private static final class Session {
        final Object detector;
        final Class<?> config;
        final Context context;
        final Handler handler;
        final SidebarGesture gesture = new SidebarGesture(holdMillis);
        final Runnable timer = () -> arm(SystemClock.uptimeMillis());
        float downX, x, y, peak;
        boolean left, home, open, returnHome;
        ComponentName startedActivity;
        int rotation;
        View view;

        Session(Object detector, Class<?> config) {
            this.detector = detector;
            this.config = config;
            context = (Context) XposedHelpers.getObjectField(detector, "mContext");
            handler = (Handler) XposedHelpers.getObjectField(detector, "mGestureUIHandler");
        }

        boolean allowed() { return XposedHelpers.getBooleanField(detector, "mAllowGesture"); }
        boolean valid() {
            if (!allowed() || !XposedHelpers.getBooleanField(detector, "mThresholdCrossed")) return false;
            Display display = (Display) XposedHelpers.getObjectField(detector, "mDisplay");
            if (display.getRotation() != rotation || context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                    || !context.getSystemService(PowerManager.class).isInteractive()) return false;
            if (!java.util.Objects.equals(startedActivity, topActivity())) return false;
            int distance = (int) XposedHelpers.callStaticMethod(config, "getCancelDistance");
            Point size = (Point) XposedHelpers.getObjectField(detector, "mDisplaySize");
            if (y < 0 || y >= size.y || x < 0 || x > size.x) return false;
            return left ? x > distance : x < size.x - distance;
        }

        boolean before(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                clear();
                downX = x = event.getX();
                ComponentName top = topActivity();
                startedActivity = top;
                home = isHomeForeground(top, (String)
                        XposedHelpers.callStaticMethod(util, "getFocusWindowPkgName"));
                rotation = ((Display) XposedHelpers.getObjectField(detector, "mDisplay")).getRotation();
                return false;
            }
            x = event.getX();
            y = event.getY();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
                gesture.cancel();
                handler.removeCallbacks(timer);
                return false;
            }
            if (!allowed()) return false;
            left = XposedHelpers.getBooleanField(detector, "mIsOnLeftEdge");
            float inward = left ? x - downX : downX - x;
            if (action == MotionEvent.ACTION_MOVE) {
                if (gesture.state() == SidebarGesture.State.IDLE && inward > 0) {
                    gesture.begin(left);
                }
                if (gesture.state() == SidebarGesture.State.READY && peak - inward >= ViewConfiguration.get(context).getScaledTouchSlop()) {
                    gesture.reverse();
                    showReady(false);
                    return true;
                }
                peak = Math.max(peak, inward);
            }
            if (action == MotionEvent.ACTION_UP) {
                arm(event.getEventTime());
                if (gesture.state() == SidebarGesture.State.READY && !valid()) gesture.cancel();
                returnHome = gesture.shouldReturnHome(home, valid());
                boolean consumed = gesture.state() == SidebarGesture.State.READY || gesture.state() == SidebarGesture.State.CANCELED || home;
                open = gesture.release();
                handler.removeCallbacks(timer);
                return consumed;
            }
            return false;
        }

        void after(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN && diagnosticStarts < 40 && allowed()) {
                HookLog.info("Sidebar gesture start: top=" + startedActivity + " home=" + home
                        + " allowed=" + allowed());
            }
            if (action == MotionEvent.ACTION_MOVE) {
                if (allowed()) arm(event.getEventTime());
                else clear();
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
                boolean launch = open;
                boolean goHome = returnHome;
                boolean fromLeft = left;
                clear();
                if (launch) request(fromLeft);
                else if (goHome) requestHome();
            }
        }

        void arm(long now) {
            try {
                handler.removeCallbacks(timer);
                Point size = (Point) XposedHelpers.getObjectField(detector, "mDisplaySize");
                int max = (int) XposedHelpers.callStaticMethod(config, "getAnimSwitchDistance");
                if (gesture.advance(now, valid() && SidebarGesture.atMaximum(x, size.x, max, left))) {
                    peak = left ? x - downX : downX - x;
                    Object manager = XposedHelpers.getObjectField(detector, "mSideGestureViewManager");
                    view = (View) XposedHelpers.getObjectField(manager, left ? "mNavLeftView" : "mNavRightView");
                    showReady(true);
                    if (view != null) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    HookLog.info("Sidebar gesture ready side=" + (left ? 0 : 1));
                }
                long remaining = gesture.remainingMillis(now);
                if (remaining >= 0) handler.postAtTime(timer, now + remaining);
            } catch (Throwable error) { clear(); HookLog.error("Arm sidebar gesture", error); }
        }

        void showReady(boolean ready) {
            if (view == null) return;
            XposedHelpers.setAdditionalInstanceField(view, READY, ready);
            view.invalidate();
        }

        void clear() {
            handler.removeCallbacks(timer);
            showReady(false);
            view = null;
            gesture.reset();
            peak = 0;
            open = false;
            returnHome = false;
        }

        void requestHome() {
            try {
                if (!isHomeForeground(topActivity(), (String) XposedHelpers.callStaticMethod(util, "getFocusWindowPkgName"))) return;
                if (!"13.0.44".equals(context.getPackageManager().getPackageInfo("com.android.launcher", 0).versionName)) {
                    HookLog.info("Unsupported launcher version for default Home page");
                    return;
                }
                context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setComponent(new ComponentName("com.android.launcher", "com.android.launcher.Launcher"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                HookLog.info("Launcher native default Home requested");
            } catch (Throwable error) { HookLog.error("Return to launcher Home", error); }
        }

        void request(boolean fromLeft) {
            try {
                Intent request = new Intent(SidebarRelayHook.ACTION).setPackage("android")
                        .putExtra("side", fromLeft ? 0 : 1);
                context.sendOrderedBroadcast(request, null, new BroadcastReceiver() {
                    @Override public void onReceive(Context receiverContext, Intent intent) {
                        if (getResultCode() != Activity.RESULT_OK) {
                            Toast.makeText(context, "侧边栏未响应，请确认系统框架与智能侧边栏作用域并重启", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, handler, Activity.RESULT_CANCELED, null, null);
            } catch (Throwable error) {
                HookLog.error("Request sidebar", error);
                XposedBridge.log(error);
                Toast.makeText(context, "暂时无法呼出智能侧边栏", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

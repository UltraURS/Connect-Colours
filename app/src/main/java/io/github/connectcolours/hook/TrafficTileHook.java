package io.github.connectcolours.hook;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;

import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.connectcolours.usage.DailyUsage;
import io.github.connectcolours.usage.MobileUsageReader;

final class TrafficTileHook {
    private static final String TILE = "com.oplusos.systemui.qs.tiles.OplusCellularTile";
    private static final String TRACKER = "connect_colours_usage";
    private static final long INTERVAL = 30_000;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ConnectColours-Usage");
        thread.setDaemon(true);
        return thread;
    });

    static void install(ClassLoader loader) {
        Class<?> tile = XposedHelpers.findClass(TILE, loader);
        XposedHelpers.findAndHookMethod(tile, "handleUpdateState",
                "com.android.systemui.plugins.qs.QSTile$SignalState", Object.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Tracker tracker = tracker(param.thisObject);
                        tracker.request();
                        String label = tracker.currentLabel();
                        if (label != null && !param.hasThrowable()) {
                            Object state = param.args[0];
                            XposedHelpers.setObjectField(state, "label", label);
                            Object description = XposedHelpers.getObjectField(state, "contentDescription");
                            XposedHelpers.setObjectField(state, "contentDescription",
                                    description + ", " + label);
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(
                "com.android.systemui.qs.tileimpl.QSTileImpl", loader,
                "handleSetListening", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!tile.isInstance(param.thisObject)) return;
                        Tracker tracker = tracker(param.thisObject);
                        tracker.listening = (boolean) param.args[0];
                        tracker.handler.removeCallbacks(tracker.tick);
                        if (tracker.listening) {
                            tracker.lastAttempt = -INTERVAL;
                            tracker.tick.run();
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(tile, "handleDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Tracker tracker = (Tracker) XposedHelpers.getAdditionalInstanceField(param.thisObject, TRACKER);
                if (tracker != null) {
                    tracker.destroyed = true;
                    tracker.listening = false;
                    tracker.handler.removeCallbacks(tracker.tick);
                }
            }
        });
        HookLog.info("Traffic tile hooks installed");
    }

    private static Tracker tracker(Object tile) {
        Tracker tracker = (Tracker) XposedHelpers.getAdditionalInstanceField(tile, TRACKER);
        if (tracker == null) {
            tracker = new Tracker(tile);
            XposedHelpers.setAdditionalInstanceField(tile, TRACKER, tracker);
        }
        return tracker;
    }

    // All fields are confined to the tile's handler; the worker only queries statistics.
    private static final class Tracker {
        final Object tile;
        final Context context;
        final Handler handler;
        final Runnable tick;
        boolean listening;
        boolean destroyed;
        boolean busy;
        boolean errorLogged;
        long lastAttempt = -INTERVAL;
        long day = -1;
        String label;

        Tracker(Object tile) {
            this.tile = tile;
            context = (Context) XposedHelpers.getObjectField(tile, "mContext");
            handler = (Handler) XposedHelpers.getObjectField(tile, "mHandler");
            tick = new Runnable() {
                @Override
                public void run() {
                    if (destroyed || !listening) return;
                    request();
                    XposedHelpers.callMethod(tile, "refreshState");
                    handler.postDelayed(this, INTERVAL);
                }
            };
        }

        String currentLabel() {
            return day == today() ? label : null;
        }

        long today() {
            return DailyUsage.startOfDay(System.currentTimeMillis(), ZoneId.systemDefault());
        }

        void request() {
            long uptime = SystemClock.elapsedRealtime();
            if (destroyed || busy || uptime - lastAttempt < INTERVAL) return;
            lastAttempt = uptime;
            busy = true;
            long requestDay = today();
            WORKER.execute(() -> {
                String result = null;
                Throwable failure = null;
                try {
                    result = DailyUsage.label(MobileUsageReader.readToday(context));
                } catch (Exception error) {
                    failure = error;
                }
                String usage = result;
                Throwable error = failure;
                handler.post(() -> {
                    busy = false;
                    if (destroyed) return;
                    day = requestDay;
                    label = usage;
                    if (error != null && !errorLogged) HookLog.error("Daily usage query", error);
                    errorLogged = error != null;
                    XposedHelpers.callMethod(tile, "refreshState");
                });
            });
        }
    }
}

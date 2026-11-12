package io.github.connectcolours.hook;

import android.app.AlarmManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Map;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.connectcolours.bluetooth.BluetoothConfig;
import io.github.connectcolours.bluetooth.RetryRound;
import io.github.connectcolours.bluetooth.ReconnectMessages;

// Runs inside the verified SystemUI APK, which holds BLUETOOTH_CONNECT/PRIVILEGED.
@android.annotation.SuppressLint("MissingPermission")
final class BluetoothReconnectHook {
    // Owns only Application.attach's base Context, for the lifetime of the SystemUI process.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static BluetoothReconnectHook instance;
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Handler handler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final boolean showPrompts;
    private final AlarmManager alarms;
    private final AlarmManager.OnAlarmListener alarmTick = this::refreshAndDrive;
    private final Map<String, Entry> entries = new HashMap<>();
    private final Runnable tick = this::refreshAndDrive;

    static void install(Context context, ClassLoader loader, boolean showPrompts) {
        if (instance != null) return;
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        if (manager == null || manager.getAdapter() == null) return;
        instance = new BluetoothReconnectHook(context, manager.getAdapter(), showPrompts);
        installManualDisconnect(context, loader);
        HookLog.info("Bluetooth background reconnect installed in SystemUI");
    }

    static void installManualDisconnect(Context context, ClassLoader loader) {
        XposedHelpers.findAndHookMethod("com.android.settingslib.bluetooth.CachedBluetoothDevice",
                loader, "disconnect", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            String address = (String) XposedHelpers.callMethod(param.thisObject, "getAddress");
                            // Save before the host disconnects, so the resulting broadcast cannot race us.
                            BluetoothConfig.pause(context, address);
                        } catch (Throwable error) {
                            HookLog.error("Pause manual Bluetooth disconnect", error);
                        }
                    }
                });
    }

    private BluetoothReconnectHook(Context context, BluetoothAdapter adapter, boolean showPrompts) {
        this.context = context;
        this.adapter = adapter;
        this.showPrompts = showPrompts;
        alarms = context.getSystemService(AlarmManager.class);
        HandlerThread worker = new HandlerThread("ConnectColours-Bluetooth");
        worker.start();
        handler = new Handler(worker.getLooper());
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                try {
                    if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        if (state == BluetoothAdapter.STATE_ON) {
                            BluetoothConfig.reset(context);
                            entries.clear();
                            refreshAndDrive();
                        } else if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                            handler.removeCallbacks(tick);
                            alarms.cancel(alarmTick);
                            entries.clear();
                        }
                        return;
                    }
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device == null) return;
                    String address = device.getAddress();
                    if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())
                            && intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1) == BluetoothDevice.BOND_NONE) {
                        entries.remove(address);
                        BluetoothConfig.remove(context, address);
                    }
                    Entry entry = entries.get(address);
                    if (entry != null && BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                        entry.round.disconnected(SystemClock.elapsedRealtime());
                    }
                    refreshAndDrive();
                } catch (Throwable error) {
                    HookLog.error("Bluetooth state event", error);
                }
            }
        }, filter, null, handler);
        context.getContentResolver().registerContentObserver(BluetoothConfig.URI, true,
                new ContentObserver(handler) {
                    @Override public void onChange(boolean selfChange, Uri uri) {
                        if (BluetoothConfig.isConfigChange(uri)) refreshAndDrive();
                    }
                });
        handler.post(tick);
    }

    private void refreshAndDrive() {
        handler.removeCallbacks(tick);
        alarms.cancel(alarmTick);
        try {
            if (!adapter.isEnabled()) return;
            Bundle settings = new Bundle();
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                settings.putBundle(device.getAddress(), BluetoothConfig.read(context, device.getAddress()));
            }
            entries.keySet().removeIf(address -> !settings.containsKey(address)
                    || !settings.getBundle(address).getBoolean("enabled"));
            long now = SystemClock.elapsedRealtime();
            long next = Long.MAX_VALUE;
            for (String address : settings.keySet()) {
                Bundle config = settings.getBundle(address);
                if (!config.getBoolean("enabled")) continue;
                BluetoothDevice device = adapter.getRemoteDevice(address);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    entries.remove(address);
                    continue;
                }
                boolean connected = (boolean) XposedHelpers.callMethod(device, "isConnected");
                int limit = config.getInt("attempts", 1);
                Entry entry = entries.get(address);
                if (entry == null || entry.limit != limit) {
                    entry = new Entry(limit, now, connected);
                    entries.put(address, entry);
                }
                if (config.getBoolean("paused")) entry.round.pause();
                RetryRound.State before = entry.round.state();
                if (entry.round.poll(now, connected)) {
                    notifyUser(ReconnectMessages.attempt(device.getAlias(), entry.round.attempts(), limit));
                    try {
                        int result = (int) XposedHelpers.callMethod(device, "connect");
                        if (result != 0) entry.round.failed(now);
                        HookLog.info("Bluetooth reconnect attempt " + entry.round.attempts() + "/" + limit
                                + " result=" + result);
                    } catch (Throwable error) {
                        entry.round.failed(now);
                        HookLog.error("Bluetooth connect request", error);
                    }
                }
                notifyUser(ReconnectMessages.outcome(before, entry.round.state(),
                        entry.round.attempts(), device.getAlias()));
                next = Math.min(next, entry.round.nextDeadline());
            }
            if (next != Long.MAX_VALUE) {
                alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        Math.max(SystemClock.elapsedRealtime() + 1, next),
                        "ConnectColours-Bluetooth", alarmTick, handler);
            }
        } catch (Throwable error) {
            // Configuration failures stop retries rather than connecting with unknown settings.
            HookLog.error("Bluetooth reconnect configuration", error);
        }
    }

    private void notifyUser(String message) {
        if (showPrompts && message != null) {
            mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }
    }

    private static final class Entry {
        final int limit;
        final RetryRound round;
        Entry(int limit, long now, boolean connected) {
            this.limit = limit;
            round = new RetryRound(limit, now, connected);
        }
    }
}

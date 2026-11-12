package io.github.connectcolours.hook;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Keep the privileged provider call in system_server, without changing provider permissions. */
final class SidebarRelayHook {
    static final String ACTION = "io.github.connectcolours.OPEN_SIDEBAR";

    static void install(ClassLoader loader) {
        XposedHelpers.findAndHookMethod("com.android.server.am.ActivityManagerService", loader,
                "systemReady", Runnable.class,
                XposedHelpers.findClass("com.android.server.utils.TimingsTraceAndSlog", loader),
                new XC_MethodHook() {
                    private boolean installed;
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (installed || param.hasThrowable()) return;
                        Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                        HandlerThread thread = new HandlerThread("ConnectColoursSidebarRelay");
                        thread.start();
                        context.registerReceiver(new BroadcastReceiver() {
                            @Override public void onReceive(Context receiverContext, Intent intent) {
                                if (!ACTION.equals(intent.getAction())) return;
                                int side = intent.getIntExtra("side", -1);
                                if (side != 0 && side != 1) return;
                                try {
                                    boolean accepted = forward(context, side);
                                    setResultCode(accepted ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
                                    HookLog.info("Sidebar relay accepted=" + accepted + " side=" + side);
                                } catch (Throwable error) {
                                    HookLog.error("Sidebar relay", error);
                                    XposedBridge.log(error);
                                }
                            }
                        }, new IntentFilter(ACTION), "android.permission.STATUS_BAR_SERVICE",
                                new Handler(thread.getLooper()), Context.RECEIVER_EXPORTED);
                        installed = true;
                        HookLog.info("Sidebar system relay installed (build 12)");
                    }
                });
    }

    static boolean forward(Context context, int side) {
        if (side != 0 && side != 1) return false;
        Bundle args = new Bundle();
        args.putInt("side", side);
        Bundle result = context.getContentResolver().call(
                Uri.parse("content://com.coloros.sidebar"), SidebarProviderHook.METHOD, null, args);
        return result != null && result.getBoolean("accepted");
    }
}

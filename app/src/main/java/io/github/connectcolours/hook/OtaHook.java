package io.github.connectcolours.hook;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;

import org.json.JSONObject;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.connectcolours.Feature;

final class OtaHook {
    static void install(ClassLoader loader, Context context, FeatureConfig config) {
        if (config.enabled(Feature.UPDATE_STATE)) installState(loader);
        if (config.enabled(Feature.UPDATE_PROMPTS)) installPrompts(loader, context);
        if (config.enabled(Feature.UPDATE_NETWORK)) blockNetwork(loader);
    }

    private static void installState(ClassLoader loader) {
        // Only changes this process's reads; stored state survives module removal.
        Class<?> stateStore = XposedHelpers.findClass("b4.d", loader);
        XposedHelpers.findAndHookMethod("b4.a", loader, "l", String.class, Object.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (stateStore.isInstance(param.thisObject)
                                && "update_state".equals(param.args[0])) param.setResult(0);
                    }
                });
        XposedHelpers.findAndHookMethod("com.oplus.ota.service.h", loader,
                "handleMessage", Message.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Message message = (Message) param.args[0];
                        if (message.what == 1001) message.what = 2001; // Native no-update result.
                        if (message.what == 1002) message.what = 2006; // Description query finished.
                    }
                });
        XposedHelpers.findAndHookMethod("v4.g", loader, "run", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                Handler callback = (Handler) XposedHelpers.getObjectField(param.thisObject, "c");
                String query = (String) XposedHelpers.getObjectField(param.thisObject, "d");
                callback.sendEmptyMessage("query_description".equals(query) ? 2006 : 2001);
                return null;
            }
        });
        XposedHelpers.findAndHookMethod("com.oplus.ota.db.OTAProvider", loader,
                "call", String.class, String.class, Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Keep the provider's original caller and permission checks.
                        if (param.hasThrowable() || !(param.getResult() instanceof Bundle)) return;
                        if (!"callExternalGetOtaInfo".equals(param.args[0])
                                && !"getOTAInfoFromAssistant".equals(param.args[0])) return;
                        Bundle result = new Bundle((Bundle) param.getResult());
                        JSONObject data = new JSONObject(result.getString("data", "{}"));
                        data.put("have_update", false);
                        for (String key : new String[] {
                                "new_romVer", "new_colorOSVer", "new_androidVer", "new_otaVersion"
                        }) {
                            if (data.has(key)) data.put(key, "");
                        }
                        result.putString("data", data.toString());
                        param.setResult(result);
                    }
                });
        HookLog.info("OTA no-update hooks installed");
    }

    private static void installPrompts(ClassLoader loader, Context context) {
        for (String method : new String[] {"k", "m"}) {
            XposedHelpers.findAndHookMethod("c5.p", loader, method, Context.class,
                    XC_MethodReplacement.returnConstant(null));
        }
        XposedHelpers.findAndHookMethod(NotificationManager.class, "notify",
                String.class, int.class, Notification.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isUpdateNotification((int) param.args[1])) param.setResult(null);
                    }
                });
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (int id : new int[] {88766, 1987923, 1987924, 20020401}) manager.cancel(id);
        HookLog.info("OTA popup and notification hooks installed");
    }

    private static boolean isUpdateNotification(int id) {
        return id == 88766 || id == 1987923 || id == 1987924 || id == 20020401;
    }

    private static void blockNetwork(ClassLoader loader) {
        HookLog.info("Installing OTA network hooks");
        // The supplied updater uses OkHttp and HttpURLConnection for queries/downloads.
        // These boot-class hooks are installed only in com.oplus.ota processes.
        XposedHelpers.findAndHookMethod("okhttp3.l0", loader, "b", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setThrowable(new IOException("OTA networking blocked by Connect Colours"));
            }
        });
        XposedBridge.hookAllMethods(URL.class, "openConnection", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String protocol = ((URL) param.thisObject).getProtocol();
                if ("http".equals(protocol) || "https".equals(protocol)) {
                    param.setThrowable(new IOException("OTA networking blocked by Connect Colours"));
                }
            }
        });
        XposedHelpers.findAndHookMethod(Socket.class, "connect", SocketAddress.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args[0] instanceof InetSocketAddress) {
                            param.setThrowable(new IOException("OTA networking blocked by Connect Colours"));
                        }
                    }
                });
    }
}

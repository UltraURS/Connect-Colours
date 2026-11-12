package io.github.connectcolours.hook;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

final class SimWelcomeHook {
    static boolean isWelcome(String text) {
        if (text == null) return false;
        String normalized = text.replaceAll("\\s+", "");
        return normalized.startsWith("感谢您使用") || normalized.startsWith("感謝您使用")
                || normalized.matches("尊敬的客户[：:]感谢您选择中国移动通信[！!]?");
    }

    static void install(ClassLoader loader) {
        try {
            Class<?> dialog = XposedHelpers.findClass("com.android.stk.StkDialogActivity", loader);
            Field message = XposedHelpers.findField(dialog, "mTextMsg");
            Field text = XposedHelpers.findField(message.getType(), "text");
            Field alert = XposedHelpers.findField(dialog, "mAlertDialog");
            Method response = XposedHelpers.findMethodExact(dialog, "sendResponse", int.class, boolean.class);
            XposedHelpers.findAndHookMethod(Dialog.class, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (dismissWelcome((Dialog) param.thisObject, dialog, message, text, alert, response)) {
                            param.setResult(null);
                            HookLog.info("SIM welcome blocked before show");
                        }
                    } catch (Throwable error) {
                        HookLog.error("SIM welcome dismissal", error);
                    }
                }
            });
            HookLog.info("SIM welcome hooks installed (STK 13.2.50 dialog)");
        } catch (Throwable error) {
            HookLog.error("Unsupported STK implementation", error);
        }
    }

    static boolean dismissWelcome(Dialog shown, Class<?> activityType, Field message, Field text,
                                  Field alert, Method response) throws ReflectiveOperationException {
        Context context = shown.getContext();
        while (context instanceof ContextWrapper && !activityType.isInstance(context)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        // STK shares the phone process: never suppress other activities' dialogs.
        if (!activityType.isInstance(context)) return false;
        Activity activity = (Activity) context;
        if (activity.isFinishing() || alert.get(activity) != shown) return false;
        Object value = message.get(activity);
        if (value == null || !isWelcome((String) text.get(value))) return false;
        // STK 13.2.50's Cancel listener calls sendResponse(13, false).
        // It handles responseNeeded, the SIM slot, timeout cancellation and finish().
        response.invoke(activity, 13, false);
        return activity.isFinishing();
    }
}

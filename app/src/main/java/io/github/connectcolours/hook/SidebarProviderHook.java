package io.github.connectcolours.hook;

import android.content.ContentProvider;
import android.content.Context;
import android.app.KeyguardManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

final class SidebarProviderHook {
    static final String METHOD = "connect_colours_open_sidebar";
    private static final String DISPLAY = "com.oplus.smartsidebar.permanent.models.DisplayDataManager";
    private static final String SETTINGS = "com.coloros.edgepanel.utils.EdgePanelSettingsValueProxy";

    static void install(ClassLoader loader) {
        Class<?> display = XposedHelpers.findClass(DISPLAY, loader);
        Class<?> settings = XposedHelpers.findClass(SETTINGS, loader);
        XposedHelpers.findField(display, "a");
        XposedHelpers.findMethodExact(display, "m");
        XposedHelpers.findMethodExact(display, "r", boolean.class, boolean.class);
        XposedHelpers.findMethodExact(display, "f");
        XposedHelpers.findMethodExact(settings, "getToggleState", Context.class);
        Handler main = new Handler(Looper.getMainLooper());
        XposedHelpers.findAndHookMethod("com.coloros.edgepanel.api.SideBarProvider", loader,
                "call", String.class, String.class, Bundle.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!METHOD.equals(param.args[0])) return;
                        Bundle result = new Bundle();
                        param.setResult(result);
                        // The existing provider permission remains enforced as well.
                        if (Binder.getCallingUid() != 1000) return;
                        Bundle args = (Bundle) param.args[2];
                        if (args == null || !args.containsKey("side")) return;
                        int side = args.getInt("side", -1);
                        if (side != 0 && side != 1) return;
                        Context context = ((ContentProvider) param.thisObject).getContext();
                        result.putBoolean("accepted", main.post(() -> {
                            try {
                                if ((int) XposedHelpers.callStaticMethod(settings, "getToggleState", context) != 1) {
                                    Toast.makeText(context, "请先开启系统智能侧边栏", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
                                Object manager = XposedHelpers.getStaticObjectField(display, "a");
                                Object view = XposedHelpers.callMethod(manager, "m");
                                if (view == null) {
                                    Toast.makeText(context, "智能侧边栏尚未就绪，请稍后重试", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if ("HIDE".equals(String.valueOf(XposedHelpers.getObjectField(view, "mGlobalBarShowType")))) {
                                    HookLog.info("Sidebar unavailable in current scene");
                                    return;
                                }
                                XposedHelpers.callMethod(manager, "r", side == 0, false);
                                // r posts the bar update to the main queue. Open after that update.
                                main.post(() -> {
                                    try {
                                        XposedHelpers.callMethod(manager, "f");
                                        HookLog.info("Sidebar native open requested side=" + side);
                                    } catch (Throwable error) { HookLog.error("Open sidebar", error); }
                                });
                            } catch (Throwable error) { HookLog.error("Prepare sidebar", error); }
                        }));
                    }
                });
        HookLog.info("Sidebar 15.2.7 provider bridge installed");
    }
}

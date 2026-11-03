package pl.tablet.cleanlauncher;

import android.graphics.drawable.Drawable;
import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    private static final String MEMORY = "com.joyar.ext.meminfo.MeminfoView";
    private static final String CLEAR_ALL = "com.joyar.ext.clearall.ClearAllButton";
    private static final String HOTSEAT = "com.android.launcher3.Hotseat";

    private static boolean shouldHide(Object view) {
        String name = view.getClass().getName();
        return MEMORY.equals(name) || CLEAR_ALL.equals(name);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!"com.android.launcher3".equals(param.packageName)) return;

        XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam hook) {
                        if (shouldHide(hook.thisObject)) hook.args[0] = View.GONE;
                    }
                });

        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam hook) {
                        View view = (View) hook.thisObject;
                        if (shouldHide(view)) {
                            view.setVisibility(View.GONE);
                            return;
                        }
                        if (HOTSEAT.equals(view.getClass().getName())) {
                            Drawable background = view.getBackground();
                            XposedBridge.log("CleanLauncher Hotseat background: " + background);
                            view.setBackground(null);
                        }
                    }
                });
    }
}

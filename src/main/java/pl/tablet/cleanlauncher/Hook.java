package pl.tablet.cleanlauncher;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewParent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.android.launcher3";
    private static final String MEMORY = "com.joyar.ext.meminfo.MeminfoView";
    private static final String CLEAR_ALL = "com.joyar.ext.clearall.ClearAllButton";
    private static final String HOTSEAT = "com.android.launcher3.Hotseat";
    private static final String POPUP =
            "com.android.launcher3.popup.PopupContainerWithArrow";

    private static final int BLUR = 60;
    private static final int POPUP_COLOR = 0xFF2C1F1C;

    private static int systemId;
    private static int deepId;
    private static boolean applyingPopup;

    private static boolean shouldHide(Object view) {
        String name = view.getClass().getName();
        return MEMORY.equals(name) || CLEAR_ALL.equals(name);
    }

    private static void loadPopupIds(View view) {
        if (systemId != 0 && deepId != 0) return;
        systemId = view.getResources().getIdentifier(
                "system_shortcuts_container", "id", PACKAGE);
        deepId = view.getResources().getIdentifier(
                "deep_shortcuts_container", "id", PACKAGE);
    }

    private static void fixPopup(View popup) {
        loadPopupIds(popup);
        View system = popup.findViewById(systemId);
        View deep = popup.findViewById(deepId);
        if (system == null || deep == null) return;

        Drawable source = deep.getBackground();
        Drawable card;
        if (source != null && source.getConstantState() != null) {
            card = source.getConstantState()
                    .newDrawable(popup.getResources()).mutate();
        } else {
            GradientDrawable fallback = new GradientDrawable();
            fallback.setColor(POPUP_COLOR);
            fallback.setCornerRadius(
                    28f * popup.getResources().getDisplayMetrics().density);
            card = fallback;
        }

        XposedBridge.log("CleanLauncher popup source: " + source);
        applyingPopup = true;
        try {
            popup.setBackground(card);
            system.setBackground(null);
        } finally {
            applyingPopup = false;
        }
    }

    private static void schedulePopupFix(View popup) {
        popup.post(() -> fixPopup(popup));
        popup.postDelayed(() -> fixPopup(popup), 120);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!PACKAGE.equals(param.packageName)) return;

        Class<?> depth = XposedHelpers.findClassIfExists(
                "com.android.launcher3.statehandlers.DepthController",
                param.classLoader);
        if (depth == null) {
            XposedBridge.log("CleanLauncher: nie znaleziono DepthController");
        } else {
            XposedBridge.hookAllConstructors(depth, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam hook) {
                    try {
                        int oldBlur = XposedHelpers.getIntField(
                                hook.thisObject, "mMaxBlurRadius");
                        XposedHelpers.setIntField(
                                hook.thisObject, "mMaxBlurRadius", BLUR);
                        XposedBridge.log("CleanLauncher blur: " + oldBlur + " -> " + BLUR);
                    } catch (Throwable error) {
                        XposedBridge.log("CleanLauncher blur error: " + error);
                    }
                }
            });
        }

        XposedHelpers.findAndHookMethod(View.class, "setBackgroundDrawable", Drawable.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam hook) {
                        if (HOTSEAT.equals(hook.thisObject.getClass().getName())
                                && hook.args[0] != null) {
                            XposedBridge.log("CleanLauncher blocked background: "
                                    + hook.args[0].getClass().getName());
                            hook.args[0] = null;
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam hook) {
                        if (applyingPopup) return;

                        View view = (View) hook.thisObject;
                        if (POPUP.equals(view.getClass().getName())) {
                            schedulePopupFix(view);
                            return;
                        }

                        int id = view.getId();
                        if (id != View.NO_ID && (id == systemId || id == deepId)) {
                            ViewParent parent = view.getParent();
                            if (parent instanceof View
                                    && POPUP.equals(parent.getClass().getName())) {
                                schedulePopupFix((View) parent);
                            }
                        }
                    }
                });

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

                        String name = view.getClass().getName();
                        if (HOTSEAT.equals(name)) {
                            Drawable background = view.getBackground();
                            XposedBridge.log("CleanLauncher Hotseat background: " + background);
                            view.setBackground(null);
                        } else if (POPUP.equals(name)) {
                            schedulePopupFix(view);
                        }
                    }
                });
    }
}

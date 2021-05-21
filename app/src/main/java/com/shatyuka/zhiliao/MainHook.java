package com.shatyuka.zhiliao;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    public final static String hookPackage = "com.zhihu.android";
    static String modulePath;

    private native void initNative();

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private void tryLoadNative() {
        String path = modulePath.substring(0, modulePath.lastIndexOf('/'));
        try {
            System.load(path + "/lib/arm64/libzhiliao.so");
            initNative();
            return;
        } catch (Throwable ignored) {
        }

        try { // Let's try again
            System.load(path + "/lib/arm/libzhiliao.so");
            initNative();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (hookPackage.equals(lpparam.packageName)) {
            tryLoadNative();

            XposedBridge.hookAllConstructors(XposedHelpers.findClass("com.tencent.tinker.loader.app.TinkerApplication", lpparam.classLoader), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.args[0] = 0;
                }
            });

            XposedHelpers.findAndHookMethod(android.app.Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[0] instanceof Application) {
                        Helper.context = ((Application) param.args[0]).getApplicationContext();
                        Helper.prefs = Helper.context.getSharedPreferences("zhiliao_preferences", Context.MODE_PRIVATE);

                        if (!Helper.init(lpparam.classLoader))
                            Toast.makeText(Helper.context, "知了初始化失败，可能不支持当前版本知乎: " + Helper.packageInfo.versionName, Toast.LENGTH_SHORT).show();
                        else
                            Hooks.init(lpparam.classLoader);
                    }
                }
            });
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        modulePath = startupParam.modulePath;
        Helper.modRes = Helper.getModuleRes(startupParam.modulePath);
    }
}

package com.qqversionfix;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * LSPosed module: override QQ's com.tencent.common.config.AppSetting values.
 *
 * Scope this module ONLY to com.tencent.mobileqq.
 */
public class QQVersionFix implements IXposedHookLoadPackage {

    static final String TAG = "QQVersionFix";

    // Defaults of the currently analysed QQ 9.2.15. We replace these substrings
    // with whatever the user saves in the module UI.
    private static final String OLD_VERSION = "9.2.15";
    private static final String OLD_BUILD = "29600";
    private static final String OLD_DATE = "2025-09-09";
    private static final String OLD_SIG = "012a1717";

    private static volatile Config sConfig;

    private static final String[] STRING_METHODS = {
            "c", "d", "e", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "s", "t"
    };

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (!"com.tencent.mobileqq".equals(lpparam.packageName)) {
            return;
        }
        // Only the main QQ process performs login.
        if (!"com.tencent.mobileqq".equals(lpparam.processName)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(ContextWrapper.class, "attachBaseContext",
                    Context.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (sConfig != null) {
                                return;
                            }
                            Context ctx = (Context) param.args[0];
                            sConfig = loadConfig(ctx);
                            Log.i(TAG, "config=" + (sConfig == null ? null : sConfig));
                            if (sConfig == null || !sConfig.hasAny()) {
                                return;
                            }
                            try {
                                hookAppSetting(lpparam.classLoader);
                                hookPackageManager(lpparam.classLoader);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " hook AppSetting failed: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + " installed Application.attachBaseContext hook");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " init failed: " + t);
        }
    }

    private static void hookAppSetting(ClassLoader cl) {
        Class<?> appSetting = XposedHelpers.findClass("com.tencent.common.config.AppSetting", cl);
        XposedBridge.log(TAG + " AppSetting class loaded: " + appSetting.getName());

        for (final String method : STRING_METHODS) {
            try {
                XposedBridge.hookAllMethods(appSetting, method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object result = param.getResult();
                        if (result instanceof String) {
                            param.setResult(patch((String) result));
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(TAG + " hook string method " + method + " failed: " + t);
            }
        }

        // f() returns the current build code as int.
        try {
            XposedBridge.hookAllMethods(appSetting, "f", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object result = param.getResult();
                    if (result instanceof Integer && sConfig.build != null
                            && sConfig.build.length() > 0) {
                        try {
                            param.setResult(Integer.parseInt(sConfig.build));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook f() failed: " + t);
        }

        // Also try to patch fields in case some code reads them directly.
        patchFields(appSetting);
    }

    private static void hookPackageManager(ClassLoader cl) {
        final String pkg = "com.tencent.mobileqq";
        XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", cl,
                "getPackageInfo", String.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Config c = sConfig;
                        if (c == null || !pkg.equals(param.args[0])) {
                            return;
                        }
                        Object result = param.getResult();
                        if (!(result instanceof PackageInfo)) {
                            return;
                        }
                        PackageInfo pi = (PackageInfo) result;
                        if (c.version != null && c.version.length() > 0) {
                            pi.versionName = c.version;
                        }
                        if (c.code != null && c.code.length() > 0) {
                            try {
                                int code = Integer.parseInt(c.code);
                                pi.versionCode = code;
                                XposedHelpers.setLongField(pi, "longVersionCode", (long) code);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                });
        XposedBridge.log(TAG + " PackageManager.getPackageInfo hook installed");
    }

    private static void patchFields(Class<?> appSetting) {
        String[] names = {"b", "c", "d", "e", "f", "l", "m", "n", "o", "p", "s", "t"};
        for (String name : names) {
            try {
                Field field = appSetting.getDeclaredField(name);
                field.setAccessible(true);
                Object old = field.get(null);
                if (old instanceof String) {
                    String patched = patch((String) old);
                    if (!patched.equals(old)) {
                        field.set(null, patched);
                        XposedBridge.log(TAG + " patched field " + name + " -> " + patched);
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + " patch field " + name + " failed: " + t);
            }
        }
    }

    private static String patch(String original) {
        Config c = sConfig;
        if (c == null || original == null || !c.hasAny()) {
            return original;
        }
        String out = original;
        if (c.version != null && c.version.length() > 0) {
            out = out.replace(OLD_VERSION, c.version);
        }
        if (c.build != null && c.build.length() > 0) {
            out = out.replace(OLD_BUILD, c.build);
        }
        if (c.date != null && c.date.length() > 0) {
            out = out.replace(OLD_DATE, c.date);
        }
        if (c.sig != null && c.sig.length() > 0) {
            out = out.replace(OLD_SIG, c.sig);
        }
        if (c.extra != null && c.extra.length() > 0) {
            // Allow an entirely custom string to replace "9.2.15|29600|..."
            // style values when the user knows the full current-version payload.
            out = c.extra;
        }
        return out;
    }

    private static Config loadConfig(Context ctx) {
        Config provider = loadConfigFromProvider(ctx);
        if (provider != null) {
            XposedBridge.log(TAG + " config loaded from provider: " + provider);
            return provider;
        }
        Config xsp = loadConfigFromXSharedPreferences();
        if (xsp != null) {
            XposedBridge.log(TAG + " config loaded from module prefs: " + xsp);
            return xsp;
        }
        Config fileConfig = loadConfigFromQqFile(ctx);
        if (fileConfig != null) {
            XposedBridge.log(TAG + " config loaded from QQ files: " + fileConfig);
        }
        return fileConfig;
    }

    private static Config loadConfigFromProvider(Context ctx) {
        try {
            Cursor cursor = ctx.getContentResolver().query(
                    ConfigProvider.CONFIG_URI, null, null, null, null);
            if (cursor == null) {
                return null;
            }
            try {
                if (!cursor.moveToFirst()) {
                    return null;
                }
                Config c = new Config();
                c.version = getString(cursor, MainActivity.KEY_VERSION);
                c.build = getString(cursor, MainActivity.KEY_BUILD);
                c.date = getString(cursor, MainActivity.KEY_DATE);
                c.sig = getString(cursor, MainActivity.KEY_SIG);
                c.code = getString(cursor, MainActivity.KEY_CODE);
                c.extra = getString(cursor, MainActivity.KEY_EXTRA);
                return c;
            } finally {
                cursor.close();
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " provider read failed: " + t);
            return null;
        }
    }

    private static String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0) {
            return "";
        }
        String s = cursor.getString(index);
        return s == null ? "" : s;
    }

    private static Config loadConfigFromXSharedPreferences() {
        try {
            XSharedPreferences prefs = new XSharedPreferences(
                    "com.qqversionfix", MainActivity.PREF);
            if (!prefs.getFile().canRead()) {
                return null;
            }
            Config c = new Config();
            c.version = prefs.getString(MainActivity.KEY_VERSION, "");
            c.build = prefs.getString(MainActivity.KEY_BUILD, "");
            c.date = prefs.getString(MainActivity.KEY_DATE, "");
            c.sig = prefs.getString(MainActivity.KEY_SIG, "");
            c.code = prefs.getString(MainActivity.KEY_CODE, "");
            c.extra = prefs.getString(MainActivity.KEY_EXTRA, "");
            return c.hasAny() ? c : null;
        } catch (Throwable t) {
            XposedBridge.log(TAG + " XSharedPreferences read failed: " + t);
            return null;
        }
    }

    private static Config loadConfigFromQqFile(Context ctx) {
        try {
            File file = new File(ctx.getFilesDir(), "qqversionfix.json");
            if (!file.exists() || !file.canRead()) {
                return null;
            }
            Config c = new Config();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = line.substring(0, eq);
                    String value = line.substring(eq + 1);
                    if (MainActivity.KEY_VERSION.equals(key)) c.version = value;
                    else if (MainActivity.KEY_BUILD.equals(key)) c.build = value;
                    else if (MainActivity.KEY_DATE.equals(key)) c.date = value;
                    else if (MainActivity.KEY_SIG.equals(key)) c.sig = value;
                    else if (MainActivity.KEY_CODE.equals(key)) c.code = value;
                    else if (MainActivity.KEY_EXTRA.equals(key)) c.extra = value;
                }
            } finally {
                reader.close();
            }
            return c.hasAny() ? c : null;
        } catch (Throwable t) {
            XposedBridge.log(TAG + " loadConfigFromQqFile failed: " + t);
            return null;
        }
    }

    static class Config {
        String version;
        String build;
        String date;
        String sig;
        String code;
        String extra;

        boolean hasAny() {
            return notEmpty(version) || notEmpty(build) || notEmpty(date)
                    || notEmpty(sig) || notEmpty(code) || notEmpty(extra);
        }

        private static boolean notEmpty(String s) {
            return s != null && s.length() > 0;
        }

        @Override
        public String toString() {
            return "version=" + version + " build=" + build + " date=" + date
                    + " sig=" + sig + " code=" + code + " extra=" + extra;
        }
    }
}

package com.qqversionfix;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // Baselines are detected automatically from the actual AppSetting class at
    // runtime. The fallback values are only used if detection somehow fails.
    private static String OLD_VERSION = "9.1.25";
    private static String OLD_BUILD = "21820";
    private static String OLD_DATE = "2024-12-10";
    private static String OLD_SIG = "008c1bb3";

    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final Pattern BUILD_PATTERN = Pattern.compile("\\d{4,6}");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern SIG_PATTERN = Pattern.compile("[0-9a-fA-F]{8}");

    private static volatile Config sConfig;
    private static volatile Context sAppContext;
    private static volatile boolean sToastShown;
    private static volatile String sStatusMessage;

    private static final String[] STRING_METHODS = {
            "c", "d", "e", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "s", "t"
    };

    private static final String QUA_PREFIX = "V1_AND_SQ_";
    private static final String QUA_SUFFIX = "_YYB_D";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (!"com.tencent.mobileqq".equals(lpparam.packageName)) {
            return;
        }
        // The real MSF/native packet is built in the :MSF process, so the
        // module must also run there to spoof the version seen by the server.
        String process = lpparam.processName;
        if (!"com.tencent.mobileqq".equals(process)
                && !"com.tencent.mobileqq:MSF".equals(process)) {
            return;
        }
        final boolean isMsf = "com.tencent.mobileqq:MSF".equals(process);

        try {
            XposedHelpers.findAndHookMethod(ContextWrapper.class, "attachBaseContext",
                    Context.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (sConfig != null) {
                                return;
                            }
                            Context ctx = (Context) param.args[0];
                            sAppContext = ctx.getApplicationContext();
                            XposedBridge.log(TAG + " attachBaseContext process="
                                    + (isMsf ? ":MSF" : "main"));
                            sConfig = loadConfig(ctx);
                            Log.i(TAG, "config=" + (sConfig == null ? null : sConfig));
                            if (sConfig == null || !sConfig.hasAny()) {
                                return;
                            }
                            try {
                                hookAppSetting(lpparam.classLoader);
                                hookAppSettingApi(lpparam.classLoader);
                                hookInjectorA(lpparam.classLoader);
                                hookQua(lpparam.classLoader);
                                hookPackageManager(lpparam.classLoader);
                                sStatusMessage = buildSuccessStatus();
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " hook AppSetting failed: " + t);
                                sStatusMessage = "QQ 登录版本伪装\n查找失败："
                                        + (t.getMessage() == null ? t : t.getMessage());
                            }
                            if (!isMsf) {
                                showStatusOnce();
                            }
                        }
                    });
            XposedBridge.log(TAG + " installed Application.attachBaseContext hook");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " init failed: " + t);
        }
    }

    private static void showStatusOnce() {
        if (sToastShown || sAppContext == null || sStatusMessage == null) {
            return;
        }
        sToastShown = true;
        final String msg = sStatusMessage;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(sAppContext, msg, Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {
                }
            }
        }, 2500);
    }

    private static String buildSuccessStatus() {
        Config c = sConfig;
        String target = c == null ? "(空)" : c.version + " / " + c.build
                + " / " + c.date + " / " + c.sig + " / code=" + c.code;
        return "QQ 登录版本伪装\n"
                + "AppSetting 查找成功\n"
                + "当前版本：" + OLD_VERSION + "\n"
                + "构建号：" + OLD_BUILD + "\n"
                + "日期：" + OLD_DATE + "\n"
                + "签名：" + OLD_SIG + "\n"
                + "伪装目标：" + target;
    }

    private static void hookAppSetting(ClassLoader cl) {
        Class<?> appSetting = XposedHelpers.findClass("com.tencent.common.config.AppSetting", cl);
        XposedBridge.log(TAG + " AppSetting class loaded: " + appSetting.getName());
        detectBaseline(appSetting);

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

        // Also try to patch fields in case some code reads them directly.
        patchFields(appSetting);
    }

    private static void hookAppSettingApi(ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass(
                    "com.tencent.mobileqq.config.api.impl.AppSettingApiImpl", cl);
            hookStringMethod(c, "buildNum", value(1));
            hookStringMethod(c, "getSubVersion", value(0));
            hookStringMethod(c, "getReportVersionName", value(0));
            hookStringMethod(c, "getVersion", new Function0<String>() {
                @Override
                public String invoke() {
                    return "android " + version();
                }
            });
            hookStringMethod(c, "getPublishVersionString", new Function0<String>() {
                @Override
                public String invoke() {
                    return version() + "." + code();
                }
            });
            XposedBridge.log(TAG + " AppSettingApiImpl hooks installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook AppSettingApiImpl failed: " + t);
        }
    }

    private static void hookInjectorA(ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass("com.tencent.mobileqq.injector.a", cl);
            hookStringMethod(c, "d", new Function0<String>() {
                @Override
                public String invoke() {
                    return "2013 " + version();
                }
            });
            hookStringMethod(c, "getSubVersion", value(0));
            hookStringMethod(c, "getVersion", new Function0<String>() {
                @Override
                public String invoke() {
                    return "android " + version();
                }
            });
            hookStringMethod(c, "getReportVersionName", value(0));
            hookStringMethod(c, "f", value(1));
            XposedBridge.log(TAG + " injector.a hooks installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook injector.a failed: " + t);
        }
    }

    private static void hookQua(ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass("cooperation.qzone.QUA", cl);
            hookStringMethod(c, "getQUA3", new Function0<String>() {
                @Override
                public String invoke() {
                    return qua();
                }
            });
            hookStringMethod(c, "getVersionForHabo", new Function0<String>() {
                @Override
                public String invoke() {
                    return coreQua();
                }
            });
            hookStringMethod(c, "getVersionForPic", new Function0<String>() {
                @Override
                public String invoke() {
                    return coreQua();
                }
            });
            XposedBridge.log(TAG + " QUA hooks installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook QUA failed: " + t);
        }
    }

    private static String version() {
        Config c = sConfig;
        return c != null && notEmpty(c.version) ? c.version : OLD_VERSION;
    }

    private static String build() {
        Config c = sConfig;
        return c != null && notEmpty(c.build) ? c.build : OLD_BUILD;
    }

    private static String code() {
        Config c = sConfig;
        return c != null && notEmpty(c.code) ? c.code : OLD_BUILD;
    }

    private static String qua() {
        return QUA_PREFIX + version() + "_" + code() + QUA_SUFFIX;
    }

    private static String coreQua() {
        return qua().substring(3, 25);
    }

    private static boolean notEmpty(String s) {
        return s != null && s.length() > 0;
    }

    private interface Function0<T> {
        T invoke();
    }

    private static Function0<String> value(final int idx) {
        return new Function0<String>() {
            @Override
            public String invoke() {
                return idx == 0 ? version() : build();
            }
        };
    }

    private static void hookStringMethod(Class<?> clazz, String methodName,
                                         final Function0<String> supplier) {
        try {
            XposedBridge.hookAllMethods(clazz, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (sConfig == null || !sConfig.hasAny()) {
                        return;
                    }
                    param.setResult(supplier.invoke());
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook " + clazz.getSimpleName() + "." + methodName
                    + " failed: " + t);
        }
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
                                try {
                                    XposedHelpers.setLongField(pi, "longVersionCode", (long) code);
                                } catch (Throwable ignored) {
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                });
        XposedBridge.log(TAG + " PackageManager.getPackageInfo hook installed");
    }

    private static void detectBaseline(Class<?> appSetting) {
        try {
            String version = null;
            String build = null;
            String date = null;
            String sig = null;
            for (Field f : appSetting.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object value = f.get(null);
                    if (!(value instanceof String)) {
                        continue;
                    }
                    String s = (String) value;
                    if (version == null) {
                        Matcher m = VERSION_PATTERN.matcher(s);
                        if (m.find()) {
                            version = m.group();
                        }
                    }
                    if (build == null && s.matches("\\d{4,6}")) {
                        build = s;
                    }
                    if (date == null) {
                        Matcher m = DATE_PATTERN.matcher(s);
                        if (m.find()) {
                            date = m.group();
                        }
                    }
                    if (sig == null && s.matches("[0-9a-fA-F]{8}")) {
                        sig = s;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (version != null) OLD_VERSION = version;
            if (build != null) OLD_BUILD = build;
            if (date != null) OLD_DATE = date;
            if (sig != null) OLD_SIG = sig;
            XposedBridge.log(TAG + " auto baseline version=" + OLD_VERSION
                    + " build=" + OLD_BUILD + " date=" + OLD_DATE
                    + " sig=" + OLD_SIG);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " auto baseline detection failed: " + t);
        }
    }

    private static void patchFields(Class<?> appSetting) {
        String[] names = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k",
                "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w"};
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

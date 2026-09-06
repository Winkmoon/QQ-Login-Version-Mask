package com.qqversionfix;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/**
 * UI for editing QQ AppSetting values.
 */
public class MainActivity extends Activity {

    static final String PREF = "qq_version_config";
    static final String KEY_VERSION = "version";
    static final String KEY_BUILD = "build";
    static final String KEY_DATE = "date";
    static final String KEY_SIG = "sig";
    static final String KEY_CODE = "code";
    static final String KEY_EXTRA = "extra";

    private EditText etVersion;
    private EditText etBuild;
    private EditText etDate;
    private EditText etSig;
    private EditText etCode;
    private EditText etExtra;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());

        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        etVersion.setText(sp.getString(KEY_VERSION, "9.3.55"));
        etBuild.setText(sp.getString(KEY_BUILD, "29600"));
        etDate.setText(sp.getString(KEY_DATE, "2026-09-01"));
        etSig.setText(sp.getString(KEY_SIG, "012a1717"));
        etCode.setText(sp.getString(KEY_CODE, ""));
        etExtra.setText(sp.getString(KEY_EXTRA, ""));
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        root.setGravity(Gravity.FILL_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("QQ 登录版本伪装");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        root.addView(label("生效链路：\n模块 SharedPreferences\n  → ContentProvider\n  → QQ Hook 读取并修改 AppSetting / PackageManager"));
        root.addView(label("这些值会注入 com.tencent.common.config.AppSetting。\n一般只需要改版本名，其它保持默认即可。"));

        etVersion = input("版本名 (如 9.3.55)", "9.3.55");
        etBuild = input("子版本/build (原 29600)", "29600");
        etDate = input("发布日期 (原 2025-09-09)", "2026-09-01");
        etSig = input("签名/代码 (原 012a1717)", "012a1717");
        etCode = input("versionCode (PackageManager 上报，原 11480；可留空)", "");
        etExtra = input("附加原样串 (可留空)", "");

        root.addView(etVersion);
        root.addView(etBuild);
        root.addView(etDate);
        root.addView(etSig);
        root.addView(etCode);
        root.addView(etExtra);

        Button btnSave = new Button(this);
        btnSave.setText("保存并强停 QQ");
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (saveConfig()) {
                    Toast.makeText(MainActivity.this, "配置写入成功（SharedPreferences/Provider），正在强停 QQ...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "配置写入失败（SharedPreferences/Provider）", Toast.LENGTH_LONG).show();
                }
                forceStopQQ();
            }
        });
        root.addView(btnSave);

        Button btnSaveOnly = new Button(this);
        btnSaveOnly.setText("仅保存");
        btnSaveOnly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (saveConfig()) {
                    Toast.makeText(MainActivity.this, "配置写入成功（SharedPreferences/Provider）", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "配置写入失败（SharedPreferences/Provider）", Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(btnSaveOnly);

        Button btnRestore = new Button(this);
        btnRestore.setText("还原默认（取消伪装）并强停 QQ");
        btnRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etVersion.setText("");
                etBuild.setText("");
                etDate.setText("");
                etSig.setText("");
                etCode.setText("");
                etExtra.setText("");
                if (saveConfig()) {
                    Toast.makeText(MainActivity.this, "配置写入成功（SharedPreferences/Provider），已还原默认，正在强停 QQ...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "配置写入失败（SharedPreferences/Provider）", Toast.LENGTH_LONG).show();
                }
                forceStopQQ();
            }
        });
        root.addView(btnRestore);

        TextView note = new TextView(this);
        note.setText("保存后 QQ 会重启。\n若 QQ 没被杀掉，请手动在系统设置里强停。\n之后重新打开 QQ 生效。");
        note.setTextSize(13);
        note.setPadding(0, 24, 0, 0);
        root.addView(note);

        return scroll;
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setPadding(0, 24, 0, 8);
        return tv;
    }

    private EditText input(String hint, String def) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(def);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        et.setLayoutParams(lp);
        return et;
    }

    private boolean saveConfig() {
        SharedPreferences.Editor ed = getSharedPreferences(PREF, MODE_PRIVATE).edit();
        ed.putString(KEY_VERSION, clean(etVersion));
        ed.putString(KEY_BUILD, clean(etBuild));
        ed.putString(KEY_DATE, clean(etDate));
        ed.putString(KEY_SIG, clean(etSig));
        ed.putString(KEY_CODE, clean(etCode));
        ed.putString(KEY_EXTRA, clean(etExtra));
        boolean ok = ed.commit();
        writeConfigToQQ();
        return ok;
    }

    private static final String[] SU_CANDIDATES = {
            "su",
            "/system/bin/su",
            "/sbin/su",
            "/data/adb/ksu/bin/su"
    };

    private static class RootResult {
        boolean ok;
        String error;
        String used;
        String out;

        RootResult(boolean ok, String error, String used, String out) {
            this.ok = ok;
            this.error = error;
            this.used = used;
            this.out = out;
        }
    }

    /**
     * Try different su binaries. KernelSU/Magisk variants may expose su under
     * different paths, so always try bare "su" first (PATH lookup), then
     * common absolute locations.
     */
    private static RootResult runRoot(String cmd) {
        Throwable lastNotFound = null;
        for (String su : SU_CANDIDATES) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{su, "-c", cmd});
                String err = readAll(p.getErrorStream());
                int code = p.waitFor();
                String out = readAll(p.getInputStream());
                if (code == 0) {
                    return new RootResult(true, "", su, out);
                }
                return new RootResult(false, "exit=" + code + " " + err.trim(), su, out);
            } catch (Throwable t) {
                lastNotFound = t;
            }
        }
        return new RootResult(false,
                lastNotFound == null ? "su not found" : String.valueOf(lastNotFound),
                "", "");
    }

    /**
     * Optional best-effort mirror of the config into QQ's own files dir.
     * QQ reads the module's ContentProvider, so failure here is NOT an error
     * for the user; we keep this write silent.
     */
    private void writeConfigToQQ() {
        final String cfg = "version=" + clean(etVersion) + "\n"
                + "build=" + clean(etBuild) + "\n"
                + "date=" + clean(etDate) + "\n"
                + "sig=" + clean(etSig) + "\n"
                + "code=" + clean(etCode) + "\n"
                + "extra=" + clean(etExtra) + "\n";
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File tmp = new File(getCacheDir(), "qqversionfix.json");
                    FileOutputStream fos = new FileOutputStream(tmp);
                    fos.write(cfg.getBytes("UTF-8"));
                    fos.close();
                    String cmd = "tmp=" + tmp.getAbsolutePath()
                            + "; d=/data/data/com.tencent.mobileqq/files; "
                            + "f=$d/qqversionfix.json; "
                            + "cp $tmp $f && chown $(stat -c %u $d):$(stat -c %g $d) $f "
                            + "&& chmod 660 $f && cmp -s $tmp $f";
                    runRoot(cmd);
                } catch (Throwable ignored) {
                }
            }
        }).start();
    }

    private static String readAll(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    private void writeError(String msg) {
        try {
            File f = new File(getCacheDir(), "qqversionfix_error.txt");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(msg.getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {
        }
    }

    private String clean(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void forceStopQQ() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RootResult result = runRoot("am force-stop com.tencent.mobileqq");
                if (!result.ok) {
                    // Last resort without root; usually fails for non-system apps.
                    try {
                        Runtime.getRuntime().exec(new String[]{"am", "force-stop",
                                "com.tencent.mobileqq"}).waitFor();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }).start();
    }
}

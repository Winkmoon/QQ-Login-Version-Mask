# QQ 登录版本伪装 (QQ-Login-Version-Mask)

LSPosed module for **com.tencent.mobileqq**.

Lets you customize the QQ client version information that is reported by
`com.tencent.common.config.AppSetting` and `PackageManager`, so QQ itself sees
the version you configured instead of the built-in one.

## How it works

```
Module UI (SharedPreferences)
  -> ContentProvider
  -> QQ Hook reads config
  -> patches AppSetting fields / methods
  -> patches PackageManager.getPackageInfo versionName/versionCode
```

QQ does **not** need to read the module's private files. The module exposes its
config through an exported ContentProvider; QQ's hook process reads it from the
Provider.

## Features

- Customize:
  - 版本名 (version name, e.g. `9.3.55`)
  - 子版本/build (e.g. `29600`)
  - 发布日期
  - 签名/代码
  - versionCode (PackageManager)
  - extra full payload
- Save and force-stop QQ
- One-key restore to QQ's real version (clears all spoofing)
- Works for user 0 and Android MultiApp/user 999 (install module in the same user)

## Usage

1. Build/install the APK (see below).
2. Enable the module in LSPosed and scope it to `com.tencent.mobileqq`.
3. Open **QQ 登录版本伪装**.
4. Fill in the values and tap **保存并强停 QQ**.
5. Reopen QQ. Version info should now show your configured value.

To disable spoofing, tap **还原默认（取消伪装）并强停 QQ**.

## Build (no Gradle)

Requirements: Android SDK (android.jar), `javac`, `d8`, `aapt2`, `zipalign`,
`apksigner`, `keytool`.

The project already includes `app/libs/XposedBridgeApi-82.jar`.

```bash
bash build.sh
```

Output APK:

```text
build/QQVersionFix.apk
```

## Files

- `app/src/main/java/com/qqversionfix/MainActivity.java` – config UI
- `app/src/main/java/com/qqversionfix/QQVersionFix.java` – Xposed entry/hooks
- `app/src/main/java/com/qqversionfix/ConfigProvider.java` – config bridge
- `build.sh` – no-Gradle build script

## Disclaimer

This project is for technical research and educational purposes only. Use at
your own risk. QQ's server may still reject old protocol versions even when the
local version string is changed.

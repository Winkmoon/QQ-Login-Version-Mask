#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
ANDROID_JAR="${ANDROID_HOME}/platforms/android-34/android.jar"
API_JAR="${ROOT}/app/libs/XposedBridgeApi-82.jar"

OUT="${ROOT}/build"
CLS="${OUT}/classes"
DEX="${OUT}/dex"
APKOUT="${OUT}/apk"
SOURCES="${OUT}/sources.txt"
CLASSES="${OUT}/classes.txt"

for tool in javac d8 aapt2 zipalign apksigner keytool zip; do
    command -v "$tool" >/dev/null || { echo "missing tool: $tool"; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "missing android.jar: $ANDROID_JAR"; exit 1; }
[ -f "$API_JAR" ] || { echo "missing Xposed API jar: $API_JAR"; exit 1; }

rm -rf "$OUT"
mkdir -p "$CLS" "$DEX" "$APKOUT"

find app/src/main/java -name '*.java' > "$SOURCES"
javac -encoding UTF-8 \
    -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -classpath "$API_JAR" \
    -d "$CLS" \
    @"$SOURCES"

find "$CLS" -name '*.class' > "$CLASSES"
# shellcheck disable=SC2046
d8 --release \
    --lib "$ANDROID_JAR" \
    --min-api 23 \
    --output "$DEX" \
    $(cat "$CLASSES")

aapt2 link \
    -o "$APKOUT/unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest app/src/main/AndroidManifest.xml \
    -A app/src/main/assets \
    --min-sdk-version 23 \
    --target-sdk-version 31 \
    --version-code 1 \
    --version-name 0.1

(cd "$DEX" && zip -q "$APKOUT/unsigned.apk" classes.dex)

zipalign -f 4 "$APKOUT/unsigned.apk" "$APKOUT/aligned.apk"

KEYSTORE="$ROOT/keystore.jks"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -alias qqversionfix \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass qqversionfix \
        -keypass qqversionfix \
        -dname "CN=QQVersionFix,O=Local,C=CN"
fi
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:qqversionfix \
    --key-pass pass:qqversionfix \
    --out "$OUT/QQVersionFix.apk" \
    "$APKOUT/aligned.apk"

echo "Build OK: $OUT/QQVersionFix.apk"

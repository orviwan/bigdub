#!/usr/bin/env bash
# Reproducible build script for com.bigdub.clock (Android 2.1 Eclair / API Level 7)
# Produces: nook_setup/apks/com.bigdub.clock.apk

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_APK="$REPO_ROOT/nook_setup/apks/com.bigdub.clock.apk"

# Locate or bootstrap official Google Android 2.1 (API-7) platform & build-tools
SDK_CACHE="${ANDROID_SDK_CACHE:-$HOME/.cache/bigdub-android-sdk}"
ANDROID_JAR="${ANDROID_JAR:-$SDK_CACHE/android-2.1_r03-linux/android.jar}"
BUILD_TOOLS="${BUILD_TOOLS:-$SDK_CACHE/android-9}"

if [ ! -f "$ANDROID_JAR" ] || [ ! -x "$BUILD_TOOLS/aapt" ]; then
    echo "Bootstrapping official Android 2.1 SDK & build-tools into $SDK_CACHE..."
    mkdir -p "$SDK_CACHE"
    (
        cd "$SDK_CACHE"
        if [ ! -f android-2.1_r03.zip ]; then
            curl -fsSLO https://dl.google.com/android/repository/android-2.1_r03.zip
        fi
        echo "5ce51b023ac19f8738500b1007a1da5de2349a1e  android-2.1_r03.zip" | sha1sum -c -

        if [ ! -f build-tools_r28.0.3-linux.zip ]; then
            curl -fsSLO https://dl.google.com/android/repository/build-tools_r28.0.3-linux.zip
        fi
        echo "ea6f2f7103cd9da9ff0bdf6e37fbbba548fa4165  build-tools_r28.0.3-linux.zip" | sha1sum -c -

        python3 -c '
import zipfile, os
for zname in ["android-2.1_r03.zip", "build-tools_r28.0.3-linux.zip"]:
    with zipfile.ZipFile(zname) as z:
        z.extractall(".")
for root, _, files in os.walk("android-9"):
    for f in files:
        os.chmod(os.path.join(root, f), 0o755)
'
    )
fi

AAPT="$BUILD_TOOLS/aapt"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"

if ! javac -version >/dev/null 2>&1; then
    if command -v mise >/dev/null 2>&1; then
        JAVA_HOME_DIR="$(mise where java@temurin-11.0.33+10)"
        export JAVA_HOME="$JAVA_HOME_DIR"
        export PATH="$JAVA_HOME_DIR/bin:$PATH"
    else
        echo "Error: javac not found (install JDK 8/11 or mise java)." >&2
        exit 1
    fi
fi

BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT

mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" \
         "$BUILD_DIR/assets/images" "$SCRIPT_DIR/res/drawable" \
         "$(dirname "$OUT_APK")"

# 1. Copy 91Dub watchface sprites into APK assets/images/
cp "$REPO_ROOT/dashboard/assets/images/"*.png "$BUILD_DIR/assets/images/"

# 2. Generate 48x56 crisp 2x launcher icon from menu_icon_91w.png if not present
python3 - "$REPO_ROOT/dashboard/assets/images/menu_icon_91w.png" "$SCRIPT_DIR/res/drawable/icon.png" <<'PY'
import sys, shutil
shutil.copyfile(sys.argv[1], sys.argv[2])
PY

echo "[1/6] Generating R.java with aapt (API 7)..."
"$AAPT" package -f -m \
    -J "$BUILD_DIR/gen" \
    -M "$SCRIPT_DIR/AndroidManifest.xml" \
    -S "$SCRIPT_DIR/res" \
    -I "$ANDROID_JAR"

echo "[2/6] Compiling Java sources against Android 2.1 (API Level 7)..."
find "$SCRIPT_DIR/src" "$BUILD_DIR/gen" -name "*.java" > "$BUILD_DIR/sources.txt"
javac \
    -source 7 -target 7 \
    -bootclasspath "$ANDROID_JAR" \
    -classpath "$ANDROID_JAR" \
    -encoding UTF-8 \
    -d "$BUILD_DIR/classes" \
    @"$BUILD_DIR/sources.txt"

echo "[3/6] Compiling Dalvik bytecode (DEX 035 for Android 2.1)..."
find "$BUILD_DIR/classes" -name "*.class" > "$BUILD_DIR/classes.txt"
"$D8" \
    --min-api 7 \
    --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/dex" \
    $(cat "$BUILD_DIR/classes.txt")

echo "[4/6] Packaging resources and assets with aapt v1..."
"$AAPT" package -f \
    -M "$SCRIPT_DIR/AndroidManifest.xml" \
    -S "$SCRIPT_DIR/res" \
    -A "$BUILD_DIR/assets" \
    -I "$ANDROID_JAR" \
    -F "$BUILD_DIR/unsigned.apk"

(cd "$BUILD_DIR/dex" && "$AAPT" add -v "$BUILD_DIR/unsigned.apk" classes.dex >/dev/null)

echo "[5/6] Signing with Android 2.1-compatible v1 JAR signature (SHA1withRSA / SHA1)..."
KEYSTORE="$BUILD_DIR/signing.keystore"
keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias bigdubclock \
    -keyalg RSA \
    -keysize 2048 \
    -sigalg SHA1withRSA \
    -validity 10000 \
    -dname "CN=BigDub 91Dub Clock, OU=Clock, O=Orviwan, L=Home, S=UK, C=GB"

jarsigner \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -sigalg SHA1withRSA \
    -digestalg SHA1 \
    "$BUILD_DIR/unsigned.apk" \
    bigdubclock >/dev/null

echo "[6/6] Running zipalign (4-byte alignment) & binary verification..."
"$ZIPALIGN" -f 4 "$BUILD_DIR/unsigned.apk" "$OUT_APK"

python3 - "$OUT_APK" <<'PY'
import sys, zipfile
apk = sys.argv[1]
with zipfile.ZipFile(apk) as z:
    dex = z.read("classes.dex")
    assert dex[:8] == b"dex\n035\x00", f"Unexpected DEX version: {dex[:8]!r}"
    mf = z.read("META-INF/MANIFEST.MF").decode("latin1")
    assert "SHA1-Digest:" in mf and "SHA-256-Digest:" not in mf, "Manifest must use SHA1-Digest for Android 2.1!"
    imgs = [n for n in z.namelist() if n.startswith("assets/images/") and n.endswith(".png")]
    assert len(imgs) >= 70, f"Expected >=70 embedded sprites, got {len(imgs)}"
print(f"Verified {apk}: DEX=035, v1 SHA1withRSA signature OK, {len(imgs)} sprites embedded.")
PY

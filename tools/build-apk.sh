#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-$ROOT/.tooling/jdk/Contents/Home}"
SDK="${ANDROID_SDK_ROOT:-$ROOT/.tooling/android-sdk}"
BUILD_TOOLS="$SDK/build-tools/35.0.1"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
MIN_API="${MIN_API:-23}"
WORK="$ROOT/.tooling/build"
OUTPUT="$ROOT/dist/samo-tv-debug.apk"
KEYSTORE="$ROOT/.tooling/debug.keystore"

for tool in "$JAVA_HOME/bin/java" "$JAVA_HOME/bin/javac" "$JAVA_HOME/bin/jar" "$JAVA_HOME/bin/keytool" "$BUILD_TOOLS/aapt2" "$BUILD_TOOLS/zipalign" "$BUILD_TOOLS/apksigner"; do
  if [[ ! -x "$tool" ]]; then
    printf 'Required executable is missing: %s\n' "$tool" >&2
    exit 1
  fi
done
[[ -f "$ANDROID_JAR" ]] || { printf 'Android platform is missing: %s\n' "$ANDROID_JAR" >&2; exit 1; }
[[ -f "$ROOT/app/src/main/AndroidManifest.xml" ]] || { printf 'AndroidManifest.xml is missing.\n' >&2; exit 1; }
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="$SDK"

rm -rf "$WORK"
mkdir -p "$WORK/generated" "$WORK/classes" "$WORK/dex" "$ROOT/dist"

resource_args=()
if [[ -d "$ROOT/app/src/main/res" ]]; then
  "$BUILD_TOOLS/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$WORK/resources.zip"
  resource_args+=("$WORK/resources.zip")
fi
"$BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --java "$WORK/generated" \
  --auto-add-overlay \
  -o "$WORK/resources.ap_" \
  ${resource_args[@]+"${resource_args[@]}"}

[[ -d "$ROOT/app/src/main/java" ]] || { printf 'Java source directory is missing.\n' >&2; exit 1; }
sources=()
while IFS= read -r -d '' source; do
  sources+=("$source")
done < <(find "$ROOT/app/src/main/java" "$WORK/generated" -name '*.java' -type f -print0)
[[ ${#sources[@]} -gt 0 ]] || { printf 'No Java sources found.\n' >&2; exit 1; }
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$BUILD_TOOLS/core-lambda-stubs.jar:$ANDROID_JAR" -d "$WORK/classes" "${sources[@]}"
"$JAVA_HOME/bin/jar" cf "$WORK/classes.jar" -C "$WORK/classes" .
"$JAVA_HOME/bin/java" -Xmx512M -cp "$BUILD_TOOLS/lib/d8.jar" \
  com.android.tools.r8.D8 --min-api "$MIN_API" --lib "$ANDROID_JAR" \
  --output "$WORK/dex" "$WORK/classes.jar"

python3 - "$WORK" <<'PY'
from pathlib import Path
import sys, zipfile
work = Path(sys.argv[1])
with zipfile.ZipFile(work / 'resources.ap_') as resource_zip:
    with zipfile.ZipFile(work / 'unsigned.apk', 'w') as apk:
        for entry in resource_zip.infolist():
            apk.writestr(entry, resource_zip.read(entry.filename))
        for dex in sorted((work / 'dex').glob('classes*.dex')):
            apk.write(dex, dex.name, compress_type=zipfile.ZIP_STORED)
PY
"$BUILD_TOOLS/zipalign" -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
if [[ ! -f "$KEYSTORE" ]]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -noprompt \
    -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Samo TV Debug,O=Local Development,C=CN'
fi
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android --out "$OUTPUT" "$WORK/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUTPUT"
"$BUILD_TOOLS/zipalign" -c -p 4 "$OUTPUT"
shasum -a 256 "$OUTPUT" > "$OUTPUT.sha256"
printf 'Built: %s\n' "$OUTPUT"

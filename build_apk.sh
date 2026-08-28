#!/data/data/com.termux/files/usr/bin/bash
set -e

PROJECT_DIR="/data/data/com.termux/files/home"
ANDROID_SDK="/data/data/com.termux/files/home/android-sdk"
NATIVE_AAPT2="/data/data/com.termux/files/usr/bin/aapt2"

export ANDROID_HOME="$ANDROID_SDK"
export ANDROID_SDK_ROOT="$ANDROID_SDK"

echo "Running Gradle build..."
cd "$PROJECT_DIR"

# Ensure all transformed aapt2 in gradle caches are replaced with native aapt2 if present
find /data/data/com.termux/files/home/.gradle/caches/ -name "aapt2" -type f 2>/dev/null | while read -r aapt_bin; do
    if [ "$aapt_bin" != "$NATIVE_AAPT2" ]; then
        cp "$NATIVE_AAPT2" "$aapt_bin" 2>/dev/null || true
        chmod +x "$aapt_bin" 2>/dev/null || true
    fi
done

gradle :app:assembleDebug --no-daemon \
    -Dcom.android.build.gradle.aapt2FromMavenOverride="$NATIVE_AAPT2" \
    -Dandroid.aapt2FromMavenOverride="$NATIVE_AAPT2" \
    -Pandroid.aapt2FromMavenOverride="$NATIVE_AAPT2" \
    -Pcom.android.build.gradle.aapt2FromMavenOverride="$NATIVE_AAPT2" "$@"

echo "Build complete."

#!/usr/bin/env bash
# Build script for noncey Android app
# Usage:
#   ./build.sh            — debug APK
#   ./build.sh release    — signed release APK (requires keystore.properties)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

MODE="${1:-debug}"

# ── Prerequisite checks ────────────────────────────────────────────────────────

if ! command -v java &>/dev/null; then
    echo "ERROR: Java (JDK 17+) not found. Install from https://adoptium.net and set JAVA_HOME."
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
    echo "ERROR: JDK 17 or higher required (found Java $JAVA_VER)."
    exit 1
fi

if [ -z "${ANDROID_HOME:-}" ]; then
    # Try common locations
    for candidate in \
        "$HOME/Android/Sdk" \
        "$HOME/Library/Android/sdk" \
        "/usr/local/lib/android/sdk"; do
        if [ -d "$candidate" ]; then
            export ANDROID_HOME="$candidate"
            break
        fi
    done
fi

if [ -z "${ANDROID_HOME:-}" ]; then
    echo "ERROR: ANDROID_HOME is not set and Android SDK was not found in common locations."
    echo "       Install Android Studio or the standalone SDK and set ANDROID_HOME."
    exit 1
fi

# ── Bootstrap Gradle wrapper JAR if missing ────────────────────────────────────

WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Gradle wrapper JAR not found. Bootstrapping via 'gradle wrapper'..."
    if ! command -v gradle &>/dev/null; then
        echo "ERROR: 'gradle' is not on PATH and wrapper JAR is missing."
        echo "       Either install Gradle (https://gradle.org/install/) or open the project"
        echo "       in Android Studio once — it will generate the wrapper automatically."
        exit 1
    fi
    gradle wrapper --gradle-version 8.6
fi

chmod +x gradlew

# ── Release: verify keystore ───────────────────────────────────────────────────

if [ "$MODE" = "release" ]; then
    if [ ! -f "keystore.properties" ]; then
        echo "ERROR: keystore.properties not found."
        echo ""
        echo "To create a signing keystore (one-time):"
        echo "  keytool -genkey -v -keystore noncey.keystore -alias noncey \\"
        echo "          -keyalg RSA -keysize 2048 -validity 10000"
        echo ""
        echo "Then copy keystore.properties.example → keystore.properties and fill in the values."
        exit 1
    fi
fi

# ── Build ──────────────────────────────────────────────────────────────────────

if [ "$MODE" = "release" ]; then
    echo "Building release APK..."
    ./gradlew assembleRelease
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
    echo "Building debug APK..."
    ./gradlew assembleDebug
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

echo ""
echo "Build successful."
echo "APK: $SCRIPT_DIR/$APK_PATH"

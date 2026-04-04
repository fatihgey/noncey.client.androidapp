@echo off
REM Build script for noncey Android app
REM Usage:
REM   build.bat           — debug APK
REM   build.bat release   — signed release APK (requires keystore.properties)
REM   build.bat install   — adb install -r (prefers release APK, falls back to debug)

setlocal EnableDelayedExpansion

set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "MODE=%~1"
if "%MODE%"=="" set "MODE=debug"

REM ── Install mode ─────────────────────────────────────────────────────────────

if "%MODE%"=="install" (
    set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    if not exist "!ADB!" (
        echo ERROR: adb not found at !ADB!
        exit /b 1
    )
    REM Default to debug APK; pass "install release" as two args is not supported,
    REM so we just look for whichever APK exists, preferring release.
    set "APK_PATH=app\build\outputs\apk\release\app-release.apk"
    if not exist "!APK_PATH!" set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
    if not exist "!APK_PATH!" (
        echo ERROR: No APK found. Run 'build.bat' or 'build.bat release' first.
        exit /b 1
    )
    echo Installing !APK_PATH!...
    "!ADB!" install -r "!APK_PATH!"
    exit /b %ERRORLEVEL%
)

REM ── Prerequisite checks ──────────────────────────────────────────────────────

where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java ^(JDK 17+^) not found. Install from https://adoptium.net and set JAVA_HOME.
    exit /b 1
)

if "%ANDROID_HOME%"=="" (
    REM Try common locations
    if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
    if exist "%USERPROFILE%\AppData\Local\Android\Sdk" set "ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk"
)

if "%ANDROID_HOME%"=="" (
    echo ERROR: ANDROID_HOME is not set and Android SDK was not found in common locations.
    echo        Install Android Studio or the standalone SDK and set ANDROID_HOME.
    exit /b 1
)

REM ── Bootstrap Gradle wrapper JAR if missing ───────────────────────────────────

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo Gradle wrapper JAR not found. Bootstrapping via 'gradle wrapper'...
    where gradle >nul 2>&1
    if %ERRORLEVEL% neq 0 (
        echo ERROR: 'gradle' is not on PATH and wrapper JAR is missing.
        echo        Either install Gradle ^(https://gradle.org/install/^) or open the project
        echo        in Android Studio once — it will generate the wrapper automatically.
        exit /b 1
    )
    gradle wrapper --gradle-version 8.6
    if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
)

REM ── Release: verify keystore ──────────────────────────────────────────────────

if "%MODE%"=="release" (
    if not exist "keystore.properties" (
        echo ERROR: keystore.properties not found.
        echo.
        echo To create a signing keystore ^(one-time^):
        echo   keytool -genkey -v -keystore noncey.keystore -alias noncey ^
        echo           -keyalg RSA -keysize 2048 -validity 10000
        echo.
        echo Then copy keystore.properties.example -^> keystore.properties and fill in the values.
        exit /b 1
    )
)

REM ── Build ─────────────────────────────────────────────────────────────────────

if "%MODE%"=="release" (
    echo Building release APK...
    call gradlew.bat assembleRelease
    if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
    set "APK_PATH=app\build\outputs\apk\release\app-release.apk"
) else (
    echo Building debug APK...
    call gradlew.bat assembleDebug
    if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
    set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
)

echo.
echo Build successful.
echo APK: %SCRIPT_DIR%%APK_PATH%

endlocal

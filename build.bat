@echo off
REM Build script for noncey Android app
REM Usage:
REM   build.bat             — debug APK
REM   build.bat release     — signed release APK (requires keystore.properties)
REM   build.bat install-emu — adb install -r to the first connected emulator
REM   build.bat install-phone   — adb install -r to the first connected physical device
REM   build.bat uninstall-phone — adb uninstall from the first connected physical device

setlocal EnableDelayedExpansion

set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "MODE=%~1"
if "%MODE%"=="" set "MODE=debug"

REM ── Install modes ────────────────────────────────────────────────────────────

if "%MODE%"=="install-emu" goto :do_install
if "%MODE%"=="install-phone" goto :do_install
if "%MODE%"=="uninstall-phone" goto :do_uninstall
goto :after_install

:do_install
    set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    if not exist "!ADB!" (
        echo ERROR: adb not found at !ADB!
        exit /b 1
    )

    REM Find the target device serial from 'adb devices'
    set "TARGET_SERIAL="
    for /f "skip=1 tokens=1,2" %%A in ('"!ADB!" devices') do (
        if "%%B"=="device" (
            if "!TARGET_SERIAL!"=="" (
                if "%MODE%"=="install-emu" (
                    echo %%A | findstr /b "emulator" >nul && set "TARGET_SERIAL=%%A"
                ) else (
                    echo %%A | findstr /b "emulator" >nul || set "TARGET_SERIAL=%%A"
                )
            )
        )
    )

    if "!TARGET_SERIAL!"=="" (
        if "%MODE%"=="install-emu" (
            echo ERROR: No emulator device found. Start an AVD and try again.
        ) else (
            echo ERROR: No physical device found. Connect a phone with USB debugging enabled.
        )
        exit /b 1
    )

    REM Resolve APK — prefer release, fall back to debug
    set "APK_PATH=app\build\outputs\apk\release\app-release.apk"
    if not exist "!APK_PATH!" set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
    if not exist "!APK_PATH!" (
        echo ERROR: No APK found. Run 'build.bat' or 'build.bat release' first.
        exit /b 1
    )

    echo Installing !APK_PATH! on !TARGET_SERIAL!...
    "!ADB!" -s "!TARGET_SERIAL!" install -r "!APK_PATH!"
    exit /b %ERRORLEVEL%

:do_uninstall
    set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    if not exist "!ADB!" (
        echo ERROR: adb not found at !ADB!
        exit /b 1
    )

    REM Find the first non-emulator device
    set "TARGET_SERIAL="
    for /f "skip=1 tokens=1,2" %%A in ('"!ADB!" devices') do (
        if "%%B"=="device" (
            if "!TARGET_SERIAL!"=="" (
                echo %%A | findstr /b "emulator" >nul || set "TARGET_SERIAL=%%A"
            )
        )
    )

    if "!TARGET_SERIAL!"=="" (
        echo ERROR: No physical device found. Connect a phone with USB debugging enabled.
        exit /b 1
    )

    echo Uninstalling com.noncey.android from !TARGET_SERIAL!...
    "!ADB!" -s "!TARGET_SERIAL!" uninstall com.noncey.android
    exit /b %ERRORLEVEL%

:after_install

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

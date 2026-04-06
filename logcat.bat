@echo off
setlocal

set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set PKG=%~1
if "%PKG%"=="" set PKG=com.noncey.android

echo Waiting for device...
"%ADB%" wait-for-device

echo Getting PID for %PKG%...
for /f "tokens=*" %%i in ('"%ADB%" shell pidof %PKG% 2^>nul') do set PID=%%i

if "%PID%"=="" (
    echo App is not running. Launch it on the device first, then re-run this script.
    pause
    exit /b 1
)

echo Streaming logcat for %PKG% ^(PID %PID%^) -- Ctrl+C to stop
"%ADB%" logcat --pid=%PID% -v time

endlocal

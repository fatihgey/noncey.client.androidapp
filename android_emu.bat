@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "EMULATOR=%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if "%~1"=="" goto :usage
if /I "%~1"=="start" goto :start
if /I "%~1"=="stop" goto :stop
goto :usage

:start
if not exist "%EMULATOR%" (
    echo ERROR: emulator.exe not found at:
    echo   "%EMULATOR%"
    exit /b 1
)

set "FIRST_AVD="
for /f "usebackq delims=" %%A in (`"%EMULATOR%" -list-avds`) do (
    if not defined FIRST_AVD set "FIRST_AVD=%%A"
)

if not defined FIRST_AVD (
    echo ERROR: No Android Virtual Devices found.
    exit /b 1
)

echo Starting AVD: !FIRST_AVD!
start "" "%EMULATOR%" -avd "!FIRST_AVD!" -no-boot-anim
exit /b 0

:stop
if not exist "%ADB%" (
    echo ERROR: adb.exe not found at:
    echo   "%ADB%"
    exit /b 1
)

set "FIRST_EMU="
for /f "skip=1 tokens=1" %%D in ('"%ADB%" devices') do (
    echo %%D | findstr /b /c:"emulator-" >nul
    if not errorlevel 1 (
        if not defined FIRST_EMU set "FIRST_EMU=%%D"
    )
)

if not defined FIRST_EMU (
    echo ERROR: No running emulator devices found.
    exit /b 1
)

echo Stopping emulator: !FIRST_EMU!
"%ADB%" -s "!FIRST_EMU!" emu kill
exit /b %ERRORLEVEL%

:usage
echo Usage:
echo   %~nx0 start
echo   %~nx0 stop
exit /b 1
@echo off
REM CameraLink - Quick Test Script
REM This script helps you test the streaming functionality

echo ============================================
echo CameraLink - Stream Testing Helper
echo ============================================
echo.

:MENU
echo Select an option:
echo 1. Check if phone is connected
echo 2. View app logs (monitor streaming)
echo 3. Get phone's IP address
echo 4. Install APK to phone
echo 5. Test stream URL from this computer
echo 6. Open stream in browser
echo 7. Exit
echo.
set /p choice="Enter your choice (1-7): "

if "%choice%"=="1" goto CHECK_DEVICE
if "%choice%"=="2" goto VIEW_LOGS
if "%choice%"=="3" goto GET_IP
if "%choice%"=="4" goto INSTALL_APK
if "%choice%"=="5" goto TEST_URL
if "%choice%"=="6" goto OPEN_BROWSER
if "%choice%"=="7" goto END

echo Invalid choice. Please try again.
echo.
goto MENU

:CHECK_DEVICE
echo.
echo Checking for connected Android devices...
adb devices
echo.
echo If you see your device listed above, it's connected!
echo If not, make sure:
echo - USB Debugging is enabled on your phone
echo - Phone is connected via USB
echo - USB drivers are installed
echo.
pause
goto MENU

:VIEW_LOGS
echo.
echo Monitoring CameraLink logs... (Press Ctrl+C to stop)
echo.
adb logcat -s CameraStreamingService:* StreamingServer:* MainActivity:*
pause
goto MENU

:GET_IP
echo.
echo Getting phone's IP address...
echo.
echo Method 1: From phone shell
adb shell ip addr show wlan0 | findstr "inet "
echo.
echo Method 2: Alternative
adb shell ifconfig wlan0 | findstr "inet "
echo.
echo Look for the IP address (usually 192.168.x.x or 10.x.x.x)
echo.
pause
goto MENU

:INSTALL_APK
echo.
echo Installing CameraLink APK...
echo.
set /p apk_path="Enter full path to APK file (or press Enter for default): "
if "%apk_path%"=="" set apk_path=app\build\outputs\apk\debug\app-debug.apk
echo Installing from: %apk_path%
adb install -r "%apk_path%"
echo.
echo Installation complete!
echo.
pause
goto MENU

:TEST_URL
echo.
set /p stream_ip="Enter your phone's IP address: "
set stream_url=http://%stream_ip%:8080
echo.
echo Testing connection to %stream_url%
echo.
curl -I %stream_url%
echo.
echo If you see "HTTP/1.1 200 OK" above, the stream is working!
echo.
pause
goto MENU

:OPEN_BROWSER
echo.
set /p stream_ip="Enter your phone's IP address: "
set stream_url=http://%stream_ip%:8080
echo.
echo Opening %stream_url% in your default browser...
start %stream_url%
echo.
pause
goto MENU

:END
echo.
echo Thank you for using CameraLink!
echo.
exit /b


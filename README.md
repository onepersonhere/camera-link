# CameraLink - IP Camera App

Turn your Android phone into an IP camera and stream video footage over your local network - even with the screen off!

## Features

- 📹 **Live Video Streaming**: Stream your phone's camera feed in real-time
- 🌐 **Web-Based Viewing**: View the stream from any device with a web browser
- 📱 **Simple Interface**: Easy-to-use UI with one-tap streaming
- 🔴 **Live Indicator**: Visual feedback when streaming is active
- 📸 **Snapshot Support**: Capture still images from the stream
- 🌙 **Background Streaming**: Continue streaming even when screen is off
- 🔋 **Foreground Service**: Reliable streaming with wake lock support
- 🔔 **Persistent Notification**: Shows stream URL and allows easy control

## Quick Start Guide

### 1. Installation

#### Using Android Studio:
1. Open project in Android Studio
2. Connect your Android device via USB
3. Enable USB Debugging on device
4. Click "Run" (green play button)
5. Select your device
6. App will build and install automatically

#### Using Command Line:
```bash
# Navigate to project directory
cd C:\Users\wh\Documents\GitHub\CameraLink

# Build debug APK
gradlew assembleDebug

# Install to connected device
gradlew installDebug

# Or manually install
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Basic Usage

**On Your Phone:**
1. Launch the CameraLink app
2. Grant all requested permissions (Camera, Notifications)
3. Tap "▶ Start Streaming"
4. Note the displayed URL (e.g., `http://192.168.1.100:8080`)

**On Viewing Device:**
1. Open a web browser (Chrome, Firefox, Safari, etc.)
2. Enter the URL shown on your phone
3. View the live camera feed
4. Use the "Take Snapshot" button to capture still images

**Stop Streaming:**
- Tap "Stop" in the notification, or
- Reopen the app and tap "Stop Streaming"

### 3. Screen-Off Streaming

The killer feature! Your stream continues even when the phone screen is off:

1. Start streaming as above
2. **Turn off your phone screen** (press power button)
3. On your viewing device, the stream continues uninterrupted
4. The notification remains visible when you turn the screen back on
5. Stream runs until you explicitly stop it

## How It Works

### Architecture

- **Camera**: Uses CameraX API for reliable camera access
- **Streaming**: MJPEG stream over HTTP using NanoHTTPD server
- **Background Service**: Foreground service keeps streaming active
- **Wake Lock**: Prevents device from sleeping during streaming
- **Port**: Default streaming port is 8080
- **Frame Rate**: Approximately 30 FPS
- **Format**: JPEG compression at 80% quality

### Key Components

1. **CameraStreamingService**: Background service for camera streaming
   - Foreground service with camera permission
   - Wake lock management
   - Camera lifecycle handling without UI

2. **MainActivity**: Service control interface
   - Start/Stop streaming service
   - Display stream URL
   - Permission handling

3. **StreamingServer**: HTTP server for MJPEG streaming
   - Multiple endpoints (/, /stream, /snapshot, /test)
   - Proper YUV to NV21 conversion
   - Handles stride and pixel stride correctly

## Requirements

- Android 12 (API 31) or higher
- Camera permission
- Notification permission
- Wake lock permission
- Foreground service permissions
- Both devices must be on the same network (for local access)

## Technical Details

### Permissions Required

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### API Endpoints

- `/` or `/index.html` - Web viewer with embedded player
- `/stream` - Raw MJPEG stream
- `/snapshot` - Single frame JPEG snapshot
- `/test` - Server health check

### Network Configuration

The app automatically detects your phone's local IP address. Make sure:
- Your phone is connected to WiFi
- The viewing device is on the same network
- No firewall is blocking port 8080

## Testing Guide

### Test 1: Basic Streaming
1. Install the app on your Android device
2. Grant all requested permissions (Camera, Notifications)
3. Tap "▶ Start Streaming"
4. Note the IP address shown (e.g., http://192.168.1.100:8080)
5. On another device on the same WiFi network, open a web browser
6. Enter the stream URL
7. You should see the live camera feed

### Test 2: Screen Off Streaming
1. Start streaming as in Test 1
2. **Turn off your phone screen** (press power button)
3. On your viewing device, verify the stream continues
4. The stream should remain active without interruption
5. Turn the screen back on - stream should still be working
6. Check the notification - it should show the stream URL

### Test 3: App Closed Streaming
1. Start streaming
2. Press Home button to close the app
3. Stream should continue (check notification)
4. You can even close the app from recent apps
5. Service will continue until you tap "Stop" in the notification

### Test 4: Long Duration Test
1. Start streaming
2. Turn off screen
3. Leave it running for 30+ minutes
4. Verify stream remains active throughout
5. Check that battery usage is acceptable

### Test 5: Multiple Viewers
1. Start streaming
2. Open the stream URL on multiple devices simultaneously
3. All viewers should see the stream
4. Test with different browsers (Chrome, Firefox, Safari)

### Test 6: VLC Player
You can also view the stream in VLC:
1. Open VLC
2. Media → Open Network Stream
3. Enter: `http://YOUR_PHONE_IP:8080/stream`
4. Play

## Remote Access (Accessing from Anywhere)

### Option 1: Port Forwarding (Most Common)
1. Access your router's admin panel (usually 192.168.1.1 or 192.168.0.1)
2. Find "Port Forwarding" or "Virtual Server" settings
3. Forward external port 8080 to your phone's local IP:8080
4. Find your public IP address (Google "what is my ip")
5. Access stream from anywhere: http://YOUR_PUBLIC_IP:8080

**⚠️ Security Warning**: This exposes your camera to the internet. Consider adding authentication.

### Option 2: VPN (More Secure)
1. Set up a VPN server on your home network (e.g., WireGuard, OpenVPN)
2. Connect to your home VPN from anywhere
3. Access the local IP address as if you're on the same network
4. Much more secure than port forwarding

### Option 3: Tailscale/ZeroTier (Easiest & Secure)
1. Install Tailscale or ZeroTier on your phone and viewing device
2. Both devices join the same network
3. Access the stream using the Tailscale IP address
4. No router configuration needed
5. Encrypted and secure

### Option 4: ngrok (Quick Testing)
1. Install ngrok on a computer on the same network
2. Run: `ngrok http YOUR_PHONE_IP:8080`
3. ngrok will provide a public URL (e.g., https://abc123.ngrok.io)
4. Access that URL from anywhere
5. Free tier has limitations but great for testing

## Troubleshooting

### Stream Not Accessible

**Check WiFi**: Phone must be on WiFi (not mobile data by default)
- Verify phone is connected to WiFi
- Check viewing device is on same network
- Try pinging the IP address

**Check Firewall**: Some networks block streaming
- Temporarily disable firewall
- Check router settings
- Try different port if needed

**Check IP**: IP address changes when you reconnect to WiFi
- Use "🔄 Refresh IP Address" button in app
- Verify IP matches what's shown in browser

**Same Network**: Viewing device must be on same network (for local access)
- Both devices connected to same WiFi
- Not using VPN unless intentional
- No network isolation enabled

### Stream Stops When Screen Off

**Check Permissions**: Ensure all permissions granted
- Camera permission
- Notification permission
- Review in Settings → Apps → CameraLink

**Battery Optimization**: Disable battery optimization for CameraLink
1. Settings → Apps → CameraLink
2. Battery → Unrestricted
3. This prevents Android from killing the service

**Check Notification**: Should show "Camera streaming active"
- If notification disappears, service was killed
- Check battery optimization settings

### Poor Stream Quality

**Network Speed**: Ensure good WiFi signal
- Move closer to router
- Check for interference
- Use 5GHz WiFi if available

**Reduce Load**: Close other apps on phone
- Free up memory
- Close background apps
- Restart phone if needed

**Router**: Check if router is overloaded
- Too many connected devices
- High bandwidth usage elsewhere
- Consider quality of service (QoS) settings

### Stream Works Locally But Not Remotely

**Port Forwarding**: Verify it's set up correctly
- Correct internal IP
- Correct port (8080)
- Both TCP and UDP if option available

**Public IP**: Your ISP might use carrier-grade NAT
- Use VPN solution instead
- Try dynamic DNS service
- Contact ISP about static IP

**Firewall**: Check router and ISP firewall settings
- Enable port forwarding in firewall
- Check ISP doesn't block ports
- Some ISPs block common ports

### Image Shows Corrupted/Garbled

**YUV Conversion Issue**: Should be fixed in current version
- Rebuild and reinstall app
- Check for updates
- Review logcat for errors

**Camera Format**: Some devices use different formats
- Try different camera (front/back)
- Check device compatibility
- Report device model if issue persists

### Black Screen in Browser

**No Frames Being Captured**:
- Check camera permission granted
- Ensure camera isn't used by another app
- Restart the stream

**Check Logcat** for errors:
```
adb logcat | findstr "StreamingServer"
```

Look for:
- "Frame updated, size: XXXXX bytes" (good)
- "ERROR - image is null" (bad)
- "WARNING - No frames available" (bad)

### Can't Access Specific Endpoints

**Test Each Endpoint**:
- `/` - Homepage (should load HTML)
- `/test` - Shows "Server is working!"
- `/snapshot` - Single JPEG image
- `/stream` - MJPEG video stream

**If snapshot works but stream doesn't**:
- Browser compatibility issue
- Try Chrome or Firefox
- Check MJPEG support

**If nothing works**:
- Server not running
- Check "Start Streaming" button pressed
- Verify green LIVE indicator showing

## Battery Considerations

The app uses a wake lock to keep streaming active, which consumes battery:

- **Average Usage**: ~10-15% per hour depending on device
- **To Minimize**:
  - Plug phone into charger for extended streaming
  - Reduce screen brightness (screen-off is best)
  - Close other apps
  - Use WiFi instead of mobile data
  - Disable unused features (Bluetooth, GPS, etc.)

## Security Notes

⚠️ **Important Security Considerations**:

1. **No Authentication**: The stream currently has no password protection
   - Anyone with the URL can view the stream
   - Consider adding authentication for public access

2. **Unencrypted**: HTTP (not HTTPS)
   - Data is not encrypted
   - Only use on trusted networks or VPN

3. **Physical Security**: 
   - Phone must be placed appropriately
   - Someone with physical access can stop the service

## Advanced Configuration

### Changing the Port

Edit `CameraStreamingService.kt`:
```kotlin
private val port = 8080  // Change to your desired port
```

### Changing Stream Quality

Edit `StreamingServer.kt` in `imageProxyToJpeg()`:
```kotlin
yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
// Change 80 to 60 for lower quality/size, or 95 for higher quality
```

### Using Front Camera

Edit `startCamera()` in `CameraStreamingService.kt`:
```kotlin
val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
```

### Frame Rate Adjustment

Edit `StreamingServer.kt` in the stream loop:
```kotlin
Thread.sleep(33)  // ~30 FPS (change to 16 for ~60 FPS, 66 for ~15 FPS)
```

## Project Structure

```
CameraLink/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/cameralink/
│   │   │   ├── MainActivity.kt              # App entry, permissions, service control
│   │   │   ├── CameraStreamingService.kt    # Background streaming service
│   │   │   ├── StreamingServer.kt           # HTTP server with MJPEG
│   │   │   └── ui/theme/                    # App theme
│   │   ├── res/                             # Resources
│   │   └── AndroidManifest.xml              # Permissions and service declaration
│   └── build.gradle.kts                     # Dependencies
├── build.gradle.kts                         # Project config
├── settings.gradle.kts                      # Project settings
└── README.md                                # This file
```

## Dependencies

```kotlin
// CameraX dependencies
implementation("androidx.camera:camera-camera2:1.3.0")
implementation("androidx.camera:camera-lifecycle:1.3.0")
implementation("androidx.camera:camera-view:1.3.0")

// Lifecycle service for background operation
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
implementation("androidx.lifecycle:lifecycle-service:2.6.1")

// HTTP Server for streaming
implementation("org.nanohttpd:nanohttpd:2.3.1")

// Permissions
implementation("com.google.accompanist:accompanist-permissions:0.33.2-alpha")

// Jetpack Compose
implementation("androidx.activity:activity-compose:1.8.0")
implementation(platform("androidx.compose:compose-bom:2024.09.00"))
implementation("androidx.compose.material3:material3")
```

## Build Instructions

### Prerequisites
- Android Studio (latest version)
- Android device with API 31 (Android 12) or higher
- USB cable for device connection

### Steps
1. **Open Project**: File → Open → Select CameraLink folder
2. **Sync Gradle**: File → Sync Project with Gradle Files
3. **Connect Device**: Enable USB Debugging and connect via USB
4. **Build & Run**: Run → Run 'app' (Shift+F10)

### Troubleshooting Build Issues

**Gradle Sync Fails**:
- Check internet connection
- Try: File → Invalidate Caches / Restart

**Device Not Detected**:
- Check USB cable connection
- Ensure USB Debugging is enabled
- Try a different USB port

**Build Errors**:
- Clean Project: Build → Clean Project
- Rebuild: Build → Rebuild Project
- Check Android SDK is installed (SDK 36)

## Use Cases

Perfect for:
- 🏠 Home security monitoring
- 👶 Baby monitor
- 🐾 Pet monitoring
- 🔒 DIY surveillance system
- 🔭 Remote observation
- 📹 Webcam replacement
- 🎥 Live event streaming
- 🔬 Scientific observation

## Performance Tips

1. **WiFi vs Mobile Data**: WiFi is much faster and more reliable
2. **5GHz WiFi**: Use 5GHz band if available for better bandwidth
3. **Router Placement**: Place phone and router close together
4. **Close Apps**: Close unnecessary background apps
5. **Cooling**: Ensure phone doesn't overheat during extended streaming
6. **Power**: Keep phone plugged in for long sessions
7. **Quality**: Lower quality settings if network is slow

## Debugging

### Logcat Commands

**Windows Command Prompt:**
```cmd
adb logcat | findstr "CameraLink StreamingServer"
```

**PowerShell:**
```powershell
adb logcat | Select-String "CameraLink|StreamingServer"
```

**Android Studio:**
- Filter: `CameraLink|StreamingServer`
- Level: Verbose

### Expected Log Output

When working correctly:
```
StreamingServer: Frame updated, size: 45678 bytes
StreamingServer: Request received for: /
StreamingServer: Serving homepage
StreamingServer: Request received for: /stream
StreamingServer: Starting MJPEG stream send loop
StreamingServer: Sent 30 frames
StreamingServer: Sent 60 frames
...
```

### Common Log Errors

- `ERROR - image is null` → Camera not capturing
- `WARNING - No frames available` → Image analyzer not working
- `ERROR in imageProxyToJpeg` → Conversion problem
- `Connection refused` → Server not started

## License

This project is open source and available for educational purposes.

## Future Enhancements

- [ ] HTTPS support for secure streaming
- [ ] Password protection / Authentication
- [ ] Multiple camera support (front/back switching)
- [ ] Stream quality settings UI
- [ ] Recording capability
- [ ] Motion detection
- [ ] Custom port selection
- [ ] QR code for easy URL sharing
- [ ] Multi-camera simultaneous streaming
- [ ] Cloud storage integration
- [ ] Two-way audio
- [ ] PTZ (Pan-Tilt-Zoom) support with device orientation

## Contributing

Contributions are welcome! Feel free to submit issues and pull requests.

---

## Changelog

### Version 1.1 - Background Streaming
- ✅ Added foreground service for background streaming
- ✅ Screen-off streaming support
- ✅ Wake lock implementation
- ✅ Persistent notification with stream URL
- ✅ Improved battery efficiency

### Version 1.0 - Initial Release
- ✅ Basic camera streaming
- ✅ Web-based viewer
- ✅ MJPEG format
- ✅ Snapshot support
- ✅ Local network access

---

**Made with ❤️ for the open source community**


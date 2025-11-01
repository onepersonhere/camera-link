# CameraLink - IP Camera App

Turn your Android phone into an IP camera and stream video footage over your local network!

## Features

- 📹 **Live Video Streaming**: Stream your phone's camera feed in real-time
- 🌐 **Web-Based Viewing**: View the stream from any device with a web browser
- 📱 **Simple Interface**: Easy-to-use UI with one-tap streaming
- 🔴 **Live Indicator**: Visual feedback when streaming is active
- 📸 **Snapshot Support**: Capture still images from the stream

## How It Works

1. **Grant Permissions**: On first launch, grant camera permissions
2. **Start Streaming**: Tap the "Start Streaming" button
3. **Get Stream URL**: The app will display your phone's IP address and port (e.g., `http://192.168.1.100:8080`)
4. **View on Other Devices**: Open the displayed URL in any web browser on the same network
5. **Watch Live Feed**: Enjoy your live camera stream!

## Technical Details

### Architecture

- **Camera**: Uses CameraX API for reliable camera access
- **Streaming**: MJPEG stream over HTTP using NanoHTTPD server
- **Port**: Default streaming port is 8080
- **Frame Rate**: Approximately 30 FPS
- **Format**: JPEG compression at 80% quality

### Requirements

- Android 12 (API 31) or higher
- Camera permission
- Internet permission (for network streaming)
- Both devices must be on the same network

### Network Configuration

The app automatically detects your phone's local IP address. Make sure:
- Your phone is connected to WiFi
- The viewing device is on the same network
- No firewall is blocking port 8080

## Usage Instructions

### On Your Phone:
1. Launch the CameraLink app
2. Grant camera permissions when prompted
3. Tap "Start Streaming"
4. Note the displayed URL

### On Viewing Device:
1. Open a web browser (Chrome, Firefox, Safari, etc.)
2. Enter the URL shown on your phone
3. View the live camera feed
4. Use the "Take Snapshot" button to capture still images

### Stop Streaming:
- Tap the "Stop Streaming" button on your phone
- The stream will immediately stop

## Dependencies

- **AndroidX CameraX**: Camera access and management
- **NanoHTTPD**: Lightweight HTTP server for streaming
- **Accompanist Permissions**: Permission handling in Compose
- **Jetpack Compose**: Modern Android UI framework

## Development

### Build Instructions

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Run on an Android device (API 31+)

### Project Structure

```
app/
├── src/main/java/com/example/cameralink/
│   ├── MainActivity.kt          # Main activity and permissions
│   ├── CameraScreen.kt          # Camera preview and controls
│   ├── StreamingServer.kt       # HTTP server for streaming
│   └── ui/theme/                # App theme
└── AndroidManifest.xml          # Permissions and configuration
```

## Troubleshooting

### Can't access the stream?
- Ensure both devices are on the same WiFi network
- Check that the IP address is correct
- Verify no firewall is blocking port 8080
- Try restarting the stream

### Black screen on camera?
- Grant camera permissions in app settings
- Restart the app
- Check if another app is using the camera

### Poor streaming quality?
- Move closer to your WiFi router
- Reduce network traffic
- Close other apps on your phone

## Security Note

⚠️ This app streams over HTTP without encryption or authentication. Only use on trusted networks. The stream is accessible to anyone on the same network who knows your IP address.

## License

This project is open source and available for educational purposes.

## Future Enhancements

- [ ] HTTPS support for secure streaming
- [ ] Password protection
- [ ] Multiple camera support (front/back switching)
- [ ] Stream quality settings
- [ ] Recording capability
- [ ] Motion detection
- [ ] Custom port selection
- [ ] QR code for easy URL sharing

## Contributing

Contributions are welcome! Feel free to submit issues and pull requests.


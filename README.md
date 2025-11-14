# CameraLink

CameraLink turns any Android 12+ phone into an HTTP-based IP camera with an optional Tailscale keep-alive service. The app keeps streaming even with the screen off, exposes a browser-friendly MJPEG feed, and periodically pings configured Tailscale peers so remote access stays responsive.

## Feature Highlights
- Live MJPEG streaming over HTTP, viewable from any modern browser or VLC
- Foreground camera service with wake lock for reliable screen-off streaming
- Built-in HTTP endpoints for `/`, `/stream`, `/snapshot`, and `/test`
- Background Tailscale ping service with configurable peers and interval
- Persistent notifications for both streaming and pinging with quick controls
- Snapshot capture, multi-viewer support, and MagicDNS hostname resolution

## Architecture Overview
- **CameraStreamingService**: Foreground service that owns CameraX capture, wake locks, and lifecycle.
- **StreamingServer**: Embedded NanoHTTPD server that serves frames as MJPEG or single JPEG snapshots.
- **TailscalePingService + TailscalePinger**: Foreground service that resolves peers, issues ICMP pings every 15 seconds by default, and updates status in notifications/UI.
- **MainActivity**: Compose UI for starting/stopping services, showing stream URLs, peer status, and editing peer lists.

The app targets Android API level 31+ and is written entirely in Kotlin with Jetpack Compose and CameraX.

## Getting Started

### Prerequisites
- Android Studio Ladybug or newer
- Android SDK 31 or higher installed locally
- A physical Android device running Android 12+ with USB debugging enabled
- Java 17+ if building from the command line

### Clone and Build
```bash
git clone https://github.com/yourusername/camera-link.git
cd camera-link
./gradlew assembleDebug
```

### Install
- **Android Studio**: Open the project, sync Gradle, connect a device, and press Run to deploy the debug build.
- **Command line**:
  ```bash
  ./gradlew installDebug
  # or manually install the generated APK
  adb install app/build/outputs/apk/debug/app-debug.apk
  ```

## Usage

### Start Streaming
1. Launch CameraLink on the device.
2. Grant camera, notification, and foreground service permissions when prompted.
3. Tap **Start Streaming**. The UI and notification display the local stream URL (default `http://<device-ip>:8080`).
4. Open the URL from any device on the same network to view the live feed or trigger `/snapshot`.
5. Stop streaming from the in-app button or the notification action.

Screen-off and background streaming remain active as long as the foreground service runs. Disable battery optimizations for best reliability.

### Tailscale Keep-Alive
1. TailscalePingService starts automatically (configurable in `MainActivity`).
2. Every 15 seconds (default) it resolves configured peers (MagicDNS or 100.64.0.0/10 addresses) and pings them.
3. Status appears in app and notification (successful/failed counts).
4. Use the **Manage Tailscale Peers** section to add or remove peers at runtime.

## Configuration
- **Ping interval**: `app/src/main/java/.../TailscalePingService.kt`, `PING_INTERVAL_MS` constant.
- **Default peers**: `TailscalePinger.kt`, `configuredTailscaleIps` set.
- **HTTP port**: `CameraStreamingService.kt` and `MainActivity.kt` `port` value (default 8080).
- **Camera selection**: Update `cameraSelector` in `CameraStreamingService.startCamera()` to choose front or back camera.
- **JPEG quality / FPS**: Adjust compression quality in `StreamingServer.imageProxyToJpeg()` and sleep duration in the streaming loop respectively.

## Testing
- **Local browser/VLC test**: Start streaming, visit `/stream` or `/snapshot` from another device, or add the URL to VLC via “Open Network Stream.”
- **Service longevity**: Leave the stream running 30+ minutes with the screen off to confirm wake lock behavior.
- **Tailscale ping verification**: Run `adb logcat | grep TailscalePing` to confirm resolution and ping outcomes.

## Development Notes
- Project structure follows the standard Android Gradle layout under `app/src/main`.
- Dependencies include CameraX, Jetpack Compose, NanoHTTPD, Lifecycle runtime/service, Accompanist Permissions, WorkManager, and Kotlin coroutines.
- Common commands:
  ```bash
  ./gradlew lint
  ./gradlew test
  ./gradlew assembleRelease
  ```
- When modifying streaming code, test on physical hardware; emulators lack the necessary camera and network characteristics.

## Contributing
Issues and pull requests are welcome. Please include reproducible steps, device details, and logs for bug reports. For larger changes, open an issue first so we can discuss design and testing expectations.

## License
CameraLink is distributed under the [MIT License](./LICENSE). By contributing, you agree that your contributions will be licensed under the same terms.

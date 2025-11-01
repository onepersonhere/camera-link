package com.example.cameralink

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cameralink.ui.theme.CameraLinkTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraLinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraApp() {
    val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.INTERNET
    )

    // Add notification permission for Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(Unit) {
        permissionsState.launchMultiplePermissionRequest()
    }

    when {
        permissionsState.allPermissionsGranted -> {
            ServiceControlScreen()
        }
        permissionsState.shouldShowRationale -> {
            PermissionRationale(
                onRequestPermission = {
                    permissionsState.launchMultiplePermissionRequest()
                }
            )
        }
        else -> {
            PermissionRequest(
                onRequestPermission = {
                    permissionsState.launchMultiplePermissionRequest()
                }
            )
        }
    }
}

@Composable
fun ServiceControlScreen() {
    val context = LocalContext.current
    var isStreaming by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf(getIpAddress()) }
    val port = 8080

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📹",
            style = MaterialTheme.typography.displayLarge,
            color = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "CameraLink Background Streaming",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isStreaming) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a2a))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔴 STREAMING",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Stream URL:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "http://$ipAddress:$port",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "✅ Camera is streaming in the background\n" +
                               "✅ Works even when screen is off\n" +
                               "✅ Access from anywhere on your network",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "You can close this app or turn off the screen.\n" +
                       "The camera will continue streaming.\n" +
                       "Check the notification for the stream URL.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "Start the background streaming service to access your camera remotely. " +
                       "The stream will continue even when your screen is off.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (isStreaming) {
                    CameraStreamingService.stopService(context)
                    isStreaming = false
                } else {
                    CameraStreamingService.startService(context, port)
                    ipAddress = getIpAddress()
                    isStreaming = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isStreaming) Color.Red else Color(0xFF4CAF50)
            )
        ) {
            Text(
                text = if (isStreaming) "⏹ Stop Streaming" else "▶ Start Streaming",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (isStreaming) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    ipAddress = getIpAddress()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2a2a2a)
                )
            ) {
                Text(
                    text = "🔄 Refresh IP Address",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun getIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            val addrs = intf.inetAddresses
            for (addr in addrs) {
                if (!addr.isLoopbackAddress) {
                    val hostAddress = addr.hostAddress
                    if (hostAddress != null && hostAddress.indexOf(':') < 0) {
                        return hostAddress
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "Unable to get IP"
}

@Composable
fun PermissionRequest(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📹",
            style = MaterialTheme.typography.displayLarge,
            color = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "CameraLink needs camera permission to stream video to other devices on your network.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.LightGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text(
                text = "Grant Permission",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionRationale(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayLarge,
            color = Color(0xFFFFA726)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Permission Denied",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Camera permission is essential for this app to function. Please grant the permission to continue.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.LightGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text(
                text = "Try Again",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
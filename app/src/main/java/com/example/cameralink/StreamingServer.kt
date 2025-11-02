package com.example.cameralink

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

class StreamingServer(port: Int) : NanoHTTPD(port) {

    private val currentFrame = AtomicReference<ByteArray>()

    @androidx.camera.core.ExperimentalGetImage
    fun updateFrame(imageProxy: ImageProxy) {
        try {
            val jpegBytes = imageProxyToJpeg(imageProxy)
            if (jpegBytes.isNotEmpty()) {
                currentFrame.set(jpegBytes)
                println("StreamingServer: Frame updated, size: ${jpegBytes.size} bytes")
            } else {
                println("StreamingServer: WARNING - Empty frame generated!")
            }
        } catch (e: Exception) {
            println("StreamingServer: ERROR in updateFrame: ${e.message}")
            e.printStackTrace()
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun imageProxyToJpeg(imageProxy: ImageProxy): ByteArray {
        try {
            val image = imageProxy.image
            if (image == null) {
                println("StreamingServer: ERROR - image is null")
                return ByteArray(0)
            }

            val width = imageProxy.width
            val height = imageProxy.height

            // Get the YUV planes
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val ySize = width * height
            val uvSize = width * height / 2

            val nv21 = ByteArray(ySize + uvSize)

            // Copy Y plane - handle row stride
            val yBuffer = yPlane.buffer
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride

            yBuffer.rewind()

            if (yPixelStride == 1 && yRowStride == width) {
                // Optimized path: no padding
                yBuffer.get(nv21, 0, ySize)
            } else {
                // Handle padding: copy row by row
                var pos = 0
                for (row in 0 until height) {
                    yBuffer.position(row * yRowStride)
                    yBuffer.get(nv21, pos, width)
                    pos += width
                }
            }

            // Convert U and V planes to NV21 format (interleaved VUVUVU...)
            val vBuffer = vPlane.buffer
            val uBuffer = uPlane.buffer
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            vBuffer.rewind()
            uBuffer.rewind()

            var pos = ySize

            if (uvPixelStride == 1) {
                // U and V are already packed, just interleave them
                for (row in 0 until height / 2) {
                    vBuffer.position(row * uvRowStride)
                    uBuffer.position(row * uvRowStride)
                    @Suppress("UNUSED_VARIABLE")
                    for (col in 0 until width / 2) {
                        nv21[pos++] = vBuffer.get()
                        nv21[pos++] = uBuffer.get()
                    }
                }
            } else {
                // U and V have pixel stride > 1, need to sample
                for (row in 0 until height / 2) {
                    for (col in 0 until width / 2) {
                        val offset = row * uvRowStride + col * uvPixelStride
                        vBuffer.position(offset)
                        uBuffer.position(offset)
                        nv21[pos++] = vBuffer.get()
                        nv21[pos++] = uBuffer.get()
                    }
                }
            }

            // Convert to JPEG
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
            val result = out.toByteArray()

            return result
        } catch (e: Exception) {
            println("StreamingServer: ERROR in imageProxyToJpeg: ${e.message}")
            e.printStackTrace()
            return ByteArray(0)
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        println("StreamingServer: Request received for: $uri")

        return when {
            uri == "/" || uri == "/index.html" -> {
                println("StreamingServer: Serving homepage")
                serveHomePage()
            }
            uri == "/stream" -> {
                println("StreamingServer: Serving MJPEG stream")
                serveMjpegStream()
            }
            uri == "/snapshot" -> {
                println("StreamingServer: Serving snapshot")
                serveSnapshot()
            }
            uri == "/test" -> {
                println("StreamingServer: Test endpoint called")
                newFixedLengthResponse(Response.Status.OK, "text/plain", "Server is working! Frame available: ${currentFrame.get() != null}")
            }
            uri == "/api/tailscale/ping" -> {
                println("StreamingServer: Tailscale ping endpoint called")
                handleTailscalePing()
            }
            uri == "/api/tailscale/peers" && session.method == Method.GET -> {
                println("StreamingServer: Get peers endpoint called")
                handleGetPeers()
            }
            uri == "/api/tailscale/peers" && session.method == Method.POST -> {
                println("StreamingServer: Add peer endpoint called")
                handleAddPeer(session)
            }
            uri == "/api/tailscale/peers" && session.method == Method.DELETE -> {
                println("StreamingServer: Remove peer endpoint called")
                handleRemovePeer(session)
            }
            else -> {
                println("StreamingServer: 404 - Not found: $uri")
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        }
    }

    private fun handleTailscalePing(): Response {
        return try {
            val results = runBlocking {
                TailscalePinger.pingAllTailscaleConnections()
            }

            val jsonResponse = JSONObject()
            val peersArray = JSONArray()

            results.forEach { (target, success) ->
                val peerObj = JSONObject()
                peerObj.put("target", target)
                peerObj.put("online", success)
                peersArray.put(peerObj)
            }

            jsonResponse.put("success", true)
            jsonResponse.put("peers", peersArray)
            jsonResponse.put("timestamp", System.currentTimeMillis())

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                jsonResponse.toString()
            )
        } catch (e: Exception) {
            println("StreamingServer: Error in ping: ${e.message}")
            val errorResponse = JSONObject()
            errorResponse.put("success", false)
            errorResponse.put("error", e.message)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                errorResponse.toString()
            )
        }
    }

    private fun handleGetPeers(): Response {
        return try {
            val peers = TailscalePinger.getConfiguredIps()
            val jsonResponse = JSONObject()
            val peersArray = JSONArray()

            peers.forEach { peer ->
                peersArray.put(peer)
            }

            jsonResponse.put("success", true)
            jsonResponse.put("peers", peersArray)

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                jsonResponse.toString()
            )
        } catch (e: Exception) {
            val errorResponse = JSONObject()
            errorResponse.put("success", false)
            errorResponse.put("error", e.message)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                errorResponse.toString()
            )
        }
    }

    private fun handleAddPeer(session: IHTTPSession): Response {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val postData = files["postData"] ?: ""

            val json = JSONObject(postData)
            val target = json.getString("target")

            TailscalePinger.addTailscaleIp(target)

            val jsonResponse = JSONObject()
            jsonResponse.put("success", true)
            jsonResponse.put("message", "Peer added successfully")
            jsonResponse.put("target", target)

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                jsonResponse.toString()
            )
        } catch (e: Exception) {
            println("StreamingServer: Error adding peer: ${e.message}")
            val errorResponse = JSONObject()
            errorResponse.put("success", false)
            errorResponse.put("error", e.message)
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                errorResponse.toString()
            )
        }
    }

    private fun handleRemovePeer(session: IHTTPSession): Response {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val postData = files["postData"] ?: ""

            val json = JSONObject(postData)
            val target = json.getString("target")

            TailscalePinger.removeTailscaleIp(target)

            val jsonResponse = JSONObject()
            jsonResponse.put("success", true)
            jsonResponse.put("message", "Peer removed successfully")
            jsonResponse.put("target", target)

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                jsonResponse.toString()
            )
        } catch (e: Exception) {
            val errorResponse = JSONObject()
            errorResponse.put("success", false)
            errorResponse.put("error", e.message)
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                errorResponse.toString()
            )
        }
    }

    private fun serveHomePage(): Response {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>IP Camera Stream</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        margin: 0;
                        padding: 20px;
                        font-family: Arial, sans-serif;
                        background-color: #1a1a1a;
                        color: #ffffff;
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                    }
                    h1 {
                        text-align: center;
                        color: #4CAF50;
                    }
                    h2 {
                        color: #4CAF50;
                        margin-top: 30px;
                    }
                    .video-container {
                        position: relative;
                        width: 100%;
                        max-width: 800px;
                        margin: 20px auto;
                        border: 2px solid #4CAF50;
                        border-radius: 8px;
                        overflow: hidden;
                        background-color: #000;
                        min-height: 400px;
                    }
                    img {
                        width: 100%;
                        height: auto;
                        display: block;
                    }
                    .info {
                        text-align: center;
                        margin-top: 20px;
                        padding: 15px;
                        background-color: #2a2a2a;
                        border-radius: 8px;
                    }
                    .button {
                        display: inline-block;
                        padding: 10px 20px;
                        margin: 10px;
                        background-color: #4CAF50;
                        color: white;
                        text-decoration: none;
                        border-radius: 4px;
                        cursor: pointer;
                        border: none;
                        font-size: 14px;
                    }
                    .button:hover {
                        background-color: #45a049;
                    }
                    .button:disabled {
                        background-color: #666;
                        cursor: not-allowed;
                    }
                    .debug-info {
                        margin-top: 20px;
                        padding: 15px;
                        background-color: #2a2a2a;
                        border-radius: 8px;
                        font-family: monospace;
                        font-size: 12px;
                        color: #aaa;
                    }
                    .error {
                        color: #ff5555;
                        padding: 10px;
                        text-align: center;
                    }
                    .tailscale-section {
                        margin-top: 30px;
                        padding: 20px;
                        background-color: #2a2a2a;
                        border-radius: 8px;
                    }
                    .peer-list {
                        margin-top: 15px;
                    }
                    .peer-item {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: 10px;
                        margin: 5px 0;
                        background-color: #1a1a1a;
                        border-radius: 4px;
                        border-left: 3px solid #666;
                    }
                    .peer-item.online {
                        border-left-color: #4CAF50;
                    }
                    .peer-item.offline {
                        border-left-color: #ff5555;
                    }
                    .peer-name {
                        flex: 1;
                        font-family: monospace;
                    }
                    .peer-status {
                        margin: 0 10px;
                        font-weight: bold;
                    }
                    .status-online {
                        color: #4CAF50;
                    }
                    .status-offline {
                        color: #ff5555;
                    }
                    .remove-btn {
                        padding: 5px 10px;
                        background-color: #ff5555;
                        color: white;
                        border: none;
                        border-radius: 3px;
                        cursor: pointer;
                        font-size: 12px;
                    }
                    .remove-btn:hover {
                        background-color: #ff3333;
                    }
                    .add-peer-form {
                        display: flex;
                        gap: 10px;
                        margin-top: 15px;
                    }
                    .add-peer-form input {
                        flex: 1;
                        padding: 10px;
                        background-color: #1a1a1a;
                        border: 1px solid #4CAF50;
                        border-radius: 4px;
                        color: white;
                        font-family: monospace;
                    }
                    .add-peer-form input::placeholder {
                        color: #666;
                    }
                    .ping-status {
                        margin-top: 15px;
                        padding: 10px;
                        background-color: #1a1a1a;
                        border-radius: 4px;
                        text-align: center;
                    }
                    .loading {
                        color: #ffa500;
                    }
                    .section-divider {
                        margin: 30px 0;
                        border-top: 1px solid #444;
                    }
                </style>
                <script>
                    let pingInProgress = false;

                    window.onload = function() {
                        var img = document.getElementById('stream');
                        var status = document.getElementById('status');
                        
                        img.onerror = function() {
                            status.innerHTML = '<div class="error">❌ Stream failed to load. Check console for details.</div>';
                            console.error('Stream image failed to load from /stream endpoint');
                        };
                        
                        img.onload = function() {
                            status.innerHTML = '<div style="color: #4CAF50;">✅ Stream loaded successfully!</div>';
                            console.log('Stream image loaded successfully');
                        };
                        
                        // Test server connectivity
                        fetch('/test')
                            .then(response => response.text())
                            .then(data => {
                                console.log('Server test:', data);
                                document.getElementById('server-test').innerText = 'Server: ' + data;
                            })
                            .catch(err => {
                                console.error('Server test failed:', err);
                                document.getElementById('server-test').innerText = 'Server: ERROR - ' + err.message;
                            });

                        // Load Tailscale peers
                        loadPeers();
                    };

                    async function loadPeers() {
                        try {
                            const response = await fetch('/api/tailscale/peers');
                            const data = await response.json();
                            
                            if (data.success) {
                                displayPeers(data.peers);
                            } else {
                                console.error('Failed to load peers:', data.error);
                            }
                        } catch (error) {
                            console.error('Error loading peers:', error);
                        }
                    }

                    function displayPeers(peers, pingResults = null) {
                        const peerList = document.getElementById('peer-list');
                        
                        if (peers.length === 0) {
                            peerList.innerHTML = '<div style="color: #666; text-align: center; padding: 20px;">No peers configured. Add one below!</div>';
                            return;
                        }

                        peerList.innerHTML = peers.map(peer => {
                            let statusClass = '';
                            let statusText = '';
                            let statusEmoji = '';
                            
                            if (pingResults && pingResults[peer] !== undefined) {
                                if (pingResults[peer]) {
                                    statusClass = 'online';
                                    statusText = 'status-online';
                                    statusEmoji = '✅ Online';
                                } else {
                                    statusClass = 'offline';
                                    statusText = 'status-offline';
                                    statusEmoji = '❌ Offline';
                                }
                            }

                            return `
                                <div class="peer-item ${'$'}{statusClass}">
                                    <span class="peer-name">${'$'}{peer}</span>
                                    <span class="peer-status ${'$'}{statusText}">${'$'}{statusEmoji}</span>
                                    <button class="remove-btn" onclick="removePeer('${'$'}{peer}')">Remove</button>
                                </div>
                            `;
                        }).join('');
                    }

                    async function pingNow() {
                        if (pingInProgress) return;

                        pingInProgress = true;
                        const pingBtn = document.getElementById('ping-btn');
                        const pingStatus = document.getElementById('ping-status');
                        
                        pingBtn.disabled = true;
                        pingStatus.innerHTML = '<span class="loading">🔄 Pinging all devices...</span>';

                        try {
                            const response = await fetch('/api/tailscale/ping');
                            const data = await response.json();
                            
                            if (data.success) {
                                const results = {};
                                data.peers.forEach(peer => {
                                    results[peer.target] = peer.online;
                                });

                                const onlineCount = data.peers.filter(p => p.online).length;
                                const totalCount = data.peers.length;
                                const timestamp = new Date(data.timestamp).toLocaleTimeString();

                                pingStatus.innerHTML = `✅ Ping complete: ${'$'}{onlineCount}/${'$'}{totalCount} devices online (${'$'}{timestamp})`;
                                
                                // Update peer list with results
                                const peersResponse = await fetch('/api/tailscale/peers');
                                const peersData = await peersResponse.json();
                                if (peersData.success) {
                                    displayPeers(peersData.peers, results);
                                }
                            } else {
                                pingStatus.innerHTML = `<span class="error">❌ Ping failed: ${'$'}{data.error}</span>`;
                            }
                        } catch (error) {
                            console.error('Ping error:', error);
                            pingStatus.innerHTML = `<span class="error">❌ Error: ${'$'}{error.message}</span>`;
                        } finally {
                            pingInProgress = false;
                            pingBtn.disabled = false;
                        }
                    }

                    async function addPeer() {
                        const input = document.getElementById('peer-input');
                        const target = input.value.trim();

                        if (!target) {
                            alert('Please enter a Tailscale IP or hostname');
                            return;
                        }

                        try {
                            const response = await fetch('/api/tailscale/peers', {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json'
                                },
                                body: JSON.stringify({ target: target })
                            });

                            const data = await response.json();
                            
                            if (data.success) {
                                input.value = '';
                                await loadPeers();
                                alert('Peer added successfully!');
                            } else {
                                alert('Failed to add peer: ' + data.error);
                            }
                        } catch (error) {
                            console.error('Error adding peer:', error);
                            alert('Error: ' + error.message);
                        }
                    }

                    async function removePeer(target) {
                        if (!confirm(`Remove peer "${'$'}{target}"?`)) {
                            return;
                        }

                        try {
                            const response = await fetch('/api/tailscale/peers', {
                                method: 'DELETE',
                                headers: {
                                    'Content-Type': 'application/json'
                                },
                                body: JSON.stringify({ target: target })
                            });

                            const data = await response.json();
                            
                            if (data.success) {
                                await loadPeers();
                            } else {
                                alert('Failed to remove peer: ' + data.error);
                            }
                        } catch (error) {
                            console.error('Error removing peer:', error);
                            alert('Error: ' + error.message);
                        }
                    }

                    // Auto-ping every 30 seconds
                    setInterval(() => {
                        if (!pingInProgress) {
                            pingNow();
                        }
                    }, 30000);
                </script>
            </head>
            <body>
                <div class="container">
                    <h1>📹 CameraLink IP Camera</h1>
                    <div id="status"></div>
                    <div class="video-container">
                        <img id="stream" src="/stream" alt="Camera Stream">
                    </div>
                    <div class="info">
                        <p><strong>Stream Active</strong></p>
                        <a href="/snapshot" class="button" target="_blank">📸 Take Snapshot</a>
                        <a href="/test" class="button" target="_blank">🔧 Test Server</a>
                    </div>

                    <div class="section-divider"></div>

                    <div class="tailscale-section">
                        <h2>🔗 Tailscale Keep-Alive</h2>
                        <p style="color: #aaa;">Monitor and manage Tailscale peer connections</p>
                        
                        <div style="text-align: center; margin: 20px 0;">
                            <button id="ping-btn" class="button" onclick="pingNow()">🔄 Ping All Devices</button>
                        </div>
                        
                        <div id="ping-status" class="ping-status">
                            Click "Ping All Devices" to check connection status
                        </div>

                        <h3 style="color: #4CAF50; margin-top: 25px;">📋 Configured Peers</h3>
                        <div id="peer-list" class="peer-list">
                            Loading peers...
                        </div>

                        <h3 style="color: #4CAF50; margin-top: 25px;">➕ Add Peer</h3>
                        <div class="add-peer-form">
                            <input 
                                type="text" 
                                id="peer-input" 
                                placeholder="Enter Tailscale IP (e.g., 100.64.1.5) or hostname (e.g., my-laptop)"
                                onkeypress="if(event.key === 'Enter') addPeer()"
                            />
                            <button class="button" onclick="addPeer()">Add Peer</button>
                        </div>
                        <p style="color: #666; font-size: 12px; margin-top: 10px;">
                            💡 Tip: Use Tailscale MagicDNS hostnames (e.g., "laptop-name") or IP addresses (100.64-127.x.x range)
                        </p>
                    </div>

                    <div class="debug-info">
                        <strong>Debug Info:</strong><br>
                        <div id="server-test">Testing server connection...</div>
                        Stream URL: /stream<br>
                        Snapshot URL: /snapshot<br>
                        Tailscale API: /api/tailscale/*<br>
                        Open browser console (F12) for more details
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun serveMjpegStream(): Response {
        println("StreamingServer: MJPEG stream requested")

        return newChunkedResponse(
            Response.Status.OK,
            "multipart/x-mixed-replace; boundary=frame",
            object : java.io.InputStream() {
                private var frameIterator = 0
                private var currentData: ByteArrayInputStream? = null

                override fun read(): Int {
                    while (true) {
                        if (currentData != null) {
                            val b = currentData?.read() ?: -1
                            if (b != -1) return b
                            currentData = null
                        }

                        val frame = currentFrame.get()
                        if (frame != null && frame.isNotEmpty()) {
                            val header = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
                            val footer = "\r\n"

                            val combined = header.toByteArray() + frame + footer.toByteArray()
                            currentData = ByteArrayInputStream(combined)

                            frameIterator++
                            if (frameIterator % 30 == 0) {
                                println("StreamingServer: Sent $frameIterator frames")
                            }
                        } else {
                            if (frameIterator == 0) {
                                println("StreamingServer: WARNING - No frames available yet")
                            }
                            Thread.sleep(33)
                        }
                    }
                }

                override fun available(): Int = 1
            }
        )
    }

    private fun serveSnapshot(): Response {
        val frame = currentFrame.get()
        println("StreamingServer: Snapshot requested, frame available: ${frame != null}, size: ${frame?.size ?: 0}")
        return if (frame != null && frame.isNotEmpty()) {
            newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(frame), frame.size.toLong())
        } else {
            newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "No frame available")
        }
    }
}


package com.example.cameralink

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class StreamingServer(port: Int) : NanoHTTPD(port) {

    private val currentFrame = AtomicReference<ByteArray>()

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
            else -> {
                println("StreamingServer: 404 - Not found: $uri")
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
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
                    }
                    .button:hover {
                        background-color: #45a049;
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
                </style>
                <script>
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
                    };
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
                    <div class="debug-info">
                        <strong>Debug Info:</strong><br>
                        <div id="server-test">Testing server connection...</div>
                        Stream URL: /stream<br>
                        Snapshot URL: /snapshot<br>
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


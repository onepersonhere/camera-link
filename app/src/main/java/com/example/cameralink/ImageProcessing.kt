package com.example.cameralink

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalGetImage::class)
fun imageProxyToJpegByteArray(imageProxy: ImageProxy): ByteArray {
    return try {
        val image = imageProxy.image ?: return ByteArray(0)

        val width = imageProxy.width
        val height = imageProxy.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val ySize = width * height
        val uvSize = width * height / 2

        val nv21 = ByteArray(ySize + uvSize)

        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        yBuffer.rewind()

        if (yPixelStride == 1 && yRowStride == width) {
            yBuffer.get(nv21, 0, ySize)
        } else {
            var pos = 0
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        vBuffer.rewind()
        uBuffer.rewind()

        var pos = ySize

        if (uvPixelStride == 1) {
            for (row in 0 until height / 2) {
                vBuffer.position(row * uvRowStride)
                uBuffer.position(row * uvRowStride)
                for (col in 0 until width / 2) {
                    nv21[pos++] = vBuffer.get()
                    nv21[pos++] = uBuffer.get()
                }
            }
        } else {
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

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
        out.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        ByteArray(0)
    }
}

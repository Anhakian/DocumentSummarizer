package com.example.documentsummarizer.utils
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object ImageUtils {

    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        return when (image.format) {
            ImageFormat.JPEG -> decodeJpeg(image)
            ImageFormat.YUV_420_888 -> decodeYuv(image)
            else -> error("Unsupported ImageProxy format: ${image.format}")
        }
    }

    private fun decodeJpeg(image: ImageProxy): Bitmap {
        // Plane 0 holds the full JPEG. Ensure we read from position 0.
        val buf = image.planes[0].buffer.duplicate().apply { rewind() }
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)

        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        return bmp ?: error("Failed to decode JPEG bytes (size=${bytes.size})")
    }

    private fun decodeYuv(image: ImageProxy): Bitmap {
        val nv21 = yuv420888ToNv21(image)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 85, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Failed to decode YUV-JPEG bytes (size=${bytes.size})")
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val w = image.width
        val h = image.height
        val y = image.planes[0].buffer.duplicate().apply { rewind() }
        val u = image.planes[1].buffer.duplicate().apply { rewind() }
        val v = image.planes[2].buffer.duplicate().apply { rewind() }

        val ySize = y.remaining()
        val out = ByteArray(ySize + w * h / 2)

        y.get(out, 0, ySize)

        var offset = ySize
        val rowCount = h / 2
        val colCount = w / 2
        val uRS = image.planes[1].rowStride
        val vRS = image.planes[2].rowStride
        val uPS = image.planes[1].pixelStride
        val vPS = image.planes[2].pixelStride

        for (row in 0 until rowCount) {
            val uRow = row * uRS
            val vRow = row * vRS
            for (col in 0 until colCount) {
                out[offset++] = v.get(vRow + col * vPS)
                out[offset++] = u.get(uRow + col * uPS)
            }
        }
        return out
    }

    fun rotateBitmapIfNeeded(bmp: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bmp
        val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}
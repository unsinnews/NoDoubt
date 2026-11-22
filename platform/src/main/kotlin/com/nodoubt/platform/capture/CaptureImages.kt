package com.nodoubt.platform.capture

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.util.Base64
import com.nodoubt.core.model.ContentPart
import java.io.ByteArrayOutputStream

internal object CaptureImages {
    fun bitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val width = image.width
        val paddedWidth = plane.rowStride / plane.pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        return if (paddedWidth == width) padded else Bitmap.createBitmap(padded, 0, 0, width, image.height)
            .also { padded.recycle() }
    }

    fun encode(context: Context, bitmap: Bitmap, save: Boolean): ContentPart.Image {
        try {
            // A gallery write failure (e.g. revoked permission, full storage) must not block solving.
            if (save) runCatching { GalleryScreenshots.save(context, bitmap) }
            val scale = (1600f / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
            val compressed = if (scale < 1f) Bitmap.createScaledBitmap(bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
            val bytes = ByteArrayOutputStream().use {
                compressed.compress(Bitmap.CompressFormat.JPEG, 85, it)
                it.toByteArray()
            }
            if (compressed !== bitmap) compressed.recycle()
            return ContentPart.Image(Base64.encodeToString(bytes, Base64.NO_WRAP))
        } finally { bitmap.recycle() }
    }
}

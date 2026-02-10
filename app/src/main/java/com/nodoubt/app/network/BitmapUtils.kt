package com.nodoubt.app.network

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object BitmapUtils {

    fun toBase64(bitmap: Bitmap, quality: Int = 90): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun toDataUrl(bitmap: Bitmap, quality: Int = 90): String {
        val base64 = toBase64(bitmap, quality)
        return "data:image/jpeg;base64,$base64"
    }
}

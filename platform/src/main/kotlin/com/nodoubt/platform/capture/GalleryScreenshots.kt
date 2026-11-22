package com.nodoubt.platform.capture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes kept screenshots to the system gallery album 「不惑」 (Pictures/不惑) so they stay after uninstall.
 * Android 10+ uses MediaStore without any permission; Android 6–9 needs WRITE_EXTERNAL_STORAGE and a scan.
 */
object GalleryScreenshots {
    const val ALBUM = "不惑"

    /** Whether saving can work right now; only Android 6–9 ever needs the storage permission. */
    fun canSave(context: Context): Boolean = !needsPermission() ||
        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun needsPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    internal fun save(context: Context, bitmap: Bitmap, now: Long = System.currentTimeMillis()) {
        val name = "不惑_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(now))}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToMediaStore(context, bitmap, name, now)
        else saveToPublicDirectory(context, bitmap, name)
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap, name: String, now: Long) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法保存截图到相册")
        try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: error("无法保存截图到相册")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicDirectory(context: Context, bitmap: Bitmap, name: String) {
        check(canSave(context)) { "需要存储权限才能保存截图" }
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        check(directory.exists() || directory.mkdirs()) { "无法创建相册目录" }
        val file = File(directory, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
    }
}

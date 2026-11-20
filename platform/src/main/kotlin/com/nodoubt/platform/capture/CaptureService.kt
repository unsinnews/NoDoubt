package com.nodoubt.platform.capture

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.Image
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val frames = CaptureFrameGate()
    private var saveImage = false
    private val timeout = Runnable {
        frames.pendingRequestId?.let { fail("未获取到画面，请重新授权", it, ready = false) }
    }
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            CaptureBus.status(CaptureStatus(error = "授权已失效，点击截图重新授权"))
            stopSelf()
        }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width > 0 && height > 0 && (reader?.width != width || reader?.height != height)) {
                val ticket = frames.pendingRequestId?.let(frames::begin)
                display?.resize(width, height, resources.displayMetrics.densityDpi)
                createReader(width, height, ticket)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        val requestId = intent.getLongExtra(REQUEST_ID, 0)
        if (requestId > 0 && !CaptureBus.isCurrentRequest(requestId)) {
            if (projection == null) stopSelf()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_CAPTURE) {
            saveImage = intent.getBooleanExtra(SAVE_IMAGE, false)
            if (projection == null) fail("请先授权截图。", requestId) else requestFrame(requestId)
            return START_NOT_STICKY
        }
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(RESULT_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(RESULT_DATA)
        val result = intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED)
        if (data == null || result != Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }
        saveImage = intent.getBooleanExtra(SAVE_IMAGE, false)
        try {
            foreground()
            releaseProjection()
            projection = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(result, data)
            projection!!.registerCallback(callback, handler)
            val metrics = resources.displayMetrics
            createReader(metrics.widthPixels, metrics.heightPixels)
            display = projection!!.createVirtualDisplay("NoDoubt capture", metrics.widthPixels,
                metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, handler)
            CaptureBus.status(CaptureStatus(ready = true, requestId = requestId))
            if (intent.getBooleanExtra(AUTO_CAPTURE, false)) handler.postDelayed({ requestFrame(requestId) }, 700)
        } catch (error: Exception) {
            fail(error.message ?: "无法截图，请重新授权", requestId)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createReader(width: Int, height: Int, ticket: CaptureFrameTicket? = null) {
        // Rebinding the existing display requests a new composition, including for a static screen.
        // A new reader cannot contain buffers produced before this request; no clock-domain guess is needed.
        display?.surface = null
        discardReader()
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ source ->
                val image = try { source.acquireLatestImage() } catch (_: IllegalStateException) { null }
                if (image != null) {
                    if (ticket == null || source !== reader) {
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    if (!CaptureBus.isCurrentRequest(ticket.requestId)) {
                        frames.cancel(ticket.requestId)
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    if (frames.take(ticket)) processImage(image, ticket.requestId) else image.close()
                }
            }, handler)
        }
        display?.surface = reader?.surface
    }

    private fun requestFrame(requestId: Long) {
        if (!CaptureBus.isCurrentRequest(requestId) || frames.pendingRequestId == requestId) return
        val ticket = frames.begin(requestId)
        CaptureBus.status(CaptureStatus(ready = true, capturing = true, requestId = requestId))
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 5000)
        try {
            createReader(reader?.width ?: resources.displayMetrics.widthPixels,
                reader?.height ?: resources.displayMetrics.heightPixels, ticket)
        } catch (error: Exception) { fail(error.message ?: "未获取到画面，请重新授权", requestId, ready = false) }
    }

    private fun processImage(image: Image, requestId: Long) {
        handler.removeCallbacks(timeout)
        if (!CaptureBus.isCurrentRequest(requestId)) { image.close(); return }
        val save = saveImage
        try {
            val bitmap = image.use { CaptureImages.bitmap(it) }
            scope.launch {
                try { CaptureBus.image(requestId, CaptureImages.encode(applicationContext, bitmap, save)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { handler.post { fail(error.message ?: "无法处理截图", requestId) } }
            }
            CaptureBus.status(CaptureStatus(ready = true, requestId = requestId))
        } catch (error: Exception) { fail(error.message ?: "无法读取截图", requestId) }
    }

    private fun fail(message: String, requestId: Long = frames.pendingRequestId ?: 0, ready: Boolean = projection != null) {
        if (requestId > 0 && !CaptureBus.isCurrentRequest(requestId)) return
        frames.cancel(requestId)
        handler.removeCallbacks(timeout)
        CaptureBus.status(CaptureStatus(ready = ready, error = message, requestId = requestId))
    }

    private fun discardReader() {
        val old = reader ?: return
        reader = null
        old.setOnImageAvailableListener(null, null)
        // acquireLatestImage discards older queued buffers; close also releases any in-flight buffers.
        repeat(old.maxImages) {
            try { old.acquireLatestImage()?.close() } catch (_: IllegalStateException) { }
        }
        old.close()
    }

    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "屏幕识题", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("${applicationInfo.loadLabel(packageManager)} 截图已就绪")
            .setContentText("点击悬浮球截图")
            .setOngoing(true).build()
        ServiceCompat.startForeground(this, 1001, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
    }

    private fun releaseProjection() {
        handler.removeCallbacksAndMessages(null)
        frames.cancel()
        display?.release(); display = null
        discardReader()
        val previous = projection
        projection = null
        previous?.unregisterCallback(callback)
        previous?.stop()
    }

    override fun onDestroy() {
        releaseProjection()
        scope.cancel()
        CaptureBus.status(CaptureStatus())
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_CAPTURE = "com.nodoubt.CAPTURE"
        const val RESULT_CODE = "resultCode"
        const val RESULT_DATA = "resultData"
        const val SAVE_IMAGE = "saveImage"
        const val AUTO_CAPTURE = "captureAfterAuthorization"
        const val REQUEST_ID = "captureRequestId"
        private const val CHANNEL = "nodoubt_capture"
    }
}

package com.nodoubt.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nodoubt.core.model.Appearance
import com.nodoubt.core.model.ThemeFamily
import com.nodoubt.core.model.Usage
import com.nodoubt.feature.solver.RunStatus
import com.nodoubt.platform.capture.CaptureBus
import com.nodoubt.platform.capture.CaptureService
import com.nodoubt.platform.overlay.BubbleWindow
import com.nodoubt.platform.overlay.OverlayWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class AssistantService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var bubble: BubbleWindow
    private var answerWindow: OverlayWindow? = null
    private var capturing = false
    private var answerVisible = false
    private var captureJob: Job? = null
    private var captureRequestId = 0L
    private var awaitingAuthorization = false

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        foreground()
        bubble = BubbleWindow(this, ::capture)
        mutableRunning.value = true
        val initialSession = appGraph.solver.state.value.session
        scope.launch { appGraph.repository.state.collect { updateBubble() } }
        scope.launch {
            appGraph.solver.state.distinctUntilChangedBy { it.session }.collect { state ->
                if (state.session > initialSession && state.recognition != RunStatus.IDLE) {
                    finishCapture(); showAnswer()
                }
            }
        }
        scope.launch { CaptureBus.status.collect {
            if (it.error != null && (it.requestId == 0L || it.requestId == captureRequestId)) {
                finishCapture()
                if (!answerVisible) bubble.reveal()
            } else if (awaitingAuthorization && it.ready && it.requestId == captureRequestId) {
                // Complete only after CaptureService has accepted the permission result.
                finishCapture()
                if (!answerVisible) bubble.reveal()
            }
        } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::bubble.isInitialized) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_CAPTURE -> capture()
            ACTION_AUTHORIZE -> requestAuthorization()
            ACTION_SHOW -> showAnswer()
            ACTION_STOP -> stopSelf()
            ACTION_REVEAL -> {
                val requestId = intent.getLongExtra(CaptureService.REQUEST_ID, 0)
                if (requestId == captureRequestId || (requestId == 0L && !capturing)) {
                    finishCapture()
                    if (!answerVisible) bubble.reveal()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun capture() {
        if (capturing) return
        val candidates = appGraph.repository.state.value.bindings[Usage.OCR]?.entries.orEmpty()
        if (candidates.isEmpty()) {
            Toast.makeText(this, "请先设置截图识题模型", Toast.LENGTH_LONG).show()
            return
        }
        appGraph.solver.prepareCapture()
        if (!CaptureBus.status.value.ready) {
            requestAuthorization()
            return
        }
        captureJob?.cancel()
        captureRequestId = CaptureBus.beginRequest()
        val requestId = captureRequestId
        capturing = true
        answerWindow?.hide()
        answerVisible = false
        bubble.hide()
        captureJob = scope.launch {
            delay(300)
            startService(Intent(this@AssistantService, CaptureService::class.java)
                .setAction(CaptureService.ACTION_CAPTURE)
                .putExtra(CaptureService.REQUEST_ID, requestId)
                .putExtra(CaptureService.SAVE_IMAGE, !appGraph.repository.state.value.preferences.autoDeleteScreenshot))
            delay(6500)
            captureTimedOut(requestId)
        }
    }

    private fun requestAuthorization() {
        if (capturing || CaptureBus.status.value.ready) return
        captureRequestId = CaptureBus.beginRequest()
        capturing = true
        awaitingAuthorization = true
        // Start while the user-activated overlay is visible, then hide it for system consent.
        try {
            startActivity(CapturePermissionActivity.createIntent(this, captureRequestId))
            answerWindow?.hide()
            answerVisible = false
            bubble.hide()
        } catch (_: RuntimeException) {
            finishCapture()
            if (!answerVisible) bubble.reveal()
            Toast.makeText(this, "无法打开截图授权", Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureTimedOut(requestId: Long) {
        if (captureRequestId != requestId) return
        finishCapture()
        if (!answerVisible) bubble.reveal()
    }

    private fun finishCapture() {
        captureJob?.cancel()
        captureJob = null
        CaptureBus.finishRequest(captureRequestId)
        captureRequestId = 0
        capturing = false
        awaitingAuthorization = false
    }

    private fun showAnswer() {
        if (!Settings.canDrawOverlays(this)) return
        val host = answerWindow ?: OverlayWindow(this).also { answerWindow = it }
        answerVisible = true
        bubble.hide()
        host.show(onDismiss = ::dismissAnswer) { AnswerOverlay(appGraph, onCapture = ::capture,
            onConfigure = {
                dismissAnswer()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.OPEN_MODELS, true))
            }, onResize = host::resizeBy, onInputActiveChange = host::setInputEnabled, onDismiss = ::dismissAnswer) }
    }

    private fun dismissAnswer() {
        finishCapture()
        CaptureBus.invalidateRequests()
        appGraph.solver.stopAll()
        answerWindow?.hide()
        answerVisible = false
        bubble.reveal()
    }

    private fun updateBubble() {
        if (!::bubble.isInitialized) return
        val prefs = appGraph.repository.state.value.preferences
        val dark = prefs.appearance == Appearance.DARK || (prefs.appearance == Appearance.SYSTEM &&
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        val background = when (prefs.theme) {
            ThemeFamily.PICOVE -> if (dark) "#1E1E23" else "#FFFFFF"
            ThemeFamily.VERCEL -> if (dark) "#0A0A0A" else "#FFFFFF"
            ThemeFamily.APPLE -> if (dark) "#2C2C2E" else "#FFFFFF"
        }
        val accent = when (prefs.theme) {
            ThemeFamily.PICOVE -> if (dark) "#DF6B35" else "#D45D2D"
            ThemeFamily.VERCEL -> if (dark) "#EDEDED" else "#171717"
            ThemeFamily.APPLE -> "#0068D9"
        }
        bubble.show(prefs.bubbleSizeDp, Color.parseColor(background), Color.parseColor(accent),
            Color.parseColor(if (dark) "#3A3A40" else "#E0E0E4"))
        if (capturing || answerVisible) bubble.hide()
    }

    private fun foreground() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "悬浮答题助手", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, AssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_help).setContentTitle("${getString(R.string.app_name)} 悬浮助手")
            .setContentText("点击悬浮球截图解题。")
            .setContentIntent(open).setOngoing(true).addAction(0, "关闭悬浮球", stop).build()
        ServiceCompat.startForeground(this, 1002, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBubble()
        answerWindow?.updateBounds()
    }
    override fun onDestroy() {
        mutableRunning.value = false
        finishCapture()
        CaptureBus.invalidateRequests()
        scope.cancel()
        appGraph.solver.stopAll()
        if (::bubble.isInitialized) bubble.close()
        answerWindow?.close()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_CAPTURE = "com.nodoubt.ASSISTANT_CAPTURE"
        const val ACTION_AUTHORIZE = "com.nodoubt.ASSISTANT_AUTHORIZE"
        const val ACTION_SHOW = "com.nodoubt.ASSISTANT_SHOW"
        const val ACTION_STOP = "com.nodoubt.ASSISTANT_STOP"
        const val ACTION_REVEAL = "com.nodoubt.ASSISTANT_REVEAL"
        private const val CHANNEL = "nodoubt_assistant"
        private val mutableRunning = MutableStateFlow(false)
        val running = mutableRunning.asStateFlow()
    }
}

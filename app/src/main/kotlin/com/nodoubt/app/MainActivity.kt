package com.nodoubt.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import com.nodoubt.platform.capture.CaptureService
import com.nodoubt.platform.capture.CaptureBus

class MainActivity : ComponentActivity() {
    private var settingsRequest by mutableIntStateOf(0)
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val overlay = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) startAssistant()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(OPEN_MODELS, false)) settingsRequest++
        enableEdgeToEdge()
        setContent {
            NoDoubtApp(appGraph, settingsRequest = settingsRequest, onAssistant = { enabled ->
                    if (enabled) enableAssistant()
                    else stopService(Intent(this, AssistantService::class.java))
                }, onStopCapture = { stopService(Intent(this, CaptureService::class.java)) })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(OPEN_MODELS, false)) settingsRequest++
    }

    private fun enableAssistant() {
        if (!Settings.canDrawOverlays(this)) {
            overlay.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startAssistant()
    }

    private fun startAssistant() {
        if (CaptureBus.status.value.ready && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        val intent = Intent(this, AssistantService::class.java)
        if (!CaptureBus.status.value.ready) intent.action = AssistantService.ACTION_AUTHORIZE
        ContextCompat.startForegroundService(this, intent)
    }

    companion object { const val OPEN_MODELS = "openModelServices" }
}

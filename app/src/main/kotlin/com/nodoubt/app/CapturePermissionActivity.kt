package com.nodoubt.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.nodoubt.platform.capture.CaptureService
import com.nodoubt.platform.capture.CaptureBus

class CapturePermissionActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val requestId = intent.getLongExtra(CaptureService.REQUEST_ID, 0)
        if (!CaptureBus.isCurrentRequest(requestId)) { finishAndRemoveTask(); return@registerForActivityResult }
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java)
                    .putExtra(CaptureService.RESULT_CODE, result.resultCode)
                    .putExtra(CaptureService.RESULT_DATA, result.data)
                    .putExtra(CaptureService.REQUEST_ID, requestId)
                    .putExtra(CaptureService.AUTO_CAPTURE, false)
                    .putExtra(CaptureService.SAVE_IMAGE, !appGraph.repository.state.value.preferences.autoDeleteScreenshot))
            } catch (_: RuntimeException) {
                revealBubble(requestId, "无法打开截图授权")
            }
        } else {
            revealBubble(requestId, "未授权截图")
        }
        // This activity has its own task; removing it returns directly to the previous app.
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!CaptureBus.isCurrentRequest(intent.getLongExtra(CaptureService.REQUEST_ID, 0))) {
            finishAndRemoveTask()
            return
        }
        if (savedInstanceState == null) {
            val projection = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            permission.launch(projection.createScreenCaptureIntent())
        }
    }

    private fun revealBubble(requestId: Long, message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (AssistantService.running.value) startService(Intent(this, AssistantService::class.java)
            .setAction(AssistantService.ACTION_REVEAL).putExtra(CaptureService.REQUEST_ID, requestId))
    }

    companion object {
        fun createIntent(context: Context, requestId: Long): Intent =
            Intent(context, CapturePermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(CaptureService.REQUEST_ID, requestId)
                .putExtra(CaptureService.AUTO_CAPTURE, false)
    }
}

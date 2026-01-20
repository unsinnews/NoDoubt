package com.yy.perfectfloatwindow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.yy.floatserver.FloatClient
import com.yy.floatserver.FloatHelper
import com.yy.floatserver.IFloatClickListener
import com.yy.floatserver.IFloatPermissionCallback
import com.yy.perfectfloatwindow.screenshot.ScreenshotService
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private var floatHelper: FloatHelper? = null
    private var isFloatShowing = false
    private lateinit var floatView: View
    private var currentSize = 32 // default size in dp

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startScreenshotService(result.resultCode, result.data!!)
            Toast.makeText(this, "Screenshot ready! Tap float window to capture.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screenshot permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        floatView = View.inflate(this, R.layout.float_view, null)

        floatHelper = FloatClient.Builder()
            .with(this)
            .addView(floatView)
            .enableDefaultPermissionDialog(true)
            .setClickListener(object : IFloatClickListener {
                override fun onFloatClick() {
                    takeScreenshot()
                }
            })
            .addPermissionCallback(object : IFloatPermissionCallback {
                override fun onPermissionResult(granted: Boolean) {
                    if (granted) {
                        requestScreenshotPermission()
                    } else {
                        Toast.makeText(this@MainActivity, "Float window permission denied", Toast.LENGTH_SHORT).show()
                        floatHelper?.requestPermission()
                    }
                }
            })
            .build()

        setupSwitch()
        setupButtons()
        setupSizeAdjustment()
    }

    private fun requestScreenshotPermission() {
        if (!ScreenshotService.isServiceRunning) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    private fun startScreenshotService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
            putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenshotService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun takeScreenshot() {
        if (ScreenshotService.isServiceRunning) {
            ScreenshotService.requestScreenshot()
        } else {
            Toast.makeText(this, "Please enable float window first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwitch() {
        val switchFloat = findViewById<SwitchCompat>(R.id.switchFloat)
        val tvStatusText = findViewById<TextView>(R.id.tvStatusText)

        switchFloat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                floatHelper?.show()
                tvStatusText.text = "Tap float window to screenshot"
            } else {
                floatHelper?.dismiss()
                tvStatusText.text = "Tap toggle to enable"
            }
            isFloatShowing = isChecked
        }
    }

    private fun setupButtons() {
        val switchFloat = findViewById<SwitchCompat>(R.id.switchFloat)
        val tvStatusText = findViewById<TextView>(R.id.tvStatusText)

        btnShow.setOnClickListener {
            floatHelper?.show()
            switchFloat.isChecked = true
            tvStatusText.text = "Tap float window to screenshot"
            isFloatShowing = true
        }

        btnClose.setOnClickListener {
            floatHelper?.dismiss()
            switchFloat.isChecked = false
            tvStatusText.text = "Tap toggle to enable"
            isFloatShowing = false
        }

        btnJump.setOnClickListener {
            startActivity(Intent(this, SecondActivity::class.java))
        }
    }

    private fun setupSizeAdjustment() {
        val seekBar = findViewById<SeekBar>(R.id.seekBarSize)
        val tvSizeValue = findViewById<TextView>(R.id.tvSizeValue)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentSize = progress
                tvSizeValue.text = "${progress}dp"
                updateFloatSize(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateFloatSize(sizeDp: Int) {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val iconSizePx = (sizeDp * 0.5625 * density).toInt() // icon is ~56.25% of container

        val container = floatView.findViewById<FrameLayout>(R.id.llContainer)
        val icon = floatView.findViewById<ImageView>(R.id.ivIcon)

        container?.layoutParams?.let {
            it.width = sizePx
            it.height = sizePx
            container.layoutParams = it
        }

        icon?.layoutParams?.let {
            it.width = iconSizePx
            it.height = iconSizePx
            icon.layoutParams = it
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatHelper?.release()
    }
}

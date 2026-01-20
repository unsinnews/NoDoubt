package com.yy.perfectfloatwindow

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.yy.floatserver.FloatClient
import com.yy.floatserver.FloatHelper
import com.yy.floatserver.IFloatPermissionCallback
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var floatHelper: FloatHelper? = null
    private lateinit var tvContent: TextView
    private lateinit var tvStatus: TextView
    private lateinit var floatContainer: LinearLayout
    private var countDownTimer: CountDownTimer? = null
    private var isFloatShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view = View.inflate(this, R.layout.float_view, null)

        tvContent = view.findViewById(R.id.tvContent)
        tvStatus = view.findViewById(R.id.tvStatus)
        floatContainer = view.findViewById(R.id.llContainer)

        floatHelper = FloatClient.Builder()
            .with(this)
            .addView(view)
            .enableDefaultPermissionDialog(true)
            .setClickTarget(MainActivity::class.java)
            .addPermissionCallback(object : IFloatPermissionCallback {
                override fun onPermissionResult(granted: Boolean) {
                    Toast.makeText(this@MainActivity, "Permission: $granted", Toast.LENGTH_SHORT).show()
                    if (!granted) {
                        floatHelper?.requestPermission()
                    }
                }
            })
            .build()

        setupSwitch()
        setupButtons()
        initCountDown()
    }

    private fun setupSwitch() {
        val switchFloat = findViewById<SwitchCompat>(R.id.switchFloat)
        val tvStatusText = findViewById<TextView>(R.id.tvStatusText)

        switchFloat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                floatHelper?.show()
                updateFloatState(true)
                tvStatusText.text = "Float window is active"
            } else {
                floatHelper?.dismiss()
                updateFloatState(false)
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
            updateFloatState(true)
            tvStatusText.text = "Float window is active"
            isFloatShowing = true
        }

        btnClose.setOnClickListener {
            floatHelper?.dismiss()
            switchFloat.isChecked = false
            updateFloatState(false)
            tvStatusText.text = "Tap toggle to enable"
            isFloatShowing = false
        }

        btnJump.setOnClickListener {
            startActivity(Intent(this, SecondActivity::class.java))
        }
    }

    private fun updateFloatState(isOn: Boolean) {
        if (isOn) {
            floatContainer.setBackgroundResource(R.drawable.pill_background_on)
            tvStatus.text = "ON"
            tvContent.setTextColor(0xFFE8F5E9.toInt())
        } else {
            floatContainer.setBackgroundResource(R.drawable.pill_background_off)
            tvStatus.text = "OFF"
            tvContent.setTextColor(0xFFE0E0E0.toInt())
        }
    }

    private fun initCountDown() {
        countDownTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val timeStr = getLeftTime(millisUntilFinished)
                tvContent.text = timeStr
                tvTimer.text = timeStr
            }

            override fun onFinish() {}
        }
        countDownTimer?.start()
    }

    private fun getLeftTime(time: Long): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone("GMT+00:00")
        return formatter.format(time)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        floatHelper?.release()
    }
}

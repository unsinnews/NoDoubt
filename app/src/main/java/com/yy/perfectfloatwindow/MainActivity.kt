package com.yy.perfectfloatwindow

import android.content.Intent
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {

    private var floatHelper: FloatHelper? = null
    private lateinit var floatContainer: LinearLayout
    private lateinit var tvStatus: TextView
    private var isFloatShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view = View.inflate(this, R.layout.float_view, null)

        floatContainer = view.findViewById(R.id.llContainer)
        tvStatus = view.findViewById(R.id.tvStatus)

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
    }

    private fun setupSwitch() {
        val switchFloat = findViewById<SwitchCompat>(R.id.switchFloat)
        val tvStatusText = findViewById<TextView>(R.id.tvStatusText)

        switchFloat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                floatHelper?.show()
                tvStatusText.text = "Float window is active"
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
            tvStatusText.text = "Float window is active"
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

    override fun onDestroy() {
        super.onDestroy()
        floatHelper?.release()
    }
}

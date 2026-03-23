package com.nodoubt.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nodoubt.app.R
import com.nodoubt.app.data.AISettings
import com.nodoubt.app.data.ThemeManager

class ChannelSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_selection)

        initViews()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        bindDefaultChannel()
        applyTheme()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.channelDefaultCard).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun bindDefaultChannel() {
        val channel = AISettings.getDefaultChannel(this)
        findViewById<TextView>(R.id.tvChannelCardTitle).text = channel.name
        findViewById<TextView>(R.id.tvChannelCardType).text = channel.type.displayName
        findViewById<TextView>(R.id.tvChannelCardEndpoint).text = channel.baseUrl
        findViewById<TextView>(R.id.tvChannelCardStatus).text =
            if (channel.apiKey.isBlank()) "未配置" else "已配置"
    }

    private fun applyTheme() {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val headerLayout = findViewById<FrameLayout>(R.id.headerLayout)
        val tvHeaderTitle = findViewById<TextView>(R.id.tvHeaderTitle)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val card = findViewById<LinearLayout>(R.id.channelDefaultCard)
        val tvEyebrow = findViewById<TextView>(R.id.tvChannelEyebrow)
        val tvTitle = findViewById<TextView>(R.id.tvChannelCardTitle)
        val tvType = findViewById<TextView>(R.id.tvChannelCardType)
        val tvEndpoint = findViewById<TextView>(R.id.tvChannelCardEndpoint)
        val tvStatus = findViewById<TextView>(R.id.tvChannelCardStatus)
        val tvHint = findViewById<TextView>(R.id.tvChannelHint)
        val ivChevron = findViewById<ImageButton>(R.id.ivChannelChevron)

        if (isLightGreenGray) {
            val surfaceColor = 0xFFF7F7F8.toInt()
            val textPrimary = 0xFF202123.toInt()
            val textSecondary = 0xFF6E6E80.toInt()
            val accent = 0xFF10A37F.toInt()

            window.statusBarColor = surfaceColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(surfaceColor)
            headerLayout.setBackgroundColor(surfaceColor)
            card.setBackgroundResource(R.drawable.bg_card_settings)
            tvHeaderTitle.setTextColor(textPrimary)
            btnBack.setColorFilter(textPrimary)
            tvEyebrow.setTextColor(accent)
            tvTitle.setTextColor(textPrimary)
            tvType.setTextColor(textSecondary)
            tvEndpoint.setTextColor(textPrimary)
            tvHint.setTextColor(textSecondary)
            ivChevron.setColorFilter(textSecondary)

            val isConfigured = AISettings.getApiKey(this).isNotBlank()
            tvStatus.setTextColor(if (isConfigured) accent else 0xFFE25656.toInt())
        } else {
            val backgroundColor = 0xFFFAF9F5.toInt()
            val textPrimary = 0xFF141413.toInt()
            val textSecondary = 0xFF666666.toInt()
            val accent = 0xFFDA7A5A.toInt()

            window.statusBarColor = backgroundColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(backgroundColor)
            headerLayout.setBackgroundColor(backgroundColor)
            card.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)
            tvHeaderTitle.setTextColor(textPrimary)
            btnBack.setColorFilter(textPrimary)
            tvEyebrow.setTextColor(accent)
            tvTitle.setTextColor(textPrimary)
            tvType.setTextColor(textSecondary)
            tvEndpoint.setTextColor(textPrimary)
            tvHint.setTextColor(textSecondary)
            ivChevron.setColorFilter(textSecondary)

            val isConfigured = AISettings.getApiKey(this).isNotBlank()
            tvStatus.setTextColor(if (isConfigured) accent else 0xFFE25656.toInt())
        }
    }
}

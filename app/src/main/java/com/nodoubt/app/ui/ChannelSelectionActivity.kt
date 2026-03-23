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
import com.nodoubt.app.data.AIChannelType
import com.nodoubt.app.data.AISettings
import com.nodoubt.app.data.ThemeManager
import com.nodoubt.app.network.AIProviderRegistry

class ChannelSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_selection)

        initViews()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        bindProviderCard()
        applyTheme()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.channelDefaultCard).setOnClickListener {
            startActivity(
                Intent(this, ProviderChannelsActivity::class.java).putExtra(
                    ProviderChannelsActivity.EXTRA_PROVIDER_TYPE,
                    AIChannelType.OPENAI_CHAT_COMPLETIONS.storageValue
                )
            )
        }
    }

    private fun bindProviderCard() {
        val provider = AIProviderRegistry.getVisibleProviders().first()
        val channels = AISettings.getChannels(provider.type, this)
        val configuredCount = channels.count { it.isConfigured() }
        findViewById<TextView>(R.id.tvHeaderTitle).text = "渠道"
        findViewById<TextView>(R.id.tvChannelCardTitle).text = provider.displayName
        findViewById<TextView>(R.id.tvChannelCardMeta).text =
            if (channels.isEmpty()) {
                provider.shortDescription
            } else {
                "${channels.size} 个渠道，已配置 $configuredCount 个"
            }
    }

    private fun applyTheme() {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val headerLayout = findViewById<FrameLayout>(R.id.headerLayout)
        val tvHeaderTitle = findViewById<TextView>(R.id.tvHeaderTitle)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val card = findViewById<LinearLayout>(R.id.channelDefaultCard)
        val tvTitle = findViewById<TextView>(R.id.tvChannelCardTitle)
        val tvMeta = findViewById<TextView>(R.id.tvChannelCardMeta)
        val ivChevron = findViewById<ImageButton>(R.id.ivChannelChevron)

        if (isLightGreenGray) {
            val surfaceColor = 0xFFF7F7F8.toInt()
            val textPrimary = 0xFF202123.toInt()
            val textSecondary = 0xFF6E6E80.toInt()

            window.statusBarColor = surfaceColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(surfaceColor)
            headerLayout.setBackgroundColor(surfaceColor)
            card.setBackgroundResource(R.drawable.bg_card_settings)
            tvHeaderTitle.setTextColor(textPrimary)
            btnBack.setColorFilter(textPrimary)
            tvTitle.setTextColor(textPrimary)
            tvMeta.setTextColor(textSecondary)
            ivChevron.setColorFilter(textSecondary)
        } else {
            val backgroundColor = 0xFFFAF9F5.toInt()
            val textPrimary = 0xFF141413.toInt()
            val textSecondary = 0xFF666666.toInt()

            window.statusBarColor = backgroundColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(backgroundColor)
            headerLayout.setBackgroundColor(backgroundColor)
            card.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)
            tvHeaderTitle.setTextColor(textPrimary)
            btnBack.setColorFilter(textPrimary)
            tvTitle.setTextColor(textPrimary)
            tvMeta.setTextColor(textSecondary)
            ivChevron.setColorFilter(textSecondary)
        }
    }
}

package com.nodoubt.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nodoubt.app.R
import com.nodoubt.app.data.AIChannel
import com.nodoubt.app.data.AIChannelType
import com.nodoubt.app.data.AISettings
import com.nodoubt.app.data.ThemeManager
import com.nodoubt.app.network.AIChannelProvider
import com.nodoubt.app.network.AIProviderRegistry

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROVIDER_TYPE = "provider_type"
    }

    private lateinit var providerType: AIChannelType
    private lateinit var provider: AIChannelProvider
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvSummary: TextView
    private lateinit var channelsContainer: LinearLayout
    private lateinit var btnAddChannel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        providerType = AIChannelType.fromStorageValue(intent.getStringExtra(EXTRA_PROVIDER_TYPE))
        provider = AIProviderRegistry.getProvider(providerType)

        initViews()
        bindChannels()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        bindChannels()
        applyTheme()
    }

    private fun initViews() {
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvSummary = findViewById(R.id.tvSummary)
        channelsContainer = findViewById(R.id.channelsContainer)
        btnAddChannel = findViewById(R.id.btnAddChannel)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnAddChannel.setOnClickListener {
            val channel = AISettings.createChannel(this, providerType)
            openChannel(channel)
        }
    }

    private fun bindChannels() {
        val channels = AISettings.getChannels(providerType, this)
        val configuredCount = channels.count { it.isConfigured() }

        tvHeaderTitle.text = "${provider.displayName} 渠道"
        tvSummary.text = if (channels.isEmpty()) {
            provider.shortDescription
        } else {
            "${channels.size} 个渠道，已配置 $configuredCount 个。"
        }

        channelsContainer.removeAllViews()
        channels.forEachIndexed { index, channel ->
            val itemView = layoutInflater.inflate(R.layout.item_channel_card, channelsContainer, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvChannelName)
            val tvMeta = itemView.findViewById<TextView>(R.id.tvChannelMeta)
            val tvBadge = itemView.findViewById<TextView>(R.id.tvChannelBadge)

            tvName.text = channel.name
            tvMeta.text = if (channel.isConfigured()) {
                "已配置 · ${channel.baseUrl}"
            } else {
                "未配置"
            }
            tvBadge.text = if (channel.isConfigured()) "已就绪" else "待配置"

            itemView.setOnClickListener { openChannel(channel) }
            channelsContainer.addView(itemView)

            if (index < channels.lastIndex) {
                val spacer = View(this)
                spacer.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(12f).toInt()
                )
                channelsContainer.addView(spacer)
            }
        }
    }

    private fun openChannel(channel: AIChannel) {
        AISettings.setSelectedChannelId(this, channel.id)
        startActivity(
            Intent(this, ChannelEditActivity::class.java).putExtra(
                ChannelEditActivity.EXTRA_CHANNEL_ID,
                channel.id
            )
        )
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun applyTheme() {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val headerLayout = findViewById<FrameLayout>(R.id.headerLayout)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val card = findViewById<LinearLayout>(R.id.cardChannels)

        if (isLightGreenGray) {
            val surfaceColor = 0xFFF7F7F8.toInt()
            val textPrimary = 0xFF202123.toInt()
            val textSecondary = 0xFF6E6E80.toInt()

            window.statusBarColor = surfaceColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(surfaceColor)
            headerLayout.setBackgroundColor(surfaceColor)
            card.setBackgroundResource(R.drawable.bg_card_settings)
            btnBack.setColorFilter(textPrimary)
            tvHeaderTitle.setTextColor(textPrimary)
            tvSummary.setTextColor(textSecondary)
            btnAddChannel.setBackgroundResource(R.drawable.bg_button_outline)
            btnAddChannel.setTextColor(0xFF10A37F.toInt())
        } else {
            val backgroundColor = 0xFFFAF9F5.toInt()
            val textPrimary = 0xFF141413.toInt()
            val textSecondary = 0xFF666666.toInt()

            window.statusBarColor = backgroundColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(backgroundColor)
            headerLayout.setBackgroundColor(backgroundColor)
            card.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)
            btnBack.setColorFilter(textPrimary)
            tvHeaderTitle.setTextColor(textPrimary)
            tvSummary.setTextColor(textSecondary)
            btnAddChannel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnAddChannel.setTextColor(0xFFDA7A5A.toInt())
        }

        for (index in 0 until channelsContainer.childCount) {
            val child = channelsContainer.getChildAt(index)
            if (child !is LinearLayout) continue
            val tvName = child.findViewById<TextView?>(R.id.tvChannelName) ?: continue
            val tvMeta = child.findViewById<TextView>(R.id.tvChannelMeta)
            val tvBadge = child.findViewById<TextView>(R.id.tvChannelBadge)
            val chevron = child.findViewById<ImageButton>(R.id.ivChannelChevron)

            if (isLightGreenGray) {
                child.setBackgroundResource(R.drawable.bg_model_option_unselected)
                tvName.setTextColor(0xFF202123.toInt())
                tvMeta.setTextColor(0xFF6E6E80.toInt())
                tvBadge.setBackgroundResource(R.drawable.bg_button_outline)
                tvBadge.setTextColor(0xFF10A37F.toInt())
                chevron.setColorFilter(0xFF6E6E80.toInt())
            } else {
                child.setBackgroundResource(R.drawable.bg_model_option_unselected_light_brown_black)
                tvName.setTextColor(0xFF141413.toInt())
                tvMeta.setTextColor(0xFF666666.toInt())
                tvBadge.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
                tvBadge.setTextColor(0xFFDA7A5A.toInt())
                chevron.setColorFilter(0xFF666666.toInt())
            }
        }
    }
}

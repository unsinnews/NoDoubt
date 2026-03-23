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
import com.nodoubt.app.data.AIUsage
import com.nodoubt.app.data.ThemeManager
import com.nodoubt.app.network.AIProviderRegistry

class ProviderChannelsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROVIDER_TYPE = "provider_type"
    }

    private lateinit var providerType: AIChannelType
    private lateinit var providerMenuCard: View
    private lateinit var sharedModelsCard: View
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvProviderMenuTitle: TextView
    private lateinit var tvProviderMenuMeta: TextView
    private lateinit var tvSharedModelsTitle: TextView
    private lateinit var tvSharedModelsMeta: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_channels)

        providerType = AIChannelType.fromStorageValue(intent.getStringExtra(EXTRA_PROVIDER_TYPE))
        initViews()
        bindContent()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        bindContent()
        applyTheme()
    }

    private fun initViews() {
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvProviderMenuTitle = findViewById(R.id.tvProviderMenuTitle)
        tvProviderMenuMeta = findViewById(R.id.tvProviderMenuMeta)
        tvSharedModelsTitle = findViewById(R.id.tvSharedModelsTitle)
        tvSharedModelsMeta = findViewById(R.id.tvSharedModelsMeta)
        providerMenuCard = findViewById(R.id.providerMenuCard)
        sharedModelsCard = findViewById(R.id.sharedModelsCard)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        providerMenuCard.setOnClickListener {
            startActivity(
                Intent(this, SettingsActivity::class.java).putExtra(
                    SettingsActivity.EXTRA_PROVIDER_TYPE,
                    providerType.storageValue
                )
            )
        }
        sharedModelsCard.setOnClickListener {
            startActivity(Intent(this, UsageModelPoolsActivity::class.java))
        }
    }

    private fun bindContent() {
        val provider = AIProviderRegistry.getProvider(providerType)
        val channels = AISettings.getChannels(providerType, this)
        val configuredCount = channels.count { it.isConfigured() }

        tvHeaderTitle.text = provider.displayName
        tvProviderMenuTitle.text = "${provider.displayName} 渠道"
        tvProviderMenuMeta.text =
            if (channels.isEmpty()) {
                "管理 ${provider.displayName} 的渠道列表与连接信息。"
            } else {
                "${channels.size} 个渠道，已配置 $configuredCount 个。"
            }

        val readyUsageCount = listOf(
            AISettings.hasReadyUsage(this, AIUsage.OCR),
            AISettings.hasReadyUsage(this, AIUsage.FAST),
            AISettings.hasReadyUsage(this, AIUsage.DEEP)
        ).count { it }
        tvSharedModelsTitle.text = "共享模型池"
        tvSharedModelsMeta.text = "OCR、极速、深度共 3 组，已就绪 $readyUsageCount 组。"
    }

    private fun applyTheme() {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val headerLayout = findViewById<FrameLayout>(R.id.headerLayout)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val chevronProvider = findViewById<ImageButton>(R.id.ivProviderChevron)
        val chevronModels = findViewById<ImageButton>(R.id.ivSharedModelsChevron)

        val cards = listOf(
            findViewById<LinearLayout>(R.id.providerMenuCard),
            findViewById<LinearLayout>(R.id.sharedModelsCard)
        )
        val titles = listOf(tvHeaderTitle, tvProviderMenuTitle, tvSharedModelsTitle)
        val metas = listOf(tvProviderMenuMeta, tvSharedModelsMeta)

        if (isLightGreenGray) {
            val surfaceColor = 0xFFF7F7F8.toInt()
            val textPrimary = 0xFF202123.toInt()
            val textSecondary = 0xFF6E6E80.toInt()

            window.statusBarColor = surfaceColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(surfaceColor)
            headerLayout.setBackgroundColor(surfaceColor)
            cards.forEach { it.setBackgroundResource(R.drawable.bg_card_settings) }
            titles.forEach { it.setTextColor(textPrimary) }
            metas.forEach { it.setTextColor(textSecondary) }
            btnBack.setColorFilter(textPrimary)
            chevronProvider.setColorFilter(textSecondary)
            chevronModels.setColorFilter(textSecondary)
        } else {
            val backgroundColor = 0xFFFAF9F5.toInt()
            val textPrimary = 0xFF141413.toInt()
            val textSecondary = 0xFF666666.toInt()

            window.statusBarColor = backgroundColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(backgroundColor)
            headerLayout.setBackgroundColor(backgroundColor)
            cards.forEach { it.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black) }
            titles.forEach { it.setTextColor(textPrimary) }
            metas.forEach { it.setTextColor(textSecondary) }
            btnBack.setColorFilter(textPrimary)
            chevronProvider.setColorFilter(textSecondary)
            chevronModels.setColorFilter(textSecondary)
        }
    }
}

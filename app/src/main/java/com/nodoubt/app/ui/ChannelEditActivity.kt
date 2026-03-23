package com.nodoubt.app.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nodoubt.app.R
import com.nodoubt.app.data.AIChannel
import com.nodoubt.app.data.AIChannelType
import com.nodoubt.app.data.AISettings
import com.nodoubt.app.data.ThemeManager
import com.nodoubt.app.network.AIProviderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }

    private lateinit var providerType: AIChannelType
    private lateinit var channel: AIChannel
    private lateinit var etChannelName: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnDelete: TextView
    private lateinit var btnTest: TextView
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvChannelHint: TextView
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_edit)

        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
        channel = AISettings.getChannelById(this, channelId) ?: run {
            finish()
            return
        }
        providerType = channel.type

        initViews()
        bindChannel()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun initViews() {
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvChannelHint = findViewById(R.id.tvChannelHint)
        etChannelName = findViewById(R.id.etChannelName)
        etApiKey = findViewById(R.id.etApiKey)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)
        btnTest = findViewById(R.id.btnTest)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnSave.setOnClickListener { saveChannel() }
        btnDelete.setOnClickListener { deleteChannel() }
        btnTest.setOnClickListener { testConnection() }

        addCompactWatcher(etApiKey)
        addCompactWatcher(etBaseUrl)
    }

    private fun bindChannel() {
        val provider = AIProviderRegistry.getProvider(providerType)
        tvHeaderTitle.text = channel.name
        tvChannelHint.text = provider.shortDescription
        etChannelName.setText(channel.name)
        etApiKey.setText(channel.apiKey)
        etBaseUrl.setText(channel.baseUrl)
    }

    private fun currentChannelDraft(): AIChannel {
        return channel.copy(
            name = etChannelName.text?.toString().orEmpty().trim(),
            apiKey = compactInput(etApiKey.text?.toString().orEmpty()),
            baseUrl = compactInput(etBaseUrl.text?.toString().orEmpty())
        )
    }

    private fun saveChannel() {
        val provider = AIProviderRegistry.getProvider(providerType)
        val draft = provider.normalizeChannel(currentChannelDraft())
        if (draft.name.isBlank()) {
            Toast.makeText(this, "请输入渠道名称", Toast.LENGTH_SHORT).show()
            return
        }
        channel = AISettings.upsertChannel(this, draft)
        tvHeaderTitle.text = channel.name
        Toast.makeText(this, "渠道已保存", Toast.LENGTH_SHORT).show()
    }

    private fun deleteChannel() {
        if (AISettings.getChannels(this).size <= 1) {
            Toast.makeText(this, "至少保留一个渠道", Toast.LENGTH_SHORT).show()
            return
        }
        AISettings.deleteChannel(this, channel.id)
        Toast.makeText(this, "渠道已删除", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testConnection() {
        val provider = AIProviderRegistry.getProvider(providerType)
        val draft = provider.normalizeChannel(currentChannelDraft())
        if (draft.apiKey.isBlank()) {
            Toast.makeText(this, "请先填写 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        btnTest.isEnabled = false
        btnTest.text = "检测中..."
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                provider.createClient(draft.toConfig("gpt-4o-mini")).fetchModels()
            }
            btnTest.isEnabled = true
            btnTest.text = "检测连接"
            showConnectionResultDialog(result.success, result.responseBody)
        }
    }

    private fun showConnectionResultDialog(success: Boolean, responseBody: String) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        val dialog = Dialog(this, R.style.RoundedDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_connection_result)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = dialog.findViewById<LinearLayout>(R.id.dialogResultRoot)
        val ambientGlow = dialog.findViewById<View>(R.id.vAmbientGlow)
        val ringOuter = dialog.findViewById<View>(R.id.vRingOuter)
        val ringInner = dialog.findViewById<View>(R.id.vRingInner)
        val statusOrb = dialog.findViewById<FrameLayout>(R.id.statusOrb)
        val ivStatusIcon = dialog.findViewById<ImageView>(R.id.ivStatusIcon)
        val tvResultTitle = dialog.findViewById<TextView>(R.id.tvResultTitle)
        val tvResultMessage = dialog.findViewById<TextView>(R.id.tvResultMessage)
        val btnConfirm = dialog.findViewById<TextView>(R.id.btnResultConfirm)

        val accent = if (isLightGreenGray) 0xFF10A37F.toInt() else 0xFFDA7A5A.toInt()
        val textPrimary = if (isLightGreenGray) 0xFF1C2420.toInt() else 0xFF2C241F.toInt()
        val textSecondary = if (isLightGreenGray) 0xFF5E6872.toInt() else 0xFF6F625B.toInt()

        if (isLightGreenGray) {
            root.setBackgroundResource(R.drawable.bg_connection_dialog_surface)
            btnConfirm.setBackgroundResource(R.drawable.bg_connection_action_button)
        } else {
            root.setBackgroundResource(R.drawable.bg_connection_dialog_surface_light_brown_black)
            btnConfirm.setBackgroundResource(R.drawable.bg_connection_action_button_light_brown_black)
        }
        btnConfirm.setTextColor(accent)
        btnConfirm.setOnClickListener { dialog.dismiss() }
        tvResultTitle.setTextColor(if (success) accent else 0xFFE25656.toInt())
        tvResultMessage.setTextColor(textSecondary)

        if (success) {
            tvResultTitle.text = "连接成功"
            tvResultMessage.text = ""
            tvResultMessage.visibility = View.INVISIBLE
            ambientGlow.setBackgroundResource(R.drawable.bg_connection_ambient_glow_success)
            ringOuter.setBackgroundResource(R.drawable.bg_connection_ring_success)
            ringInner.setBackgroundResource(R.drawable.bg_connection_ring_success)
            statusOrb.setBackgroundResource(R.drawable.bg_connection_orb_success)
            ivStatusIcon.setImageResource(R.drawable.ic_check)
            ivStatusIcon.setColorFilter(accent)
        } else {
            tvResultTitle.text = "连接失败"
            tvResultMessage.visibility = View.VISIBLE
            tvResultMessage.text = "响应体：\n${responseBody.ifBlank { "Empty response body" }}"
            ambientGlow.setBackgroundResource(R.drawable.bg_connection_ambient_glow_failure)
            ringOuter.setBackgroundResource(R.drawable.bg_connection_ring_failure)
            ringInner.setBackgroundResource(R.drawable.bg_connection_ring_failure)
            statusOrb.setBackgroundResource(R.drawable.bg_connection_orb_failure)
            ivStatusIcon.setImageResource(R.drawable.ic_info)
            ivStatusIcon.setColorFilter(0xFFDF5D5D.toInt())
        }

        dialog.show()
        val screenWidth = resources.displayMetrics.widthPixels
        val targetWidth = minOf((screenWidth * 0.9f).toInt(), dp(348f).toInt())
        dialog.window?.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun addCompactWatcher(editText: EditText) {
        var updating = false
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val raw = s?.toString().orEmpty()
                val compact = compactInput(raw)
                if (raw != compact) {
                    updating = true
                    editText.setText(compact)
                    editText.setSelection(compact.length)
                    updating = false
                }
            }
        })
    }

    private fun compactInput(value: String): String {
        return value.replace(WHITESPACE_REGEX, "")
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun applyTheme() {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val headerLayout = findViewById<FrameLayout>(R.id.headerLayout)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val card = findViewById<LinearLayout>(R.id.cardChannel)
        val labels = listOf(
            findViewById<TextView>(R.id.tvChannelNameLabel),
            findViewById<TextView>(R.id.tvApiKeyLabel),
            findViewById<TextView>(R.id.tvBaseUrlLabel),
            tvHeaderTitle
        )

        if (isLightGreenGray) {
            val surfaceColor = 0xFFF7F7F8.toInt()
            val textPrimary = 0xFF202123.toInt()
            val textSecondary = 0xFF6E6E80.toInt()
            window.statusBarColor = surfaceColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(surfaceColor)
            headerLayout.setBackgroundColor(surfaceColor)
            card.setBackgroundResource(R.drawable.bg_card_settings)
            labels.forEach { it.setTextColor(textPrimary) }
            tvChannelHint.setTextColor(textSecondary)
            etChannelName.setBackgroundResource(R.drawable.bg_edittext_settings)
            etApiKey.setBackgroundResource(R.drawable.bg_edittext_settings)
            etBaseUrl.setBackgroundResource(R.drawable.bg_edittext_settings)
            listOf(etChannelName, etApiKey, etBaseUrl).forEach { it.setTextColor(textPrimary) }
            btnBack.setColorFilter(textPrimary)
            btnSave.setBackgroundResource(R.drawable.bg_button_outline)
            btnSave.setTextColor(0xFF10A37F.toInt())
            btnTest.setBackgroundResource(R.drawable.bg_button_outline)
            btnTest.setTextColor(0xFF10A37F.toInt())
            btnDelete.setBackgroundResource(R.drawable.bg_button_outline)
            btnDelete.setTextColor(0xFFD44949.toInt())
        } else {
            val backgroundColor = 0xFFFAF9F5.toInt()
            val textPrimary = 0xFF141413.toInt()
            val textSecondary = 0xFF666666.toInt()
            window.statusBarColor = backgroundColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(backgroundColor)
            headerLayout.setBackgroundColor(backgroundColor)
            card.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)
            labels.forEach { it.setTextColor(textPrimary) }
            tvChannelHint.setTextColor(textSecondary)
            etChannelName.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
            etApiKey.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
            etBaseUrl.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
            listOf(etChannelName, etApiKey, etBaseUrl).forEach { it.setTextColor(textPrimary) }
            btnBack.setColorFilter(textPrimary)
            btnSave.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnSave.setTextColor(0xFFDA7A5A.toInt())
            btnTest.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnTest.setTextColor(0xFFDA7A5A.toInt())
            btnDelete.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnDelete.setTextColor(0xFFD44949.toInt())
        }
    }
}

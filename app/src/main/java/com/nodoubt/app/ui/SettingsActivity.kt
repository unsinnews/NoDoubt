package com.nodoubt.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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
import com.nodoubt.app.data.AIConfig
import com.nodoubt.app.data.AISettings
import com.nodoubt.app.data.ThemeManager
import com.nodoubt.app.network.OpenAIClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var etOcrBaseUrl: EditText
    private lateinit var etOcrModelId: EditText
    private lateinit var etFastBaseUrl: EditText
    private lateinit var etDeepBaseUrl: EditText
    private lateinit var fastModelListContainer: LinearLayout
    private lateinit var deepModelListContainer: LinearLayout
    private lateinit var btnAddFastModel: TextView
    private lateinit var btnAddDeepModel: TextView
    private lateinit var tvTestResult: TextView
    private lateinit var btnTest: Button
    private lateinit var btnSave: Button

    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)

    // Track if API has been verified
    private var isApiVerified = false

    companion object {
        private const val DEFAULT_FAST_MODEL = "gpt-4o-mini"
        private const val DEFAULT_DEEP_MODEL = "gpt-4o"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSettings()
        setupButtons()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    private fun initViews() {
        etApiKey = findViewById(R.id.etApiKey)
        etOcrBaseUrl = findViewById(R.id.etOcrBaseUrl)
        etOcrModelId = findViewById(R.id.etOcrModelId)
        etFastBaseUrl = findViewById(R.id.etFastBaseUrl)
        etDeepBaseUrl = findViewById(R.id.etDeepBaseUrl)
        fastModelListContainer = findViewById(R.id.fastModelListContainer)
        deepModelListContainer = findViewById(R.id.deepModelListContainer)
        btnAddFastModel = findViewById(R.id.btnAddFastModel)
        btnAddDeepModel = findViewById(R.id.btnAddDeepModel)
        tvTestResult = findViewById(R.id.tvTestResult)
        btnTest = findViewById(R.id.btnTest)
        btnSave = findViewById(R.id.btnSave)

        // Check if settings already exist (API key is saved)
        val existingApiKey = AISettings.getApiKey(this)
        isApiVerified = existingApiKey.isNotBlank()
        updateSaveButtonState()
    }

    private fun applyTheme() {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val headerLayout = findViewById<FrameLayout>(R.id.headerLayout)
        val tvHeaderTitle = findViewById<TextView>(R.id.tvHeaderTitle)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // Cards
        val cardApiKey = findViewById<LinearLayout>(R.id.cardApiKey)
        val cardOcr = findViewById<LinearLayout>(R.id.cardOcr)
        val cardFast = findViewById<LinearLayout>(R.id.cardFast)
        val cardDeep = findViewById<LinearLayout>(R.id.cardDeep)

        // Labels
        val tvApiKeyLabel = findViewById<TextView>(R.id.tvApiKeyLabel)
        val tvApiKeyHint = findViewById<TextView>(R.id.tvApiKeyHint)
        val tvOcrLabel = findViewById<TextView>(R.id.tvOcrLabel)
        val tvOcrHint = findViewById<TextView>(R.id.tvOcrHint)
        val tvOcrUrlLabel = findViewById<TextView>(R.id.tvOcrUrlLabel)
        val tvOcrModelLabel = findViewById<TextView>(R.id.tvOcrModelLabel)
        val tvFastLabel = findViewById<TextView>(R.id.tvFastLabel)
        val tvFastHint = findViewById<TextView>(R.id.tvFastHint)
        val tvFastUrlLabel = findViewById<TextView>(R.id.tvFastUrlLabel)
        val tvFastModelLabel = findViewById<TextView>(R.id.tvFastModelLabel)
        val tvDeepLabel = findViewById<TextView>(R.id.tvDeepLabel)
        val tvDeepHint = findViewById<TextView>(R.id.tvDeepHint)
        val tvDeepUrlLabel = findViewById<TextView>(R.id.tvDeepUrlLabel)
        val tvDeepModelLabel = findViewById<TextView>(R.id.tvDeepModelLabel)

        if (isLightGreenGray) {
            // 浅绿灰主题
            val primaryColor = 0xFF10A37F.toInt()
            val surfaceColor = 0xFFF7F7F8.toInt()
            val textPrimary = 0xFF202123.toInt()
            val textSecondary = 0xFF6E6E80.toInt()

            window.statusBarColor = surfaceColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(surfaceColor)
            headerLayout.setBackgroundColor(surfaceColor)
            tvHeaderTitle.setTextColor(textPrimary)
            btnBack.setColorFilter(textPrimary)

            // Cards
            cardApiKey.setBackgroundResource(R.drawable.bg_card_settings)
            cardOcr.setBackgroundResource(R.drawable.bg_card_settings)
            cardFast.setBackgroundResource(R.drawable.bg_card_settings)
            cardDeep.setBackgroundResource(R.drawable.bg_card_settings)

            // EditTexts
            etApiKey.setBackgroundResource(R.drawable.bg_edittext_settings)
            etOcrBaseUrl.setBackgroundResource(R.drawable.bg_edittext_settings)
            etOcrModelId.setBackgroundResource(R.drawable.bg_edittext_settings)
            etFastBaseUrl.setBackgroundResource(R.drawable.bg_edittext_settings)
            etDeepBaseUrl.setBackgroundResource(R.drawable.bg_edittext_settings)

            // Text colors
            etApiKey.setTextColor(textPrimary)
            etOcrBaseUrl.setTextColor(textPrimary)
            etOcrModelId.setTextColor(textPrimary)
            etFastBaseUrl.setTextColor(textPrimary)
            etDeepBaseUrl.setTextColor(textPrimary)

            // Labels
            tvApiKeyLabel.setTextColor(textPrimary)
            tvApiKeyHint.setTextColor(textSecondary)
            tvOcrLabel.setTextColor(textPrimary)
            tvOcrHint.setTextColor(textSecondary)
            tvOcrUrlLabel.setTextColor(textPrimary)
            tvOcrModelLabel.setTextColor(textPrimary)
            tvFastLabel.setTextColor(textPrimary)
            tvFastHint.setTextColor(textSecondary)
            tvFastUrlLabel.setTextColor(textPrimary)
            tvFastModelLabel.setTextColor(textPrimary)
            tvDeepLabel.setTextColor(textPrimary)
            tvDeepHint.setTextColor(textSecondary)
            tvDeepUrlLabel.setTextColor(textPrimary)
            tvDeepModelLabel.setTextColor(textPrimary)

            btnAddFastModel.setBackgroundResource(R.drawable.bg_button_outline)
            btnAddFastModel.setTextColor(primaryColor)
            btnAddDeepModel.setBackgroundResource(R.drawable.bg_button_outline)
            btnAddDeepModel.setTextColor(primaryColor)

            // Buttons
            btnTest.backgroundTintList = null
            btnTest.setBackgroundResource(R.drawable.bg_button_outline)
            btnTest.setTextColor(primaryColor)
            btnSave.backgroundTintList = null
            btnSave.setBackgroundResource(R.drawable.bg_button_filled)

            // Test result
            tvTestResult.setBackgroundResource(R.drawable.bg_card_settings)

            applyModelRowsTheme(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                isLightGreenGray = true
            )
        } else {
            // 浅棕黑主题 - 暖橙色按钮，黑色文字
            val primaryColor = 0xFFDA7A5A.toInt()
            val backgroundColor = 0xFFFAF9F5.toInt()
            val textPrimary = 0xFF141413.toInt()
            val textSecondary = 0xFF666666.toInt()

            window.statusBarColor = backgroundColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(backgroundColor)
            headerLayout.setBackgroundColor(backgroundColor)
            tvHeaderTitle.setTextColor(textPrimary)
            btnBack.setColorFilter(textPrimary)

            // Cards
            cardApiKey.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)
            cardOcr.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)
            cardFast.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)
            cardDeep.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)

            // EditTexts
            etApiKey.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
            etOcrBaseUrl.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
            etOcrModelId.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
            etFastBaseUrl.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
            etDeepBaseUrl.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)

            // Text colors
            etApiKey.setTextColor(textPrimary)
            etOcrBaseUrl.setTextColor(textPrimary)
            etOcrModelId.setTextColor(textPrimary)
            etFastBaseUrl.setTextColor(textPrimary)
            etDeepBaseUrl.setTextColor(textPrimary)

            // Labels
            tvApiKeyLabel.setTextColor(textPrimary)
            tvApiKeyHint.setTextColor(textSecondary)
            tvOcrLabel.setTextColor(textPrimary)
            tvOcrHint.setTextColor(textSecondary)
            tvOcrUrlLabel.setTextColor(textPrimary)
            tvOcrModelLabel.setTextColor(textPrimary)
            tvFastLabel.setTextColor(textPrimary)
            tvFastHint.setTextColor(textSecondary)
            tvFastUrlLabel.setTextColor(textPrimary)
            tvFastModelLabel.setTextColor(textPrimary)
            tvDeepLabel.setTextColor(textPrimary)
            tvDeepHint.setTextColor(textSecondary)
            tvDeepUrlLabel.setTextColor(textPrimary)
            tvDeepModelLabel.setTextColor(textPrimary)

            btnAddFastModel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnAddFastModel.setTextColor(primaryColor)
            btnAddDeepModel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnAddDeepModel.setTextColor(primaryColor)

            // Buttons
            btnTest.backgroundTintList = null
            btnTest.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnTest.setTextColor(primaryColor)
            btnSave.backgroundTintList = null
            btnSave.setBackgroundResource(R.drawable.bg_button_filled_light_brown_black)

            // Test result
            tvTestResult.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black)

            applyModelRowsTheme(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                isLightGreenGray = false
            )
        }
    }

    private fun applyModelRowsTheme(textPrimary: Int, textSecondary: Int, isLightGreenGray: Boolean) {
        val containers = listOf(fastModelListContainer, deepModelListContainer)
        containers.forEach { container ->
            for (i in 0 until container.childCount) {
                val row = container.getChildAt(i) ?: continue
                val etModelId = row.findViewById<EditText>(R.id.etModelId)
                val btnRemoveModel = row.findViewById<ImageView>(R.id.btnRemoveModel)

                if (isLightGreenGray) {
                    etModelId.setBackgroundResource(R.drawable.bg_edittext_settings)
                    btnRemoveModel.setBackgroundResource(R.drawable.bg_button_outline)
                } else {
                    etModelId.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
                    btnRemoveModel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
                }

                etModelId.setTextColor(textPrimary)
                etModelId.setHintTextColor(textSecondary)
                btnRemoveModel.setColorFilter(textSecondary)
            }
        }
    }

    private fun loadSettings() {
        // API Key
        etApiKey.setText(AISettings.getApiKey(this))

        // OCR Config
        val ocrConfig = AISettings.getOCRConfig(this)
        etOcrBaseUrl.setText(ocrConfig.baseUrl)
        etOcrModelId.setText(ocrConfig.modelId)

        // Fast Config
        val fastConfig = AISettings.getFastConfig(this)
        etFastBaseUrl.setText(fastConfig.baseUrl)
        val fastModels = AISettings.getFastModelList(this).toMutableList()
        val selectedFast = AISettings.getSelectedFastModel(this)
        if (fastModels.remove(selectedFast)) {
            fastModels.add(0, selectedFast)
        }
        setModelRows(fastModelListContainer, fastModels, DEFAULT_FAST_MODEL)

        // Deep Config
        val deepConfig = AISettings.getDeepConfig(this)
        etDeepBaseUrl.setText(deepConfig.baseUrl)
        val deepModels = AISettings.getDeepModelList(this).toMutableList()
        val selectedDeep = AISettings.getSelectedDeepModel(this)
        if (deepModels.remove(selectedDeep)) {
            deepModels.add(0, selectedDeep)
        }
        setModelRows(deepModelListContainer, deepModels, DEFAULT_DEEP_MODEL)
    }

    private fun setModelRows(container: LinearLayout, models: List<String>, fallback: String) {
        container.removeAllViews()
        val normalizedModels = normalizeModelIds(models, fallback)
        normalizedModels.forEach { modelId ->
            addModelRow(container, modelId, false)
        }
    }

    private fun addModelRow(container: LinearLayout, modelId: String, requestFocus: Boolean) {
        val row = layoutInflater.inflate(R.layout.item_model_input, container, false)
        val etModelId = row.findViewById<EditText>(R.id.etModelId)
        val btnRemoveModel = row.findViewById<ImageView>(R.id.btnRemoveModel)
        etModelId.setText(modelId)

        btnRemoveModel.setOnClickListener {
            if (container.childCount <= 1) {
                Toast.makeText(this, "至少保留一个模型", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            container.removeView(row)
        }

        container.addView(row)
        if (requestFocus) {
            etModelId.requestFocus()
            etModelId.setSelection(etModelId.text.length)
        }
    }

    private fun collectModelIds(container: LinearLayout): List<String> {
        val modelIds = mutableListOf<String>()
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) ?: continue
            val etModelId = row.findViewById<EditText>(R.id.etModelId) ?: continue
            val modelId = etModelId.text.toString().trim()
            if (modelId.isNotBlank()) {
                modelIds.add(modelId)
            }
        }
        return modelIds
    }

    private fun normalizeModelIds(rawModels: List<String>, fallback: String): List<String> {
        val normalized = rawModels.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return if (normalized.isEmpty()) listOf(fallback) else normalized
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        btnAddFastModel.setOnClickListener {
            addModelRow(fastModelListContainer, "", true)
            applyTheme()
        }

        btnAddDeepModel.setOnClickListener {
            addModelRow(deepModelListContainer, "", true)
            applyTheme()
        }

        btnSave.setOnClickListener {
            saveSettings()
        }

        btnTest.setOnClickListener {
            testApi()
        }

        // Reset verification when API key changes
        etApiKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Only reset if the key changed from what was saved
                val savedKey = AISettings.getApiKey(this@SettingsActivity)
                if (s.toString().trim() != savedKey) {
                    isApiVerified = false
                    updateSaveButtonState()
                }
            }
        })
    }

    private fun updateSaveButtonState() {
        btnSave.isEnabled = isApiVerified
        btnSave.alpha = if (isApiVerified) 1.0f else 0.5f
    }

    private fun saveSettings() {
        if (!isApiVerified) {
            Toast.makeText(this, "请先点击测试按钮验证API配置", Toast.LENGTH_LONG).show()
            return
        }
        saveSettingsWithoutFinish()
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun saveSettingsWithoutFinish() {
        // Save API Key
        AISettings.saveApiKey(this, etApiKey.text.toString().trim())

        // Save OCR Config
        AISettings.saveOCRConfig(
            this,
            etOcrBaseUrl.text.toString().trim(),
            etOcrModelId.text.toString().trim()
        )

        val fastModels = normalizeModelIds(
            collectModelIds(fastModelListContainer),
            AISettings.getSelectedFastModel(this).ifBlank { DEFAULT_FAST_MODEL }
        )
        val deepModels = normalizeModelIds(
            collectModelIds(deepModelListContainer),
            AISettings.getSelectedDeepModel(this).ifBlank { DEFAULT_DEEP_MODEL }
        )

        AISettings.saveFastModelList(this, fastModels)
        AISettings.saveDeepModelList(this, deepModels)

        val selectedFast = AISettings.getSelectedFastModel(this).takeIf { fastModels.contains(it) } ?: fastModels.first()
        val selectedDeep = AISettings.getSelectedDeepModel(this).takeIf { deepModels.contains(it) } ?: deepModels.first()

        AISettings.setSelectedFastModel(this, selectedFast)
        AISettings.setSelectedDeepModel(this, selectedDeep)

        // Save base URLs and keep currently selected model
        AISettings.saveFastConfig(this, etFastBaseUrl.text.toString().trim(), selectedFast)
        AISettings.saveDeepConfig(this, etDeepBaseUrl.text.toString().trim(), selectedDeep)
    }

    private fun testApi() {
        val apiKey = etApiKey.text.toString().trim()
        val baseUrl = etFastBaseUrl.text.toString().trim()
        val modelId = normalizeModelIds(
            collectModelIds(fastModelListContainer),
            AISettings.getSelectedFastModel(this).ifBlank { DEFAULT_FAST_MODEL }
        ).firstOrNull().orEmpty()

        if (apiKey.isBlank()) {
            showTestResult("错误: API Key 不能为空", false)
            return
        }

        if (baseUrl.isBlank()) {
            showTestResult("错误: Base URL 不能为空", false)
            return
        }

        if (modelId.isBlank()) {
            showTestResult("错误: 至少需要一个模型 ID", false)
            return
        }

        btnTest.isEnabled = false
        btnTest.text = "测试中..."
        showTestResult("正在测试 API 连接...", null)

        coroutineScope.launch {
            try {
                val config = AIConfig(baseUrl, modelId, apiKey)
                val client = OpenAIClient(config)

                val messages = listOf(
                    mapOf("role" to "user", "content" to "Say 'API connection successful!' in exactly those words.")
                )

                val response = withContext(Dispatchers.IO) {
                    client.chatCompletion(messages, stream = false)
                }

                if (response.isSuccessful) {
                    val content = OpenAIClient.parseNonStreamingResponse(response)
                    // Mark as verified and auto-save settings on successful test
                    withContext(Dispatchers.Main) {
                        isApiVerified = true
                        updateSaveButtonState()
                        saveSettingsWithoutFinish()
                        showTestResult("API 测试成功！配置已自动保存\n\n模型: $modelId\n响应: $content", true)
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    withContext(Dispatchers.Main) {
                        isApiVerified = false
                        updateSaveButtonState()
                    }
                    showTestResult("API 测试失败！\n\n状态码: ${response.code}\n错误: $errorBody", false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isApiVerified = false
                    updateSaveButtonState()
                }
                showTestResult("API 测试失败！\n\n错误: ${e.message}", false)
            } finally {
                withContext(Dispatchers.Main) {
                    btnTest.isEnabled = true
                    btnTest.text = "测试连接"
                }
            }
        }
    }

    private fun showTestResult(message: String, success: Boolean?) {
        tvTestResult.visibility = View.VISIBLE
        tvTestResult.text = message
        tvTestResult.setTextColor(
            when (success) {
                true -> 0xFF4CAF50.toInt()  // Green
                false -> 0xFFF44336.toInt() // Red
                null -> 0xFF757575.toInt()  // Gray
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}

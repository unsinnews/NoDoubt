package com.nodoubt.app.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
    private lateinit var etBaseUrl: EditText
    private lateinit var etOcrModelId: EditText
    private lateinit var fastModelListContainer: LinearLayout
    private lateinit var deepModelListContainer: LinearLayout
    private lateinit var btnAddFastModel: TextView
    private lateinit var btnAddDeepModel: TextView
    private lateinit var btnTestOcr: TextView
    private lateinit var btnTestFast: TextView
    private lateinit var btnTestDeep: TextView
    private lateinit var btnSave: Button

    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)

    // Track if API has been verified
    private var isApiVerified = false

    companion object {
        private const val DEFAULT_FAST_MODEL = "gpt-4o-mini"
        private const val DEFAULT_DEEP_MODEL = "gpt-4o"
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }

    private enum class TestTarget {
        OCR, FAST, DEEP
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
        etBaseUrl = findViewById(R.id.etBaseUrl)
        etOcrModelId = findViewById(R.id.etOcrModelId)
        fastModelListContainer = findViewById(R.id.fastModelListContainer)
        deepModelListContainer = findViewById(R.id.deepModelListContainer)
        btnAddFastModel = findViewById(R.id.btnAddFastModel)
        btnAddDeepModel = findViewById(R.id.btnAddDeepModel)
        btnTestOcr = findViewById(R.id.btnTestOcr)
        btnTestFast = findViewById(R.id.btnTestFast)
        btnTestDeep = findViewById(R.id.btnTestDeep)
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
        val tvBaseUrlLabel = findViewById<TextView>(R.id.tvBaseUrlLabel)
        val tvOcrLabel = findViewById<TextView>(R.id.tvOcrLabel)
        val tvOcrHint = findViewById<TextView>(R.id.tvOcrHint)
        val tvOcrModelLabel = findViewById<TextView>(R.id.tvOcrModelLabel)
        val tvFastLabel = findViewById<TextView>(R.id.tvFastLabel)
        val tvFastHint = findViewById<TextView>(R.id.tvFastHint)
        val tvFastModelLabel = findViewById<TextView>(R.id.tvFastModelLabel)
        val tvDeepLabel = findViewById<TextView>(R.id.tvDeepLabel)
        val tvDeepHint = findViewById<TextView>(R.id.tvDeepHint)
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
            etBaseUrl.setBackgroundResource(R.drawable.bg_edittext_settings)
            etOcrModelId.setBackgroundResource(R.drawable.bg_edittext_settings)

            // Text colors
            etApiKey.setTextColor(textPrimary)
            etBaseUrl.setTextColor(textPrimary)
            etOcrModelId.setTextColor(textPrimary)

            // Labels
            tvApiKeyLabel.setTextColor(textPrimary)
            tvApiKeyHint.setTextColor(textSecondary)
            tvBaseUrlLabel.setTextColor(textPrimary)
            tvOcrLabel.setTextColor(textPrimary)
            tvOcrHint.setTextColor(textSecondary)
            tvOcrModelLabel.setTextColor(textPrimary)
            tvFastLabel.setTextColor(textPrimary)
            tvFastHint.setTextColor(textSecondary)
            tvFastModelLabel.setTextColor(textPrimary)
            tvDeepLabel.setTextColor(textPrimary)
            tvDeepHint.setTextColor(textSecondary)
            tvDeepModelLabel.setTextColor(textPrimary)

            btnAddFastModel.setBackgroundResource(R.drawable.bg_button_outline)
            btnAddFastModel.setTextColor(primaryColor)
            btnAddDeepModel.setBackgroundResource(R.drawable.bg_button_outline)
            btnAddDeepModel.setTextColor(primaryColor)
            btnTestOcr.setBackgroundResource(R.drawable.bg_button_outline)
            btnTestOcr.setTextColor(primaryColor)
            btnTestFast.setBackgroundResource(R.drawable.bg_button_outline)
            btnTestFast.setTextColor(primaryColor)
            btnTestDeep.setBackgroundResource(R.drawable.bg_button_outline)
            btnTestDeep.setTextColor(primaryColor)

            // Buttons
            btnSave.backgroundTintList = null
            btnSave.setBackgroundResource(R.drawable.bg_button_outline)
            btnSave.setTextColor(primaryColor)

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
            etBaseUrl.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
            etOcrModelId.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)

            // Text colors
            etApiKey.setTextColor(textPrimary)
            etBaseUrl.setTextColor(textPrimary)
            etOcrModelId.setTextColor(textPrimary)

            // Labels
            tvApiKeyLabel.setTextColor(textPrimary)
            tvApiKeyHint.setTextColor(textSecondary)
            tvBaseUrlLabel.setTextColor(textPrimary)
            tvOcrLabel.setTextColor(textPrimary)
            tvOcrHint.setTextColor(textSecondary)
            tvOcrModelLabel.setTextColor(textPrimary)
            tvFastLabel.setTextColor(textPrimary)
            tvFastHint.setTextColor(textSecondary)
            tvFastModelLabel.setTextColor(textPrimary)
            tvDeepLabel.setTextColor(textPrimary)
            tvDeepHint.setTextColor(textSecondary)
            tvDeepModelLabel.setTextColor(textPrimary)

            btnAddFastModel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnAddFastModel.setTextColor(primaryColor)
            btnAddDeepModel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnAddDeepModel.setTextColor(primaryColor)
            btnTestOcr.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnTestOcr.setTextColor(primaryColor)
            btnTestFast.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnTestFast.setTextColor(primaryColor)
            btnTestDeep.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnTestDeep.setTextColor(primaryColor)

            // Buttons
            btnSave.backgroundTintList = null
            btnSave.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnSave.setTextColor(primaryColor)

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
        etBaseUrl.setText(AISettings.getBaseUrl(this))

        // OCR Config
        val ocrConfig = AISettings.getOCRConfig(this)
        etOcrModelId.setText(ocrConfig.modelId)

        // Fast Config
        val fastModels = AISettings.getFastModelList(this).toMutableList()
        val selectedFast = AISettings.getSelectedFastModel(this)
        if (fastModels.remove(selectedFast)) {
            fastModels.add(0, selectedFast)
        }
        setModelRows(fastModelListContainer, fastModels, DEFAULT_FAST_MODEL)

        // Deep Config
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
        addCompactInputWatcher(etModelId) {
            markVerificationDirty()
        }

        btnRemoveModel.setOnClickListener {
            if (container.childCount <= 1) {
                Toast.makeText(this, "至少保留一个模型", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            container.removeView(row)
            markVerificationDirty()
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
            val modelId = sanitizeEditTextInPlace(etModelId)
            if (modelId.isNotBlank()) {
                modelIds.add(modelId)
            }
        }
        return modelIds
    }

    private fun normalizeModelIds(rawModels: List<String>, fallback: String): List<String> {
        val normalized = rawModels.map { compactInput(it) }.filter { it.isNotBlank() }.distinct()
        val normalizedFallback = compactInput(fallback).ifBlank { DEFAULT_FAST_MODEL }
        return if (normalized.isEmpty()) listOf(normalizedFallback) else normalized
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        btnAddFastModel.setOnClickListener {
            addModelRow(fastModelListContainer, "", true)
            markVerificationDirty()
            applyTheme()
        }

        btnAddDeepModel.setOnClickListener {
            addModelRow(deepModelListContainer, "", true)
            markVerificationDirty()
            applyTheme()
        }

        btnSave.setOnClickListener {
            saveSettings()
        }

        btnTestOcr.setOnClickListener { testApi(TestTarget.OCR) }
        btnTestFast.setOnClickListener { testApi(TestTarget.FAST) }
        btnTestDeep.setOnClickListener { testApi(TestTarget.DEEP) }

        setupInputGuardrails()
    }

    private fun updateSaveButtonState() {
        btnSave.isEnabled = isApiVerified
        btnSave.alpha = if (isApiVerified) 1.0f else 0.5f
    }

    private fun saveSettings() {
        if (!isApiVerified) {
            Toast.makeText(this, "请先点击模型右侧“检测”验证连接", Toast.LENGTH_LONG).show()
            return
        }
        saveSettingsWithoutFinish()
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun saveSettingsWithoutFinish() {
        val apiKey = sanitizeEditTextInPlace(etApiKey)
        val baseUrl = sanitizeEditTextInPlace(etBaseUrl)
        val ocrModelId = sanitizeEditTextInPlace(etOcrModelId)

        // Save API Key
        AISettings.saveApiKey(this, apiKey)
        AISettings.saveBaseUrl(this, baseUrl)

        // Save OCR Config
        AISettings.saveOCRConfig(this, ocrModelId)

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
    }

    private fun testApi(target: TestTarget) {
        val apiKey = sanitizeEditTextInPlace(etApiKey)
        val baseUrl = sanitizeEditTextInPlace(etBaseUrl)

        if (apiKey.isBlank()) {
            showConnectionResultDialog(success = false, responseBody = "API Key 不能为空")
            return
        }

        if (baseUrl.isBlank()) {
            showConnectionResultDialog(success = false, responseBody = "Base URL 不能为空")
            return
        }

        val modelCandidates = getModelCandidatesForTest(target)
        if (modelCandidates.isEmpty()) {
            showConnectionResultDialog(success = false, responseBody = "模型 ID 不能为空")
            return
        }

        if (target == TestTarget.OCR || modelCandidates.size == 1) {
            runApiTest(target, apiKey, baseUrl, modelCandidates.first())
            return
        }

        showModelSelectionDialog(target, modelCandidates) { selectedModel ->
            runApiTest(target, apiKey, baseUrl, selectedModel)
        }
    }

    private fun runApiTest(target: TestTarget, apiKey: String, baseUrl: String, modelId: String) {
        val testButton = getTestButton(target)
        testButton.isEnabled = false
        testButton.text = "检测中..."

        coroutineScope.launch {
            try {
                val config = AIConfig(baseUrl, modelId, apiKey)
                val client = OpenAIClient(config)
                val messages = listOf(
                    mapOf("role" to "user", "content" to "ping")
                )

                val response = withContext(Dispatchers.IO) {
                    client.chatCompletion(messages, stream = false)
                }
                val errorBody = if (response.isSuccessful) {
                    ""
                } else {
                    response.body?.string()?.ifBlank { "Empty response body" } ?: "Empty response body"
                }

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        when (target) {
                            TestTarget.FAST -> AISettings.setSelectedFastModel(this@SettingsActivity, modelId)
                            TestTarget.DEEP -> AISettings.setSelectedDeepModel(this@SettingsActivity, modelId)
                            TestTarget.OCR -> Unit
                        }
                        isApiVerified = true
                        updateSaveButtonState()
                        saveSettingsWithoutFinish()
                        showConnectionResultDialog(success = true)
                    } else {
                        isApiVerified = false
                        updateSaveButtonState()
                        showConnectionResultDialog(success = false, responseBody = errorBody)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isApiVerified = false
                    updateSaveButtonState()
                    showConnectionResultDialog(
                        success = false,
                        responseBody = e.message?.ifBlank { "Unknown error" } ?: "Unknown error"
                    )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    testButton.isEnabled = true
                    testButton.text = "检测"
                }
            }
        }
    }

    private fun getModelCandidatesForTest(target: TestTarget): List<String> {
        return when (target) {
            TestTarget.OCR -> {
                val model = sanitizeEditTextInPlace(etOcrModelId)
                if (model.isBlank()) emptyList() else listOf(model)
            }
            TestTarget.FAST -> normalizeModelIds(
                collectModelIds(fastModelListContainer),
                AISettings.getSelectedFastModel(this).ifBlank { DEFAULT_FAST_MODEL }
            )
            TestTarget.DEEP -> normalizeModelIds(
                collectModelIds(deepModelListContainer),
                AISettings.getSelectedDeepModel(this).ifBlank { DEFAULT_DEEP_MODEL }
            )
        }
    }

    private fun showModelSelectionDialog(
        target: TestTarget,
        models: List<String>,
        onModelSelected: (String) -> Unit
    ) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        val dialog = Dialog(this, R.style.RoundedDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_model_picker)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = dialog.findViewById<LinearLayout>(R.id.modelPickerRoot)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvModelPickerTitle)
        val tvSubtitle = dialog.findViewById<TextView>(R.id.tvModelPickerSubtitle)
        val optionsContainer = dialog.findViewById<LinearLayout>(R.id.modelPickerOptionsContainer)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancelModelPicker)

        val primaryColor = if (isLightGreenGray) 0xFF10A37F.toInt() else 0xFFDA7A5A.toInt()
        val textPrimary = if (isLightGreenGray) 0xFF1D2A2F.toInt() else 0xFF2C241F.toInt()
        val textSecondary = if (isLightGreenGray) 0xFF647177.toInt() else 0xFF6F625B.toInt()
        val selectedModel = when (target) {
            TestTarget.FAST -> AISettings.getSelectedFastModel(this)
            TestTarget.DEEP -> AISettings.getSelectedDeepModel(this)
            TestTarget.OCR -> sanitizeEditTextInPlace(etOcrModelId)
        }

        root.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_model_dialog_surface
            else R.drawable.bg_model_dialog_surface_light_brown_black
        )
        tvTitle.text = when (target) {
            TestTarget.FAST -> "选择极速模式检测模型"
            TestTarget.DEEP -> "选择深度模式检测模型"
            TestTarget.OCR -> "选择检测模型"
        }
        tvTitle.setTextColor(textPrimary)
        tvSubtitle.setTextColor(textSecondary)
        btnCancel.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_button_outline
            else R.drawable.bg_button_outline_light_brown_black
        )
        btnCancel.setTextColor(primaryColor)
        btnCancel.setOnClickListener { dialog.dismiss() }

        optionsContainer.removeAllViews()
        models.forEachIndexed { index, model ->
            val row = layoutInflater.inflate(R.layout.item_model_picker_option, optionsContainer, false)
            val optionRoot = row.findViewById<LinearLayout>(R.id.modelPickerOptionRoot)
            val tvModelName = row.findViewById<TextView>(R.id.tvModelPickerName)
            val ivCheck = row.findViewById<ImageView>(R.id.ivModelPickerCheck)
            val isSelected = model == selectedModel

            tvModelName.text = model
            tvModelName.setTextColor(textPrimary)
            ivCheck.setColorFilter(primaryColor)
            ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            optionRoot.setBackgroundResource(
                when {
                    isSelected && isLightGreenGray -> R.drawable.bg_model_option_selected
                    isSelected && !isLightGreenGray -> R.drawable.bg_model_option_selected_light_brown_black
                    !isSelected && isLightGreenGray -> R.drawable.bg_model_option_unselected
                    else -> R.drawable.bg_model_option_unselected_light_brown_black
                }
            )

            optionRoot.setOnClickListener {
                optionRoot.isEnabled = false
                optionRoot.animate()
                    .scaleX(0.98f)
                    .scaleY(0.98f)
                    .setDuration(70)
                    .withEndAction {
                        dialog.dismiss()
                        onModelSelected(model)
                    }
                    .start()
            }

            optionsContainer.addView(row)
            optionRoot.alpha = 0f
            optionRoot.translationY = dp(6f)
            optionRoot.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 35L))
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        dialog.show()
        applyModelPickerDialogWindowSize(dialog)
        root.alpha = 0f
        root.translationY = dp(8f)
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun getTestButton(target: TestTarget): TextView {
        return when (target) {
            TestTarget.OCR -> btnTestOcr
            TestTarget.FAST -> btnTestFast
            TestTarget.DEEP -> btnTestDeep
        }
    }

    private fun showConnectionResultDialog(success: Boolean, responseBody: String = "") {
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

        val textPrimary = if (isLightGreenGray) 0xFF1C2420.toInt() else 0xFF2C241F.toInt()
        val textSecondary = if (isLightGreenGray) 0xFF5E6872.toInt() else 0xFF6F625B.toInt()
        val successAccent = if (isLightGreenGray) 0xFF10A37F.toInt() else 0xFFDA7A5A.toInt()

        if (isLightGreenGray) {
            root.setBackgroundResource(R.drawable.bg_connection_dialog_surface)
            btnConfirm.setBackgroundResource(R.drawable.bg_connection_action_button)
            btnConfirm.setTextColor(successAccent)
        } else {
            root.setBackgroundResource(R.drawable.bg_connection_dialog_surface_light_brown_black)
            btnConfirm.setBackgroundResource(R.drawable.bg_connection_action_button_light_brown_black)
            btnConfirm.setTextColor(successAccent)
        }

        btnConfirm.setOnClickListener { dialog.dismiss() }
        tvResultTitle.setTextColor(textPrimary)
        tvResultMessage.setTextColor(textSecondary)

        if (success) {
            tvResultTitle.text = "连接成功"
            tvResultTitle.setTextColor(successAccent)
            tvResultMessage.visibility = View.GONE
            ambientGlow.setBackgroundResource(R.drawable.bg_connection_ambient_glow_success)
            ringOuter.setBackgroundResource(R.drawable.bg_connection_ring_success)
            ringInner.setBackgroundResource(R.drawable.bg_connection_ring_success)
            statusOrb.setBackgroundResource(R.drawable.bg_connection_orb_success)
            ivStatusIcon.setImageResource(R.drawable.ic_check)
            ivStatusIcon.setColorFilter(successAccent)
        } else {
            tvResultTitle.text = "连接失败"
            tvResultTitle.setTextColor(0xFFE25656.toInt())
            tvResultMessage.visibility = View.VISIBLE
            tvResultMessage.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            tvResultMessage.text = "响应体：\n${responseBody.ifBlank { "Empty response body" }}"
            ambientGlow.setBackgroundResource(R.drawable.bg_connection_ambient_glow_failure)
            ringOuter.setBackgroundResource(R.drawable.bg_connection_ring_failure)
            ringInner.setBackgroundResource(R.drawable.bg_connection_ring_failure)
            statusOrb.setBackgroundResource(R.drawable.bg_connection_orb_failure)
            ivStatusIcon.setImageResource(R.drawable.ic_info)
            ivStatusIcon.setColorFilter(0xFFDF5D5D.toInt())
        }

        dialog.show()
        applyResultDialogWindowSize(dialog, success)

        if (success) {
            startSuccessResultAnimation(dialog, root, statusOrb, ivStatusIcon, ringOuter, ringInner)
        } else {
            startFailureResultAnimation(root, statusOrb, ivStatusIcon, ringOuter, ringInner)
        }
    }

    private fun startSuccessResultAnimation(
        dialog: Dialog,
        root: View,
        orb: View,
        icon: View,
        ringOuter: View,
        ringInner: View
    ) {
        root.alpha = 0f
        root.translationY = dp(6f)
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()

        orb.scaleX = 0.92f
        orb.scaleY = 0.92f
        icon.alpha = 0f
        icon.scaleX = 0.88f
        icon.scaleY = 0.88f
        icon.rotation = -5f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(orb, View.SCALE_X, 0.92f, 1f),
                ObjectAnimator.ofFloat(orb, View.SCALE_Y, 0.92f, 1f),
                ObjectAnimator.ofFloat(icon, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(icon, View.SCALE_X, 0.88f, 1f),
                ObjectAnimator.ofFloat(icon, View.SCALE_Y, 0.88f, 1f),
                ObjectAnimator.ofFloat(icon, View.ROTATION, -5f, 0f)
            )
            duration = 300
            interpolator = OvershootInterpolator(0.6f)
            start()
        }

        val outerPulse = createRingPulseAnimator(ringOuter, delayMs = 0L)
        val innerPulse = createRingPulseAnimator(ringInner, delayMs = 200L)
        outerPulse.start()
        innerPulse.start()

        dialog.setOnDismissListener {
            outerPulse.cancel()
            innerPulse.cancel()
        }
    }

    private fun startFailureResultAnimation(
        root: View,
        orb: View,
        icon: View,
        ringOuter: View,
        ringInner: View
    ) {
        root.alpha = 0f
        root.translationY = dp(6f)
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        orb.scaleX = 0.93f
        orb.scaleY = 0.93f
        orb.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .start()

        icon.alpha = 0f
        icon.animate().alpha(1f).setDuration(180).start()
        ringOuter.alpha = 0.42f
        ringInner.alpha = 0.28f
        ringOuter.scaleX = 1f
        ringOuter.scaleY = 1f
        ringInner.scaleX = 1f
        ringInner.scaleY = 1f
    }

    private fun createRingPulseAnimator(target: View, delayMs: Long): Animator {
        return ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.95f, 1.01f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.95f, 1.01f, 1.06f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.06f, 0.26f, 0f)
        ).apply {
            duration = 1200
            startDelay = delayMs
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = DecelerateInterpolator()
        }
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun applyResultDialogWindowSize(dialog: Dialog, success: Boolean) {
        val screenWidth = resources.displayMetrics.widthPixels
        val maxWidth = if (success) dp(230f).toInt() else dp(360f).toInt()
        val ratio = if (success) 0.72f else 0.9f
        val targetWidth = minOf((screenWidth * ratio).toInt(), maxWidth)
        dialog.window?.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun applyModelPickerDialogWindowSize(dialog: Dialog) {
        val screenWidth = resources.displayMetrics.widthPixels
        val maxWidth = dp(340f).toInt()
        val targetWidth = minOf((screenWidth * 0.88f).toInt(), maxWidth)
        dialog.window?.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun setupInputGuardrails() {
        addCompactInputWatcher(etApiKey) { compact ->
            // Only reset if the key changed from what was saved.
            val savedKey = AISettings.getApiKey(this@SettingsActivity)
            if (compact != savedKey) {
                markVerificationDirty()
            }
        }
        addCompactInputWatcher(etBaseUrl) {
            markVerificationDirty()
        }
        addCompactInputWatcher(etOcrModelId) {
            markVerificationDirty()
        }
    }

    private fun markVerificationDirty() {
        if (!isApiVerified) return
        isApiVerified = false
        updateSaveButtonState()
    }

    private fun compactInput(raw: String): String {
        return raw.replace(WHITESPACE_REGEX, "")
    }

    private fun sanitizeEditTextInPlace(editText: EditText): String {
        val raw = editText.text?.toString().orEmpty()
        val compact = compactInput(raw)
        if (raw != compact) {
            editText.setText(compact)
            editText.setSelection(compact.length)
        }
        return compact
    }

    private fun addCompactInputWatcher(
        editText: EditText,
        onCompactChanged: ((String) -> Unit)? = null
    ) {
        var isUpdating = false
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return

                val raw = s?.toString().orEmpty()
                val compact = compactInput(raw)
                if (raw != compact) {
                    val cursor = editText.selectionStart.coerceAtLeast(0)
                    val removedBeforeCursor = raw.take(cursor).count { it.isWhitespace() }
                    val newCursor = (cursor - removedBeforeCursor).coerceIn(0, compact.length)
                    isUpdating = true
                    editText.setText(compact)
                    editText.setSelection(newCursor)
                    isUpdating = false
                }
                onCompactChanged?.invoke(compact)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}

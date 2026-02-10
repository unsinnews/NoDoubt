package com.nodoubt.app.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var ocrModelListContainer: RecyclerView
    private lateinit var fastModelListContainer: RecyclerView
    private lateinit var deepModelListContainer: RecyclerView
    private lateinit var btnAddOcrModel: TextView
    private lateinit var btnAddFastModel: TextView
    private lateinit var btnAddDeepModel: TextView
    private lateinit var btnFetchOcrModels: TextView
    private lateinit var btnFetchFastModels: TextView
    private lateinit var btnFetchDeepModels: TextView
    private lateinit var btnTestOcr: TextView
    private lateinit var btnTestFast: TextView
    private lateinit var btnTestDeep: TextView
    private lateinit var btnSave: Button

    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var ocrModelAdapter: ModelListAdapter
    private lateinit var fastModelAdapter: ModelListAdapter
    private lateinit var deepModelAdapter: ModelListAdapter

    // Track if API has been verified
    private var isApiVerified = false

    companion object {
        private const val DEFAULT_OCR_MODEL = "gpt-4o"
        private const val DEFAULT_FAST_MODEL = "gpt-4o-mini"
        private const val DEFAULT_DEEP_MODEL = "gpt-4o"
        private val WHITESPACE_REGEX = "\\s+".toRegex()
        private val NON_CHAT_MODEL_REGEX = "(embedding|rerank|tts|whisper|speech|moderation|transcription)".toRegex(
            RegexOption.IGNORE_CASE
        )
        private val IMAGE_GENERATION_ONLY_REGEX =
            "(dall-e|gpt-image|imagen|stable[-_]?diffusion|flux|midjourney|qwen-image|cogview|seedream|hunyuanimage)".toRegex(
                RegexOption.IGNORE_CASE
            )
        private val VISION_MODEL_REGEX =
            "(vision|(^|[-_.])vl([-.]|$)|omni|gpt-4o|gpt-4\\.1|gpt-5|chatgpt-4o|claude-3|claude-(haiku|sonnet|opus)-4|gemini|qwen2?-?\\.?(5)?-?vl|llava|minicpm|internvl|pixtral|glm-4v|deepseek-vl|step-1v|qvq)".toRegex(
                RegexOption.IGNORE_CASE
            )
        private val REASONING_MODEL_REGEX =
            "(^o[1345]([-.].*)?$|reasoning|reasoner|thinking|think|(^|[-_.])r1($|[-_.])|(^|[-_.])r\\d+($|[-_.])|qwq|deepseek-r1|grok-(3|4)|qwen.*thinking|magistral|seed-oss)".toRegex(
                RegexOption.IGNORE_CASE
            )
        private val NON_REASONING_HINT_REGEX = "non-reasoning".toRegex(RegexOption.IGNORE_CASE)
    }

    private enum class TestTarget {
        OCR, FAST, DEEP
    }

    private enum class ModelFilterCategory {
        ALL, REASONING, VISION
    }

    private data class CatalogModel(
        val id: String,
        val name: String,
        val ownedBy: String?,
        val supportedEndpointTypes: List<String>,
        val isVision: Boolean,
        val isReasoning: Boolean
    )

    private data class ModelRowPalette(
        val textPrimary: Int,
        val textSecondary: Int,
        val rowBackgroundRes: Int,
        val actionButtonBackgroundRes: Int
    )

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
        ocrModelListContainer = findViewById(R.id.ocrModelListContainer)
        fastModelListContainer = findViewById(R.id.fastModelListContainer)
        deepModelListContainer = findViewById(R.id.deepModelListContainer)
        btnAddOcrModel = findViewById(R.id.btnAddOcrModel)
        btnAddFastModel = findViewById(R.id.btnAddFastModel)
        btnAddDeepModel = findViewById(R.id.btnAddDeepModel)
        btnFetchOcrModels = findViewById(R.id.btnFetchOcrModels)
        btnFetchFastModels = findViewById(R.id.btnFetchFastModels)
        btnFetchDeepModels = findViewById(R.id.btnFetchDeepModels)
        btnTestOcr = findViewById(R.id.btnTestOcr)
        btnTestFast = findViewById(R.id.btnTestFast)
        btnTestDeep = findViewById(R.id.btnTestDeep)
        btnSave = findViewById(R.id.btnSave)
        setupModelRecyclerViews()

        // Check if settings already exist (API key is saved)
        val existingApiKey = AISettings.getApiKey(this)
        isApiVerified = existingApiKey.isNotBlank()
        updateSaveButtonState()
    }

    private fun setupModelRecyclerViews() {
        ocrModelAdapter = createModelAdapter(ocrModelListContainer)
        fastModelAdapter = createModelAdapter(fastModelListContainer)
        deepModelAdapter = createModelAdapter(deepModelListContainer)
    }

    private fun createModelAdapter(recyclerView: RecyclerView): ModelListAdapter {
        lateinit var itemTouchHelper: ItemTouchHelper
        val adapter = ModelListAdapter(
            onStartDrag = { viewHolder ->
                viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                itemTouchHelper.startDrag(viewHolder)
            },
            onRemoveRequest = { modelAdapter, position ->
                if (modelAdapter.itemCount <= 1) {
                    Toast.makeText(this, "至少保留一个模型", Toast.LENGTH_SHORT).show()
                    false
                } else {
                    modelAdapter.removeAt(position)
                    markVerificationDirty()
                    true
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(false)
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        // ItemTouchHelper + ScrollView scene can produce stale alpha/translation with default animator.
        recyclerView.itemAnimator = null

        itemTouchHelper = ItemTouchHelper(createDragCallback(adapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)
        return adapter
    }

    private fun createDragCallback(adapter: ModelListAdapter): ItemTouchHelper.Callback {
        var hasMoved = false
        return object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return try {
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                    val moved = adapter.moveItem(from, to)
                    if (moved) {
                        hasMoved = true
                    }
                    moved
                } catch (_: Exception) {
                    false
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.12f

            override fun getBoundingBoxMargin(): Int = dp(6f).toInt()

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    val itemView = viewHolder.itemView
                    val maxUp = -itemView.top.toFloat()
                    val maxDown = (recyclerView.height - itemView.bottom).toFloat()
                    val clampedDY = if (maxUp <= maxDown) {
                        dY.coerceIn(maxUp, maxDown)
                    } else {
                        dY
                    }
                    super.onChildDraw(c, recyclerView, viewHolder, dX, clampedDY, actionState, isCurrentlyActive)
                    return
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    hasMoved = false
                    recyclerViewParent(viewHolder.itemView)?.requestDisallowInterceptTouchEvent(true)
                    viewHolder.itemView.animate().cancel()
                    viewHolder.itemView.animate()
                        .scaleX(1.018f)
                        .scaleY(1.018f)
                        .translationZ(dp(10f))
                        .setDuration(90)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
                viewHolder.itemView.animate().cancel()
                viewHolder.itemView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationZ(0f)
                    .setDuration(120)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                if (hasMoved) {
                    markVerificationDirty()
                }
            }

            private fun recyclerViewParent(itemView: View): android.view.ViewParent? {
                return (itemView.parent as? RecyclerView)?.parent
            }
        }
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

            // Text colors
            etApiKey.setTextColor(textPrimary)
            etBaseUrl.setTextColor(textPrimary)

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

            btnAddOcrModel.setBackgroundResource(R.drawable.bg_button_outline)
            btnAddOcrModel.setTextColor(primaryColor)
            btnAddFastModel.setBackgroundResource(R.drawable.bg_button_outline)
            btnAddFastModel.setTextColor(primaryColor)
            btnAddDeepModel.setBackgroundResource(R.drawable.bg_button_outline)
            btnAddDeepModel.setTextColor(primaryColor)
            btnFetchOcrModels.setBackgroundResource(R.drawable.bg_button_outline)
            btnFetchOcrModels.setTextColor(primaryColor)
            btnFetchFastModels.setBackgroundResource(R.drawable.bg_button_outline)
            btnFetchFastModels.setTextColor(primaryColor)
            btnFetchDeepModels.setBackgroundResource(R.drawable.bg_button_outline)
            btnFetchDeepModels.setTextColor(primaryColor)
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

            // Text colors
            etApiKey.setTextColor(textPrimary)
            etBaseUrl.setTextColor(textPrimary)

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

            btnAddOcrModel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnAddOcrModel.setTextColor(primaryColor)
            btnAddFastModel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnAddFastModel.setTextColor(primaryColor)
            btnAddDeepModel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnAddDeepModel.setTextColor(primaryColor)
            btnFetchOcrModels.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnFetchOcrModels.setTextColor(primaryColor)
            btnFetchFastModels.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnFetchFastModels.setTextColor(primaryColor)
            btnFetchDeepModels.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnFetchDeepModels.setTextColor(primaryColor)
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
        val palette = ModelRowPalette(
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            rowBackgroundRes = if (isLightGreenGray) {
                R.drawable.bg_model_option_unselected
            } else {
                R.drawable.bg_model_option_unselected_light_brown_black
            },
            actionButtonBackgroundRes = if (isLightGreenGray) {
                R.drawable.bg_button_outline
            } else {
                R.drawable.bg_button_outline_light_brown_black
            }
        )
        if (::ocrModelAdapter.isInitialized) ocrModelAdapter.applyPalette(palette)
        if (::fastModelAdapter.isInitialized) fastModelAdapter.applyPalette(palette)
        if (::deepModelAdapter.isInitialized) deepModelAdapter.applyPalette(palette)
    }

    private fun loadSettings() {
        // API Key
        etApiKey.setText(AISettings.getApiKey(this))
        etBaseUrl.setText(AISettings.getBaseUrl(this))

        // OCR Config
        val ocrModels = AISettings.getOCRModelList(this)
        setModelRows(ocrModelListContainer, ocrModels, DEFAULT_OCR_MODEL)

        // Fast Config
        val fastModels = AISettings.getFastModelList(this)
        setModelRows(fastModelListContainer, fastModels, DEFAULT_FAST_MODEL)

        // Deep Config
        val deepModels = AISettings.getDeepModelList(this)
        setModelRows(deepModelListContainer, deepModels, DEFAULT_DEEP_MODEL)
    }

    private fun setModelRows(container: RecyclerView, models: List<String>, fallback: String) {
        val normalizedModels = normalizeModelIds(models, fallback)
        getModelAdapter(container).setItems(normalizedModels)
    }

    private fun collectModelIds(container: RecyclerView): List<String> {
        return getModelAdapter(container).getItems()
    }

    private fun getModelAdapter(container: RecyclerView): ModelListAdapter {
        return when (container.id) {
            R.id.ocrModelListContainer -> ocrModelAdapter
            R.id.fastModelListContainer -> fastModelAdapter
            R.id.deepModelListContainer -> deepModelAdapter
            else -> error("Unknown model list container: ${container.id}")
        }
    }

    private fun getModelAdapter(target: TestTarget): ModelListAdapter {
        return when (target) {
            TestTarget.OCR -> ocrModelAdapter
            TestTarget.FAST -> fastModelAdapter
            TestTarget.DEEP -> deepModelAdapter
        }
    }

    private fun getModelRecycler(target: TestTarget): RecyclerView {
        return when (target) {
            TestTarget.OCR -> ocrModelListContainer
            TestTarget.FAST -> fastModelListContainer
            TestTarget.DEEP -> deepModelListContainer
        }
    }

    private fun appendModelToTarget(target: TestTarget, rawModelId: String): Boolean {
        val modelId = compactInput(rawModelId)
        if (modelId.isBlank()) return false

        val adapter = getModelAdapter(target)
        val added = adapter.appendItem(modelId)
        if (!added) {
            Toast.makeText(this, "模型已存在", Toast.LENGTH_SHORT).show()
            return false
        }

        markVerificationDirty()
        val recycler = getModelRecycler(target)
        recycler.post {
            recycler.requestLayout()
            recycler.smoothScrollToPosition(adapter.itemCount - 1)
        }
        return true
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

        btnAddOcrModel.setOnClickListener {
            showAddModelDialog(TestTarget.OCR)
        }

        btnAddFastModel.setOnClickListener {
            showAddModelDialog(TestTarget.FAST)
        }

        btnAddDeepModel.setOnClickListener {
            showAddModelDialog(TestTarget.DEEP)
        }

        btnSave.setOnClickListener {
            saveSettings()
        }

        btnFetchOcrModels.setOnClickListener { fetchModelCatalog(TestTarget.OCR) }
        btnFetchFastModels.setOnClickListener { fetchModelCatalog(TestTarget.FAST) }
        btnFetchDeepModels.setOnClickListener { fetchModelCatalog(TestTarget.DEEP) }
        btnTestOcr.setOnClickListener { testApi(TestTarget.OCR) }
        btnTestFast.setOnClickListener { testApi(TestTarget.FAST) }
        btnTestDeep.setOnClickListener { testApi(TestTarget.DEEP) }

        setupInputGuardrails()
    }

    private fun showAddModelDialog(target: TestTarget) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        val dialog = Dialog(this, R.style.RoundedDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_model)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = dialog.findViewById<LinearLayout>(R.id.addModelRoot)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvAddModelTitle)
        val tvSubtitle = dialog.findViewById<TextView>(R.id.tvAddModelSubtitle)
        val etModelId = dialog.findViewById<EditText>(R.id.etAddModelInput)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancelAddModel)
        val btnSaveModel = dialog.findViewById<TextView>(R.id.btnSaveAddModel)

        val primaryColor = if (isLightGreenGray) 0xFF10A37F.toInt() else 0xFFDA7A5A.toInt()
        val textPrimary = if (isLightGreenGray) 0xFF1D2A2F.toInt() else 0xFF2C241F.toInt()
        val textSecondary = if (isLightGreenGray) 0xFF5E6872.toInt() else 0xFF6F625B.toInt()

        root.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_model_dialog_surface
            else R.drawable.bg_model_dialog_surface_light_brown_black
        )
        tvTitle.setTextColor(textPrimary)
        tvSubtitle.setTextColor(textSecondary)
        etModelId.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_edittext_settings
            else R.drawable.bg_edittext_settings_light_brown_black
        )
        etModelId.setTextColor(textPrimary)
        etModelId.setHintTextColor(textSecondary)

        btnCancel.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_button_outline
            else R.drawable.bg_button_outline_light_brown_black
        )
        btnCancel.setTextColor(primaryColor)
        btnSaveModel.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_button_filled
            else R.drawable.bg_button_filled_light_brown_black
        )
        btnSaveModel.setTextColor(0xFFFFFFFF.toInt())

        tvTitle.text = when (target) {
            TestTarget.OCR -> "添加 OCR 模型"
            TestTarget.FAST -> "添加极速模型"
            TestTarget.DEEP -> "添加深度模型"
        }
        tvSubtitle.text = "保存后会追加到列表末尾，可长按排序"

        btnSaveModel.isEnabled = false
        btnSaveModel.alpha = 0.45f

        addCompactInputWatcher(etModelId) { compact ->
            val hasText = compact.isNotBlank()
            btnSaveModel.isEnabled = hasText
            btnSaveModel.alpha = if (hasText) 1f else 0.45f
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSaveModel.setOnClickListener {
            val modelId = sanitizeEditTextInPlace(etModelId)
            if (modelId.isBlank()) return@setOnClickListener
            val added = appendModelToTarget(target, modelId)
            if (!added) return@setOnClickListener
            dialog.dismiss()
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
        etModelId.requestFocus()
        etModelId.post {
            val inputManager = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            inputManager?.showSoftInput(etModelId, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
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

        // Save API Key
        AISettings.saveApiKey(this, apiKey)
        AISettings.saveBaseUrl(this, baseUrl)

        val ocrModels = normalizeModelIds(
            collectModelIds(ocrModelListContainer),
            AISettings.getSelectedOCRModel(this).ifBlank { DEFAULT_OCR_MODEL }
        )

        val fastModels = normalizeModelIds(
            collectModelIds(fastModelListContainer),
            AISettings.getSelectedFastModel(this).ifBlank { DEFAULT_FAST_MODEL }
        )
        val deepModels = normalizeModelIds(
            collectModelIds(deepModelListContainer),
            AISettings.getSelectedDeepModel(this).ifBlank { DEFAULT_DEEP_MODEL }
        )

        AISettings.saveOCRModelList(this, ocrModels)
        AISettings.saveFastModelList(this, fastModels)
        AISettings.saveDeepModelList(this, deepModels)

        val selectedFast = AISettings.getSelectedFastModel(this).takeIf { fastModels.contains(it) } ?: fastModels.first()
        val selectedDeep = AISettings.getSelectedDeepModel(this).takeIf { deepModels.contains(it) } ?: deepModels.first()

        AISettings.setSelectedFastModel(this, selectedFast)
        AISettings.setSelectedDeepModel(this, selectedDeep)
    }

    private fun fetchModelCatalog(target: TestTarget) {
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

        val fallbackModelId = when (target) {
            TestTarget.OCR -> normalizeModelIds(
                collectModelIds(ocrModelListContainer),
                AISettings.getSelectedOCRModel(this).ifBlank { DEFAULT_OCR_MODEL }
            ).first()
            TestTarget.FAST -> normalizeModelIds(
                collectModelIds(fastModelListContainer),
                AISettings.getSelectedFastModel(this).ifBlank { DEFAULT_FAST_MODEL }
            ).first()
            TestTarget.DEEP -> normalizeModelIds(
                collectModelIds(deepModelListContainer),
                AISettings.getSelectedDeepModel(this).ifBlank { DEFAULT_DEEP_MODEL }
            ).first()
        }

        val fetchButton = getFetchButton(target)
        fetchButton.isEnabled = false
        fetchButton.text = "获取中..."

        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = OpenAIClient(AIConfig(baseUrl, fallbackModelId, apiKey))
                    client.fetchModels()
                }

                withContext(Dispatchers.Main) {
                    if (!result.success) {
                        showConnectionResultDialog(
                            success = false,
                            responseBody = result.responseBody.ifBlank { "获取模型列表失败" }
                        )
                        return@withContext
                    }

                    val catalogModels = buildCatalogModels(result.models)
                    if (catalogModels.isEmpty()) {
                        showConnectionResultDialog(
                            success = false,
                            responseBody = "模型列表为空，或返回格式不受支持"
                        )
                        return@withContext
                    }

                    val targetModels = filterCatalogModelsForTarget(target, catalogModels)
                    if (targetModels.isEmpty()) {
                        val hint = when (target) {
                            TestTarget.OCR -> "未识别到视觉模型"
                            TestTarget.FAST -> "未识别到可用模型"
                            TestTarget.DEEP -> "未识别到推理模型"
                        }
                        showConnectionResultDialog(success = false, responseBody = hint)
                        return@withContext
                    }

                    showModelCatalogDialog(target, targetModels)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showConnectionResultDialog(
                        success = false,
                        responseBody = e.message?.ifBlank { "Unknown error" } ?: "Unknown error"
                    )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    fetchButton.isEnabled = true
                    fetchButton.text = "获取模型"
                }
            }
        }
    }

    private fun buildCatalogModels(rawModels: List<OpenAIClient.RemoteModel>): List<CatalogModel> {
        val normalizedModels = mutableListOf<CatalogModel>()
        val seen = HashSet<String>()

        rawModels.forEach { model ->
            val normalizedId = compactInput(model.id)
            if (normalizedId.isBlank() || seen.contains(normalizedId)) return@forEach

            val normalizedName = model.name.trim().ifBlank { normalizedId }
            val endpointTypes = model.supportedEndpointTypes.map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()

            val haystack = "${normalizedId.lowercase()} ${normalizedName.lowercase()}"
            val isVision = isVisionModelCandidate(haystack, endpointTypes)
            val isReasoning = isReasoningModelCandidate(haystack, endpointTypes)

            normalizedModels.add(
                CatalogModel(
                    id = normalizedId,
                    name = normalizedName,
                    ownedBy = model.ownedBy?.trim()?.ifBlank { null },
                    supportedEndpointTypes = endpointTypes,
                    isVision = isVision,
                    isReasoning = isReasoning
                )
            )
            seen.add(normalizedId)
        }

        return normalizedModels.sortedBy { it.id.lowercase() }
    }

    private fun filterCatalogModelsForTarget(
        target: TestTarget,
        models: List<CatalogModel>
    ): List<CatalogModel> {
        return when (target) {
            TestTarget.OCR -> models.filter { it.isVision }
            TestTarget.FAST -> models
            TestTarget.DEEP -> models.filter { it.isReasoning }
        }
    }

    private fun isVisionModelCandidate(
        modelText: String,
        supportedEndpointTypes: List<String>
    ): Boolean {
        if (NON_CHAT_MODEL_REGEX.containsMatchIn(modelText)) return false
        if (IMAGE_GENERATION_ONLY_REGEX.containsMatchIn(modelText) && !VISION_MODEL_REGEX.containsMatchIn(modelText)) {
            return false
        }
        if (supportedEndpointTypes.contains("image-generation") && !VISION_MODEL_REGEX.containsMatchIn(modelText)) {
            return false
        }
        return VISION_MODEL_REGEX.containsMatchIn(modelText)
    }

    private fun isReasoningModelCandidate(
        modelText: String,
        supportedEndpointTypes: List<String>
    ): Boolean {
        if (NON_CHAT_MODEL_REGEX.containsMatchIn(modelText)) return false
        if (IMAGE_GENERATION_ONLY_REGEX.containsMatchIn(modelText)) return false
        if (NON_REASONING_HINT_REGEX.containsMatchIn(modelText)) return false
        if (supportedEndpointTypes.contains("jina-rerank")) return false
        return REASONING_MODEL_REGEX.containsMatchIn(modelText)
    }

    private fun showModelCatalogDialog(
        target: TestTarget,
        models: List<CatalogModel>
    ) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        val dialog = Dialog(this, R.style.RoundedDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_model_catalog)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = dialog.findViewById<LinearLayout>(R.id.modelCatalogRoot)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvModelCatalogTitle)
        val tvSubtitle = dialog.findViewById<TextView>(R.id.tvModelCatalogSubtitle)
        val filterContainer = dialog.findViewById<LinearLayout>(R.id.modelCatalogFilterContainer)
        val prefixRow = dialog.findViewById<LinearLayout>(R.id.modelCatalogPrefixRow)
        val prefixContainer = dialog.findViewById<LinearLayout>(R.id.modelCatalogPrefixContainer)
        val modelCatalogScroll = dialog.findViewById<android.widget.ScrollView>(R.id.modelCatalogScroll)
        val optionsContainer = dialog.findViewById<LinearLayout>(R.id.modelCatalogOptionsContainer)
        val btnTogglePrefixSelection = dialog.findViewById<TextView>(R.id.btnTogglePrefixSelection)
        val btnToggleAllSelection = dialog.findViewById<TextView>(R.id.btnToggleAllSelection)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancelModelCatalog)
        val btnConfirm = dialog.findViewById<TextView>(R.id.btnConfirmModelCatalog)

        val primaryColor = if (isLightGreenGray) 0xFF10A37F.toInt() else 0xFFDA7A5A.toInt()
        val textPrimary = if (isLightGreenGray) 0xFF1D2A2F.toInt() else 0xFF2C241F.toInt()
        val textSecondary = if (isLightGreenGray) 0xFF5E6872.toInt() else 0xFF6F625B.toInt()

        root.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_model_dialog_surface
            else R.drawable.bg_model_dialog_surface_light_brown_black
        )
        tvTitle.setTextColor(textPrimary)
        tvSubtitle.setTextColor(textSecondary)
        btnCancel.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_button_outline
            else R.drawable.bg_button_outline_light_brown_black
        )
        btnCancel.setTextColor(primaryColor)
        btnConfirm.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_button_filled
            else R.drawable.bg_button_filled_light_brown_black
        )
        btnConfirm.setTextColor(0xFFFFFFFF.toInt())
        btnTogglePrefixSelection.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_button_outline
            else R.drawable.bg_button_outline_light_brown_black
        )
        btnTogglePrefixSelection.setTextColor(primaryColor)
        btnToggleAllSelection.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_button_outline
            else R.drawable.bg_button_outline_light_brown_black
        )
        btnToggleAllSelection.setTextColor(primaryColor)
        btnToggleAllSelection.contentDescription = "全部模型选择切换"
        btnTogglePrefixSelection.contentDescription = "前缀模型选择切换"

        tvTitle.text = when (target) {
            TestTarget.OCR -> "选择 OCR 模型"
            TestTarget.FAST -> "添加极速模型"
            TestTarget.DEEP -> "添加深度模型"
        }
        tvSubtitle.text = when (target) {
            TestTarget.OCR -> "支持手动多选，按顺序作为 OCR 备用模型"
            TestTarget.FAST -> "支持手动多选，可按类别/前缀筛选，底部固定确定"
            TestTarget.DEEP -> "支持手动多选，可按前缀筛选，底部固定确定"
        }
        btnConfirm.text = "确定"

        val categories = buildFilterCategories(target, models)
        var activeCategory = categories.first()
        val prefixCountMap = buildPrefixCountMap(models)
        val prefixKeys = prefixCountMap.keys.toList()
        var activePrefix: String? = null

        val existingIds = when (target) {
            TestTarget.OCR -> collectModelIds(ocrModelListContainer).toSet()
            TestTarget.FAST -> collectModelIds(fastModelListContainer).toSet()
            TestTarget.DEEP -> collectModelIds(deepModelListContainer).toSet()
        }
        val selectedIds = mutableSetOf<String>()
        selectedIds.addAll(models.map { it.id }.filter { existingIds.contains(it) })

        // Keep option list in a fixed-height scroll area so bottom actions stay anchored.
        val screenHeight = resources.displayMetrics.heightPixels
        val targetScrollHeight = minOf((screenHeight * 0.42f).toInt(), dp(320f).toInt())
        modelCatalogScroll.layoutParams = modelCatalogScroll.layoutParams.apply {
            height = targetScrollHeight
        }

        val categoryViews = mutableListOf<Pair<TextView, ModelFilterCategory>>()
        fun applyCategoryUi() {
            categoryViews.forEach { (view, category) ->
                val isSelected = category == activeCategory
                view.setTextColor(if (isSelected) primaryColor else textSecondary)
                view.setBackgroundResource(
                    when {
                        isSelected && isLightGreenGray -> R.drawable.bg_dialog_item_selected_light_green_gray
                        isSelected && !isLightGreenGray -> R.drawable.bg_dialog_item_selected_light_brown_black
                        !isSelected && isLightGreenGray -> R.drawable.bg_model_option_unselected
                        else -> R.drawable.bg_model_option_unselected_light_brown_black
                    }
                )
            }
        }

        fun filteredModelsByCategory(category: ModelFilterCategory): List<CatalogModel> {
            return when (category) {
                ModelFilterCategory.ALL -> models
                ModelFilterCategory.REASONING -> models.filter { it.isReasoning }
                ModelFilterCategory.VISION -> models.filter { it.isVision }
            }
        }

        fun currentCategoryModels(): List<CatalogModel> = filteredModelsByCategory(activeCategory)

        fun currentPrefixModels(): List<CatalogModel> {
            val prefix = activePrefix ?: return emptyList()
            return currentCategoryModels().filter { extractModelPrefixKey(it.id) == prefix }
        }

        fun filteredModelsByCurrentState(): List<CatalogModel> {
            val byCategory = currentCategoryModels()
            return if (activePrefix.isNullOrBlank()) {
                byCategory
            } else {
                byCategory.filter { extractModelPrefixKey(it.id) == activePrefix }
            }
        }

        fun updateConfirmStateAndBulkButtons() {
            btnToggleAllSelection.visibility = View.VISIBLE
            btnTogglePrefixSelection.visibility =
                if (prefixRow.visibility == View.VISIBLE) View.VISIBLE else View.GONE
            btnConfirm.isEnabled = true
            btnConfirm.alpha = 1f

            val categoryIds = currentCategoryModels().map { it.id }
            val prefixIds = currentPrefixModels().map { it.id }

            val allCategorySelected = categoryIds.isNotEmpty() && categoryIds.all { selectedIds.contains(it) }
            btnToggleAllSelection.text = if (allCategorySelected) "-" else "+"
            btnToggleAllSelection.isEnabled = categoryIds.isNotEmpty()
            btnToggleAllSelection.alpha = if (categoryIds.isNotEmpty()) 1f else 0.45f

            val canPrefixToggle = activePrefix != null && prefixIds.isNotEmpty()
            val allPrefixSelected = canPrefixToggle && prefixIds.all { selectedIds.contains(it) }
            btnTogglePrefixSelection.text = if (allPrefixSelected) "-" else "+"
            btnTogglePrefixSelection.isEnabled = canPrefixToggle
            btnTogglePrefixSelection.alpha = if (canPrefixToggle) 1f else 0.45f
        }

        var optionRows = mutableListOf<Triple<LinearLayout, ImageView, CatalogModel>>()

        fun applyOptionSelectionUi() {
            optionRows.forEach { (optionRoot, ivCheck, model) ->
                val isSelected = selectedIds.contains(model.id)
                ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
                optionRoot.setBackgroundResource(
                    when {
                        isSelected && isLightGreenGray -> R.drawable.bg_model_option_selected
                        isSelected && !isLightGreenGray -> R.drawable.bg_model_option_selected_light_brown_black
                        !isSelected && isLightGreenGray -> R.drawable.bg_model_option_unselected
                        else -> R.drawable.bg_model_option_unselected_light_brown_black
                    }
                )
            }
            updateConfirmStateAndBulkButtons()
        }

        fun renderOptions() {
            optionsContainer.removeAllViews()
            val visibleModels = filteredModelsByCurrentState()
            optionRows = mutableListOf()

            visibleModels.forEachIndexed { index, model ->
                val row = layoutInflater.inflate(R.layout.item_model_catalog_option, optionsContainer, false)
                val optionRoot = row.findViewById<LinearLayout>(R.id.modelCatalogOptionRoot)
                val tvModelName = row.findViewById<TextView>(R.id.tvModelCatalogName)
                val tvModelMeta = row.findViewById<TextView>(R.id.tvModelCatalogMeta)
                val ivCheck = row.findViewById<ImageView>(R.id.ivModelCatalogCheck)

                tvModelName.text = model.id
                tvModelName.setTextColor(textPrimary)
                tvModelMeta.setTextColor(textSecondary)
                ivCheck.setColorFilter(primaryColor)

                val tags = mutableListOf<String>()
                if (model.isReasoning) tags.add("推理")
                if (model.isVision) tags.add("视觉")
                if (model.supportedEndpointTypes.isNotEmpty()) {
                    tags.add(model.supportedEndpointTypes.joinToString("/"))
                }
                if (tags.isEmpty()) {
                    val owner = model.ownedBy?.trim().orEmpty()
                    tags.add(if (owner.isNotBlank()) owner else "OpenAI-Compatible")
                }
                tvModelMeta.text = tags.joinToString(" · ")

                optionRoot.setOnClickListener {
                    optionRoot.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .setDuration(70)
                        .withEndAction {
                            optionRoot.scaleX = 1f
                            optionRoot.scaleY = 1f
                            if (selectedIds.contains(model.id)) {
                                selectedIds.remove(model.id)
                            } else {
                                selectedIds.add(model.id)
                            }
                            applyOptionSelectionUi()
                        }
                        .start()
                }

                optionsContainer.addView(row)
                optionRows.add(Triple(optionRoot, ivCheck, model))
                optionRoot.alpha = 0f
                optionRoot.translationY = dp(6f)
                optionRoot.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(index * 28L)
                    .setDuration(180)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            applyOptionSelectionUi()
        }

        var prefixViews = mutableListOf<Pair<TextView, String?>>()
        fun applyPrefixUi() {
            prefixViews.forEach { (view, key) ->
                val isSelected = key == activePrefix
                view.setTextColor(if (isSelected) primaryColor else textSecondary)
                view.setBackgroundResource(
                    when {
                        isSelected && isLightGreenGray -> R.drawable.bg_dialog_item_selected_light_green_gray
                        isSelected && !isLightGreenGray -> R.drawable.bg_dialog_item_selected_light_brown_black
                        !isSelected && isLightGreenGray -> R.drawable.bg_model_option_unselected
                        else -> R.drawable.bg_model_option_unselected_light_brown_black
                    }
                )
            }
        }

        filterContainer.removeAllViews()
        categories.forEach { category ->
            val chip = TextView(this).apply {
                text = when (category) {
                    ModelFilterCategory.ALL -> "全部"
                    ModelFilterCategory.REASONING -> "推理"
                    ModelFilterCategory.VISION -> "视觉"
                }
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                val horizontalPadding = dp(12f).toInt()
                val verticalPadding = dp(7f).toInt()
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                setOnClickListener {
                    activeCategory = category
                    if (!activePrefix.isNullOrBlank() && currentPrefixModels().isEmpty()) {
                        activePrefix = null
                    }
                    applyCategoryUi()
                    applyPrefixUi()
                    renderOptions()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8f).toInt()
            }
            filterContainer.addView(chip, params)
            categoryViews.add(chip to category)
        }

        if (prefixKeys.isEmpty()) {
            prefixRow.visibility = View.GONE
        } else {
            prefixRow.visibility = View.VISIBLE
            fun addPrefixChip(key: String?) {
                val chip = TextView(this).apply {
                    text = if (key == null) {
                        "全部前缀"
                    } else {
                        "$key (${prefixCountMap[key] ?: 0})"
                    }
                    textSize = 12f
                    gravity = android.view.Gravity.CENTER
                    val horizontalPadding = dp(12f).toInt()
                    val verticalPadding = dp(7f).toInt()
                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                    setOnClickListener {
                        activePrefix = key
                        applyPrefixUi()
                        renderOptions()
                    }
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(8f).toInt()
                }
                prefixContainer.addView(chip, params)
                prefixViews.add(chip to key)
            }

            prefixContainer.removeAllViews()
            prefixViews = mutableListOf()
            addPrefixChip(null)
            prefixKeys.forEach { prefix ->
                addPrefixChip(prefix)
            }
            applyPrefixUi()
        }

        btnToggleAllSelection.setOnClickListener {
            val categoryIds = currentCategoryModels().map { it.id }
            if (categoryIds.isEmpty()) return@setOnClickListener
            val allSelected = categoryIds.all { selectedIds.contains(it) }
            if (allSelected) {
                categoryIds.forEach { selectedIds.remove(it) }
            } else {
                selectedIds.addAll(categoryIds)
            }
            applyOptionSelectionUi()
        }

        btnTogglePrefixSelection.setOnClickListener {
            val prefixIds = currentPrefixModels().map { it.id }
            if (prefixIds.isEmpty()) return@setOnClickListener
            val allPrefixSelected = prefixIds.all { selectedIds.contains(it) }
            if (allPrefixSelected) {
                prefixIds.forEach { selectedIds.remove(it) }
            } else {
                selectedIds.addAll(prefixIds)
            }
            applyOptionSelectionUi()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val orderedSelectedIds = models
                .map { it.id }
                .filter { selectedIds.contains(it) }
            val container = getModelRecycler(target)
            val fallback = when (target) {
                TestTarget.OCR -> DEFAULT_OCR_MODEL
                TestTarget.FAST -> DEFAULT_FAST_MODEL
                TestTarget.DEEP -> DEFAULT_DEEP_MODEL
            }
            val existing = collectModelIds(container)
            val catalogIds = models.map { it.id }.toHashSet()
            val keptExisting = existing.filter { modelId ->
                if (catalogIds.contains(modelId)) {
                    selectedIds.contains(modelId)
                } else {
                    true
                }
            }
            val appendIds = orderedSelectedIds.filterNot { keptExisting.contains(it) }
            replaceModelsInContainer(
                container = container,
                nextModels = keptExisting + appendIds,
                fallback = fallback
            )
            dialog.dismiss()
        }

        applyCategoryUi()
        renderOptions()
        updateConfirmStateAndBulkButtons()
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

    private fun buildFilterCategories(
        target: TestTarget,
        models: List<CatalogModel>
    ): List<ModelFilterCategory> {
        return when (target) {
            TestTarget.OCR -> listOf(ModelFilterCategory.VISION)
            TestTarget.DEEP -> listOf(ModelFilterCategory.REASONING)
            TestTarget.FAST -> {
                val categories = mutableListOf(ModelFilterCategory.ALL)
                if (models.any { it.isReasoning }) categories.add(ModelFilterCategory.REASONING)
                if (models.any { it.isVision }) categories.add(ModelFilterCategory.VISION)
                categories
            }
        }
    }

    private fun buildPrefixCountMap(models: List<CatalogModel>): LinkedHashMap<String, Int> {
        val counts = HashMap<String, Int>()
        models.forEach { model ->
            val prefix = extractModelPrefixKey(model.id)
            if (prefix.isBlank()) return@forEach
            counts[prefix] = (counts[prefix] ?: 0) + 1
        }

        val sorted = counts.entries
            .sortedBy { it.key.lowercase() }

        val result = linkedMapOf<String, Int>()
        sorted.forEach { (key, value) ->
            result[key] = value
        }
        return result
    }

    private fun extractModelPrefixKey(modelId: String): String {
        val normalized = compactInput(modelId).lowercase()
        if (normalized.isBlank()) return ""

        val separatorIndex = normalized.indexOfFirst { ch ->
            ch == '-' || ch == '_' || ch == '/' || ch == ':'
        }
        return if (separatorIndex <= 0) normalized else normalized.substring(0, separatorIndex)
    }

    private fun replaceModelsInContainer(
        container: RecyclerView,
        nextModels: List<String>,
        fallback: String
    ) {
        setModelRows(container, nextModels, fallback)
        markVerificationDirty()
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

        if (modelCandidates.size == 1) {
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
        var testSucceeded = false

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
                        testSucceeded = true
                        testButton.text = "连接成功"
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
                    if (!testSucceeded) {
                        testButton.text = "检测"
                    }
                }
            }
        }
    }

    private fun getModelCandidatesForTest(target: TestTarget): List<String> {
        return when (target) {
            TestTarget.OCR -> normalizeModelIds(
                collectModelIds(ocrModelListContainer),
                AISettings.getSelectedOCRModel(this).ifBlank { DEFAULT_OCR_MODEL }
            )
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
        val optionsContainer = dialog.findViewById<LinearLayout>(R.id.modelPickerOptionsContainer)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancelModelPicker)
        val btnConfirm = dialog.findViewById<TextView>(R.id.btnConfirmModelPicker)

        val primaryColor = if (isLightGreenGray) 0xFF10A37F.toInt() else 0xFFDA7A5A.toInt()
        val textPrimary = if (isLightGreenGray) 0xFF1D2A2F.toInt() else 0xFF2C241F.toInt()
        val selectedModel = when (target) {
            TestTarget.FAST -> AISettings.getSelectedFastModel(this)
            TestTarget.DEEP -> AISettings.getSelectedDeepModel(this)
            TestTarget.OCR -> AISettings.getSelectedOCRModel(this)
        }
        var pendingSelectedModel = selectedModel.takeIf { models.contains(it) } ?: models.first()

        root.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_model_dialog_surface
            else R.drawable.bg_model_dialog_surface_light_brown_black
        )
        tvTitle.text = when (target) {
            TestTarget.FAST -> "选择极速模式检测模型"
            TestTarget.DEEP -> "选择模型"
            TestTarget.OCR -> "选择检测模型"
        }
        tvTitle.setTextColor(textPrimary)
        btnCancel.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_button_outline
            else R.drawable.bg_button_outline_light_brown_black
        )
        btnCancel.setTextColor(primaryColor)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_button_filled
            else R.drawable.bg_button_filled_light_brown_black
        )
        btnConfirm.setTextColor(0xFFFFFFFF.toInt())
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onModelSelected(pendingSelectedModel)
        }

        optionsContainer.removeAllViews()
        val optionRows = mutableListOf<Triple<LinearLayout, ImageView, String>>()

        fun applySelectionUi() {
            optionRows.forEach { (optionRoot, ivCheck, modelId) ->
                val isSelected = modelId == pendingSelectedModel
                ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
                optionRoot.setBackgroundResource(
                    when {
                        isSelected && isLightGreenGray -> R.drawable.bg_model_option_selected
                        isSelected && !isLightGreenGray -> R.drawable.bg_model_option_selected_light_brown_black
                        !isSelected && isLightGreenGray -> R.drawable.bg_model_option_unselected
                        else -> R.drawable.bg_model_option_unselected_light_brown_black
                    }
                )
            }
        }

        models.forEachIndexed { index, model ->
            val row = layoutInflater.inflate(R.layout.item_model_picker_option, optionsContainer, false)
            val optionRoot = row.findViewById<LinearLayout>(R.id.modelPickerOptionRoot)
            val tvModelName = row.findViewById<TextView>(R.id.tvModelPickerName)
            val ivCheck = row.findViewById<ImageView>(R.id.ivModelPickerCheck)

            tvModelName.text = model
            tvModelName.setTextColor(textPrimary)
            ivCheck.setColorFilter(primaryColor)

            optionRoot.setOnClickListener {
                pendingSelectedModel = model
                applySelectionUi()
                optionRoot.animate()
                    .scaleX(0.98f)
                    .scaleY(0.98f)
                    .setDuration(70)
                    .withEndAction {
                        optionRoot.scaleX = 1f
                        optionRoot.scaleY = 1f
                    }
                    .start()
            }

            optionsContainer.addView(row)
            optionRows.add(Triple(optionRoot, ivCheck, model))
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
        applySelectionUi()

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

    private fun getFetchButton(target: TestTarget): TextView {
        return when (target) {
            TestTarget.OCR -> btnFetchOcrModels
            TestTarget.FAST -> btnFetchFastModels
            TestTarget.DEEP -> btnFetchDeepModels
        }
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
            tvResultMessage.text = ""
            tvResultMessage.visibility = View.INVISIBLE
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
        applyResultDialogWindowSize(dialog)

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

    private fun applyResultDialogWindowSize(dialog: Dialog) {
        val screenWidth = resources.displayMetrics.widthPixels
        val maxWidth = dp(348f).toInt()
        val targetWidth = minOf((screenWidth * 0.9f).toInt(), maxWidth)
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
    }

    private fun markVerificationDirty() {
        resetTestStatusLabels()
        if (!isApiVerified) return
        isApiVerified = false
        updateSaveButtonState()
    }

    private fun resetTestStatusLabels() {
        if (::btnTestOcr.isInitialized) btnTestOcr.text = "检测"
        if (::btnTestFast.isInitialized) btnTestFast.text = "检测"
        if (::btnTestDeep.isInitialized) btnTestDeep.text = "检测"
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

    private inner class ModelListAdapter(
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
        private val onRemoveRequest: (ModelListAdapter, Int) -> Boolean
    ) : RecyclerView.Adapter<ModelListAdapter.ModelViewHolder>() {

        private val items = mutableListOf<String>()
        private var palette = ModelRowPalette(
            textPrimary = 0xFF202123.toInt(),
            textSecondary = 0xFF6E6E80.toInt(),
            rowBackgroundRes = R.drawable.bg_model_option_unselected,
            actionButtonBackgroundRes = R.drawable.bg_button_outline
        )

        inner class ModelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val rowRoot = view.findViewById<LinearLayout>(R.id.modelRowRoot)
            private val tvModelId = view.findViewById<TextView>(R.id.tvModelId)
            private val ivDragHandle = view.findViewById<ImageView>(R.id.ivDragHandle)
            private val btnRemoveModel = view.findViewById<ImageView>(R.id.btnRemoveModel)

            init {
                rowRoot.setOnLongClickListener {
                    if (bindingAdapterPosition == RecyclerView.NO_POSITION) {
                        return@setOnLongClickListener false
                    }
                    onStartDrag(this)
                    true
                }
                btnRemoveModel.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                    onRemoveRequest(this@ModelListAdapter, position)
                }
            }

            fun bind(modelId: String) {
                // Defensive reset to avoid recycled stale animation state after drag.
                itemView.alpha = 1f
                itemView.translationX = 0f
                itemView.translationY = 0f
                itemView.scaleX = 1f
                itemView.scaleY = 1f
                itemView.translationZ = 0f
                tvModelId.text = modelId
                tvModelId.setTextColor(palette.textPrimary)
                ivDragHandle.setColorFilter(palette.textSecondary)
                btnRemoveModel.setColorFilter(palette.textSecondary)
                btnRemoveModel.setBackgroundResource(palette.actionButtonBackgroundRes)
                rowRoot.setBackgroundResource(palette.rowBackgroundRes)
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ModelViewHolder {
            val view = layoutInflater.inflate(R.layout.item_model_input, parent, false)
            return ModelViewHolder(view)
        }

        override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        fun setItems(nextItems: List<String>) {
            items.clear()
            items.addAll(nextItems)
            notifyDataSetChanged()
        }

        fun getItems(): List<String> = items.toList()

        fun appendItem(modelId: String): Boolean {
            val normalized = compactInput(modelId)
            if (normalized.isBlank() || items.contains(normalized)) return false
            items.add(normalized)
            // RecyclerView is embedded in a ScrollView and uses wrap_content height.
            // Full refresh avoids occasional partial-update desync causing invisible rows.
            notifyDataSetChanged()
            return true
        }

        fun removeAt(position: Int) {
            if (position !in items.indices) return
            items.removeAt(position)
            notifyDataSetChanged()
        }

        fun moveItem(from: Int, to: Int): Boolean {
            if (from !in items.indices || to !in items.indices || from == to) return false
            val moved = items.removeAt(from)
            items.add(to, moved)
            notifyItemMoved(from, to)
            return true
        }

        fun applyPalette(newPalette: ModelRowPalette) {
            palette = newPalette
            notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}

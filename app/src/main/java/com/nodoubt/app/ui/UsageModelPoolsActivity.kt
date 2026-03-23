package com.nodoubt.app.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nodoubt.app.R
import com.nodoubt.app.data.AIChannel
import com.nodoubt.app.data.AIChannelType
import com.nodoubt.app.data.AISettings
import com.nodoubt.app.data.AIUsage
import com.nodoubt.app.data.AIUsageModelEntry
import com.nodoubt.app.data.ResolvedUsageModelEntry
import com.nodoubt.app.data.ThemeManager
import com.nodoubt.app.network.AIProviderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsageModelPoolsActivity : AppCompatActivity() {

    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var ocrAdapter: UsageModelAdapter
    private lateinit var fastAdapter: UsageModelAdapter
    private lateinit var deepAdapter: UsageModelAdapter
    private var channelPickerPopup: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage_model_pools)

        initViews()
        bindUsagePools()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        bindUsagePools()
        applyTheme()
    }

    override fun onDestroy() {
        super.onDestroy()
        channelPickerPopup?.dismiss()
        channelPickerPopup = null
        job.cancel()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnAddOcrModel).setOnClickListener { showAddModelDialog(AIUsage.OCR) }
        findViewById<TextView>(R.id.btnAddFastModel).setOnClickListener { showAddModelDialog(AIUsage.FAST) }
        findViewById<TextView>(R.id.btnAddDeepModel).setOnClickListener { showAddModelDialog(AIUsage.DEEP) }
        findViewById<TextView>(R.id.btnFetchOcrModels).setOnClickListener { fetchRemoteModels(AIUsage.OCR) }
        findViewById<TextView>(R.id.btnFetchFastModels).setOnClickListener { fetchRemoteModels(AIUsage.FAST) }
        findViewById<TextView>(R.id.btnFetchDeepModels).setOnClickListener { fetchRemoteModels(AIUsage.DEEP) }

        ocrAdapter = setupRecycler(R.id.ocrModelListContainer, AIUsage.OCR)
        fastAdapter = setupRecycler(R.id.fastModelListContainer, AIUsage.FAST)
        deepAdapter = setupRecycler(R.id.deepModelListContainer, AIUsage.DEEP)
    }

    private fun setupRecycler(recyclerId: Int, usage: AIUsage): UsageModelAdapter {
        val recyclerView = findViewById<RecyclerView>(recyclerId)
        lateinit var itemTouchHelper: ItemTouchHelper
        val adapter = UsageModelAdapter(
            onDragStart = { holder -> itemTouchHelper.startDrag(holder) },
            onSelect = { entry -> AISettings.setSelectedUsageModel(this, usage, entry); bindUsagePools() },
            onRemove = { entry ->
                AISettings.removeUsageModelEntry(this, usage, entry)
                bindUsagePools()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
        recyclerView.setHasFixedSize(false)
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
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
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                return adapter.moveItem(from, to)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                AISettings.setUsageModelEntries(
                    this@UsageModelPoolsActivity,
                    usage,
                    adapter.currentEntries(),
                    adapter.currentSelectedEntry()
                )
                bindUsagePools()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
        return adapter
    }

    private fun bindUsagePools() {
        findViewById<TextView>(R.id.tvHeaderTitle).text = "共享模型池"
        bindUsageCard(AIUsage.OCR, ocrAdapter)
        bindUsageCard(AIUsage.FAST, fastAdapter)
        bindUsageCard(AIUsage.DEEP, deepAdapter)
    }

    private fun bindUsageCard(usage: AIUsage, adapter: UsageModelAdapter) {
        val resolved = AISettings.getResolvedUsageModels(this, usage)
        adapter.submit(resolved)

        when (usage) {
            AIUsage.OCR -> {
                findViewById<TextView>(R.id.tvOcrLabel).text = usage.displayName
                findViewById<TextView>(R.id.tvOcrHint).text = usage.shortDescription
            }

            AIUsage.FAST -> {
                findViewById<TextView>(R.id.tvFastLabel).text = usage.displayName
                findViewById<TextView>(R.id.tvFastHint).text = usage.shortDescription
            }

            AIUsage.DEEP -> {
                findViewById<TextView>(R.id.tvDeepLabel).text = usage.displayName
                findViewById<TextView>(R.id.tvDeepHint).text = usage.shortDescription
            }
        }
    }

    private fun showAddModelDialog(usage: AIUsage) {
        val channels = AISettings.getChannels(AIChannelType.OPENAI_CHAT_COMPLETIONS, this)
        if (channels.isEmpty()) {
            Toast.makeText(this, "请先添加渠道", Toast.LENGTH_SHORT).show()
            return
        }
        val configuredChannels = channels.filter { it.isConfigured() }
        if (configuredChannels.isEmpty()) {
            Toast.makeText(this, "请先配置一个可用渠道", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this, R.style.RoundedDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_model)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = dialog.findViewById<LinearLayout>(R.id.addModelRoot)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvAddModelTitle)
        val tvSubtitle = dialog.findViewById<TextView>(R.id.tvAddModelSubtitle)
        val etInput = dialog.findViewById<EditText>(R.id.etAddModelInput)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancelAddModel)
        val btnSave = dialog.findViewById<TextView>(R.id.btnSaveAddModel)

        tvTitle.text = "添加 ${usage.displayName} 模型"
        tvSubtitle.text = "输入模型 ID 后选择要绑定的渠道。"
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val modelId = etInput.text?.toString().orEmpty().trim()
            if (modelId.isBlank()) {
                Toast.makeText(this, "请输入模型 ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            showChannelPicker(usage, modelId, configuredChannels)
        }

        applyDialogTheme(root, etInput, btnCancel, btnSave)
        dialog.show()
        dialog.window?.setLayout(minOf((resources.displayMetrics.widthPixels * 0.88f).toInt(), dp(340f).toInt()), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun fetchRemoteModels(usage: AIUsage) {
        val selectedChannel = AISettings.getChannels(AIChannelType.OPENAI_CHAT_COMPLETIONS, this)
            .firstOrNull { it.isConfigured() }
            ?: AISettings.getSelectedChannel(this)
        if (!selectedChannel.isConfigured()) {
            Toast.makeText(this, "请先配置一个可用渠道", Toast.LENGTH_SHORT).show()
            return
        }

        AISettings.setSelectedChannelId(this, selectedChannel.id)

        val provider = AIProviderRegistry.getProvider(selectedChannel)
        coroutineScope.launch {
            Toast.makeText(this@UsageModelPoolsActivity, "正在获取模型", Toast.LENGTH_SHORT).show()
            val result = withContext(Dispatchers.IO) {
                provider.createClient(selectedChannel.toConfig("gpt-4o-mini")).fetchModels()
            }
            if (!result.success) {
                Toast.makeText(
                    this@UsageModelPoolsActivity,
                    result.responseBody.ifBlank { "获取模型失败" },
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val modelIds = result.models.map { it.id }.distinct()
            if (modelIds.isEmpty()) {
                Toast.makeText(this@UsageModelPoolsActivity, "未获取到模型", Toast.LENGTH_SHORT).show()
                return@launch
            }
            showModelCatalogDialog(usage, selectedChannel.id, modelIds)
        }
    }

    private fun showChannelPicker(
        usage: AIUsage,
        modelId: String,
        channels: List<AIChannel>
    ) {
        channelPickerPopup?.dismiss()
        val popupContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt())
            setBackgroundResource(
                if (ThemeManager.isLightGreenGrayTheme(this@UsageModelPoolsActivity)) {
                    R.drawable.bg_model_menu_surface
                } else {
                    R.drawable.bg_model_menu_surface_light_brown_black
                }
            )
        }

        val title = TextView(this).apply {
            text = "选择渠道"
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (ThemeManager.isLightGreenGrayTheme(this@UsageModelPoolsActivity)) 0xFF202123.toInt() else 0xFF141413.toInt())
        }
        popupContent.addView(title)

        channels.forEachIndexed { index, channel ->
            val row = layoutInflater.inflate(R.layout.item_model_switch_option, popupContent, false)
            val root = row.findViewById<LinearLayout>(R.id.modelOptionRoot)
            val tvName = row.findViewById<TextView>(R.id.tvModelName)
            val tvDesc = row.findViewById<TextView>(R.id.tvModelDesc)
            val ivCheck = row.findViewById<ImageView>(R.id.ivModelCheck)
            tvName.text = channel.name
            tvDesc.text = channel.baseUrl
            ivCheck.visibility = View.GONE
            root.setOnClickListener {
                AISettings.addUsageModelEntry(
                    this,
                    usage,
                    AIUsageModelEntry(channelId = channel.id, modelId = modelId),
                    selectAfterAdd = false
                )
                bindUsagePools()
                channelPickerPopup?.dismiss()
                Toast.makeText(this, "已添加到 ${channel.name}", Toast.LENGTH_SHORT).show()
            }
            popupContent.addView(row)
            if (index < channels.lastIndex) {
                val spacer = View(this)
                spacer.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(6f).toInt()
                )
                popupContent.addView(spacer)
            }
        }

        channelPickerPopup = PopupWindow(
            popupContent,
            minOf((resources.displayMetrics.widthPixels * 0.82f).toInt(), dp(320f).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            showAtLocation(findViewById(android.R.id.content), android.view.Gravity.CENTER, 0, 0)
            setOnDismissListener { channelPickerPopup = null }
        }
    }

    private fun showModelCatalogDialog(usage: AIUsage, channelId: String, models: List<String>) {
        val dialog = Dialog(this, R.style.RoundedDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_model_catalog)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = dialog.findViewById<LinearLayout>(R.id.modelCatalogRoot)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvModelCatalogTitle)
        val tvSubtitle = dialog.findViewById<TextView>(R.id.tvModelCatalogSubtitle)
        val optionsContainer = dialog.findViewById<LinearLayout>(R.id.modelCatalogOptionsContainer)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancelModelCatalog)
        val btnConfirm = dialog.findViewById<TextView>(R.id.btnConfirmModelCatalog)
        val prefixRow = dialog.findViewById<LinearLayout>(R.id.modelCatalogPrefixRow)
        val filterRow = dialog.findViewById<LinearLayout>(R.id.modelCatalogFilterRow)

        tvTitle.text = "获取 ${usage.displayName} 模型"
        tvSubtitle.text = "从 OpenAI Chat Completions /models 添加到共享模型池。"
        prefixRow.visibility = View.GONE
        filterRow.visibility = View.GONE

        val selectedIds = LinkedHashSet<String>()
        models.forEach { modelId ->
            val row = layoutInflater.inflate(R.layout.item_model_catalog_option, optionsContainer, false)
            val rootView = row.findViewById<LinearLayout>(R.id.modelCatalogOptionRoot)
            val tvName = row.findViewById<TextView>(R.id.tvModelCatalogName)
            val tvMeta = row.findViewById<TextView>(R.id.tvModelCatalogMeta)
            val ivCheck = row.findViewById<ImageView>(R.id.ivModelCatalogCheck)

            tvName.text = modelId
            tvMeta.text = "添加后以“模型 · 渠道”形式展示"
            rootView.setOnClickListener {
                if (selectedIds.contains(modelId)) {
                    selectedIds.remove(modelId)
                } else {
                    selectedIds.add(modelId)
                }
                ivCheck.visibility = if (selectedIds.contains(modelId)) View.VISIBLE else View.GONE
            }
            optionsContainer.addView(row)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "请至少选择一个模型", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedIds.forEach { modelId ->
                AISettings.addUsageModelEntry(
                    this,
                    usage,
                    AIUsageModelEntry(channelId = channelId, modelId = modelId),
                    selectAfterAdd = false
                )
            }
            bindUsagePools()
            dialog.dismiss()
        }

        applyCatalogDialogTheme(root, btnCancel, btnConfirm)
        dialog.show()
        dialog.window?.setLayout(minOf((resources.displayMetrics.widthPixels * 0.9f).toInt(), dp(360f).toInt()), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun applyDialogTheme(
        root: LinearLayout,
        input: EditText,
        btnCancel: TextView,
        btnSave: TextView
    ) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        if (isLightGreenGray) {
            root.setBackgroundResource(R.drawable.bg_model_dialog_surface)
            input.setBackgroundResource(R.drawable.bg_edittext_settings)
            btnCancel.setBackgroundResource(R.drawable.bg_button_outline)
            btnCancel.setTextColor(0xFF10A37F.toInt())
            btnSave.setBackgroundResource(R.drawable.bg_button_filled)
        } else {
            root.setBackgroundResource(R.drawable.bg_model_dialog_surface_light_brown_black)
            input.setBackgroundResource(R.drawable.bg_edittext_settings_light_brown_black)
            btnCancel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnCancel.setTextColor(0xFFDA7A5A.toInt())
            btnSave.setBackgroundResource(R.drawable.bg_button_filled_light_brown_black)
        }
        btnSave.setTextColor(0xFFFFFFFF.toInt())
        input.inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun applyCatalogDialogTheme(
        root: LinearLayout,
        btnCancel: TextView,
        btnConfirm: TextView
    ) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        if (isLightGreenGray) {
            root.setBackgroundResource(R.drawable.bg_model_dialog_surface)
            btnCancel.setBackgroundResource(R.drawable.bg_button_outline)
            btnCancel.setTextColor(0xFF10A37F.toInt())
            btnConfirm.setBackgroundResource(R.drawable.bg_button_filled)
        } else {
            root.setBackgroundResource(R.drawable.bg_model_dialog_surface_light_brown_black)
            btnCancel.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
            btnCancel.setTextColor(0xFFDA7A5A.toInt())
            btnConfirm.setBackgroundResource(R.drawable.bg_button_filled_light_brown_black)
        }
        btnConfirm.setTextColor(0xFFFFFFFF.toInt())
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun applyTheme() {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        val headerLayout = findViewById<FrameLayout>(R.id.headerLayout)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val cards = listOf(
            findViewById<LinearLayout>(R.id.cardOcr),
            findViewById<LinearLayout>(R.id.cardFast),
            findViewById<LinearLayout>(R.id.cardDeep)
        )

        if (isLightGreenGray) {
            val surfaceColor = 0xFFF7F7F8.toInt()
            val textPrimary = 0xFF202123.toInt()
            val textSecondary = 0xFF6E6E80.toInt()

            window.statusBarColor = surfaceColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(surfaceColor)
            headerLayout.setBackgroundColor(surfaceColor)
            cards.forEach { it.setBackgroundResource(R.drawable.bg_card_settings) }
            btnBack.setColorFilter(textPrimary)
            listOf(
                findViewById<TextView>(R.id.tvHeaderTitle),
                findViewById<TextView>(R.id.tvOcrLabel),
                findViewById<TextView>(R.id.tvFastLabel),
                findViewById<TextView>(R.id.tvDeepLabel)
            ).forEach { it.setTextColor(textPrimary) }
            listOf(
                findViewById<TextView>(R.id.tvOcrHint),
                findViewById<TextView>(R.id.tvFastHint),
                findViewById<TextView>(R.id.tvDeepHint)
            ).forEach { it.setTextColor(textSecondary) }
            styleActionButtons(true)
        } else {
            val backgroundColor = 0xFFFAF9F5.toInt()
            val textPrimary = 0xFF141413.toInt()
            val textSecondary = 0xFF666666.toInt()

            window.statusBarColor = backgroundColor
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            rootLayout.setBackgroundColor(backgroundColor)
            headerLayout.setBackgroundColor(backgroundColor)
            cards.forEach { it.setBackgroundResource(R.drawable.bg_card_settings_light_brown_black) }
            btnBack.setColorFilter(textPrimary)
            listOf(
                findViewById<TextView>(R.id.tvHeaderTitle),
                findViewById<TextView>(R.id.tvOcrLabel),
                findViewById<TextView>(R.id.tvFastLabel),
                findViewById<TextView>(R.id.tvDeepLabel)
            ).forEach { it.setTextColor(textPrimary) }
            listOf(
                findViewById<TextView>(R.id.tvOcrHint),
                findViewById<TextView>(R.id.tvFastHint),
                findViewById<TextView>(R.id.tvDeepHint)
            ).forEach { it.setTextColor(textSecondary) }
            styleActionButtons(false)
        }

        ocrAdapter.applyTheme(isLightGreenGray)
        fastAdapter.applyTheme(isLightGreenGray)
        deepAdapter.applyTheme(isLightGreenGray)
    }

    private fun styleActionButtons(isLightGreenGray: Boolean) {
        val buttons = listOf(
            findViewById<TextView>(R.id.btnAddOcrModel),
            findViewById<TextView>(R.id.btnAddFastModel),
            findViewById<TextView>(R.id.btnAddDeepModel),
            findViewById<TextView>(R.id.btnFetchOcrModels),
            findViewById<TextView>(R.id.btnFetchFastModels),
            findViewById<TextView>(R.id.btnFetchDeepModels)
        )
        buttons.forEach { button ->
            if (isLightGreenGray) {
                button.setBackgroundResource(R.drawable.bg_button_outline)
                button.setTextColor(0xFF10A37F.toInt())
            } else {
                button.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
                button.setTextColor(0xFFDA7A5A.toInt())
            }
        }
    }

    private inner class UsageModelAdapter(
        private val onDragStart: (RecyclerView.ViewHolder) -> Unit,
        private val onSelect: (AIUsageModelEntry) -> Unit,
        private val onRemove: (AIUsageModelEntry) -> Unit
    ) : RecyclerView.Adapter<UsageModelAdapter.Holder>() {

        private val items = mutableListOf<ResolvedUsageModelEntry>()
        private var isLightTheme = true

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val root = view.findViewById<LinearLayout>(R.id.modelRowRoot)
            private val tvModelId = view.findViewById<TextView>(R.id.tvModelId)
            private val ivDragHandle = view.findViewById<ImageView>(R.id.ivDragHandle)
            private val btnRemove = view.findViewById<ImageView>(R.id.btnRemoveModel)

            init {
                root.setOnLongClickListener {
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        onDragStart(this)
                        true
                    } else {
                        false
                    }
                }
                root.setOnClickListener {
                    val item = items.getOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                    onSelect(item.entry)
                }
                btnRemove.setOnClickListener {
                    val item = items.getOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                    onRemove(item.entry)
                }
            }

            fun bind(item: ResolvedUsageModelEntry) {
                tvModelId.text = item.displayTitle()
                if (isLightTheme) {
                    root.setBackgroundResource(
                        if (item.isSelected) R.drawable.bg_model_option_selected else R.drawable.bg_model_option_unselected
                    )
                    tvModelId.setTextColor(0xFF202123.toInt())
                    ivDragHandle.setColorFilter(0xFF6E6E80.toInt())
                    btnRemove.setBackgroundResource(R.drawable.bg_button_outline)
                    btnRemove.setColorFilter(0xFF6E6E80.toInt())
                } else {
                    root.setBackgroundResource(
                        if (item.isSelected) R.drawable.bg_model_option_selected_light_brown_black
                        else R.drawable.bg_model_option_unselected_light_brown_black
                    )
                    tvModelId.setTextColor(0xFF141413.toInt())
                    ivDragHandle.setColorFilter(0xFF666666.toInt())
                    btnRemove.setBackgroundResource(R.drawable.bg_button_outline_light_brown_black)
                    btnRemove.setColorFilter(0xFF666666.toInt())
                }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
            return Holder(layoutInflater.inflate(R.layout.item_model_input, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        fun submit(next: List<ResolvedUsageModelEntry>) {
            items.clear()
            items.addAll(next)
            notifyDataSetChanged()
        }

        fun moveItem(from: Int, to: Int): Boolean {
            if (from !in items.indices || to !in items.indices || from == to) return false
            val moved = items.removeAt(from)
            items.add(to, moved)
            notifyItemMoved(from, to)
            return true
        }

        fun currentEntries(): List<AIUsageModelEntry> {
            return items.map { it.entry }
        }

        fun currentSelectedEntry(): AIUsageModelEntry? {
            return items.firstOrNull { it.isSelected }?.entry ?: items.firstOrNull()?.entry
        }

        fun applyTheme(isLightGreenGray: Boolean) {
            isLightTheme = isLightGreenGray
            notifyDataSetChanged()
        }
    }
}

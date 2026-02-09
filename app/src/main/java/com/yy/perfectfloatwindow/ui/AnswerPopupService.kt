package com.yy.perfectfloatwindow.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.yy.perfectfloatwindow.R
import com.yy.perfectfloatwindow.data.AIConfig
import com.yy.perfectfloatwindow.data.AISettings
import com.yy.perfectfloatwindow.data.Answer
import com.yy.perfectfloatwindow.data.Question
import com.yy.perfectfloatwindow.data.ThemeManager
import com.yy.perfectfloatwindow.network.ChatAPI
import com.yy.perfectfloatwindow.network.OCRStreamingCallback
import com.yy.perfectfloatwindow.network.StreamingCallback
import com.yy.perfectfloatwindow.network.VisionAPI
import com.yy.perfectfloatwindow.screenshot.ScreenshotService
import com.yy.perfectfloatwindow.utils.MarkdownRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Call

class AnswerPopupService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var popupView: View? = null
    private lateinit var popupParams: WindowManager.LayoutParams
    private lateinit var overlayParams: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)

    private var currentBitmap: Bitmap? = null
    private var currentQuestions: List<Question> = emptyList()

    // Cache answers for both modes
    private var fastAnswers: MutableMap<Int, Answer> = mutableMapOf()
    private var deepAnswers: MutableMap<Int, Answer> = mutableMapOf()
    private var fastAnswerViews: MutableMap<Int, View> = mutableMapOf()
    private var deepAnswerViews: MutableMap<Int, View> = mutableMapOf()

    // Track accumulated question text for streaming OCR
    private var questionTexts: MutableMap<Int, StringBuilder> = mutableMapOf()

    private var isFastMode = true
    private var isPopupShowing = false
    private var isFastSolving = false
    private var isDeepSolving = false

    private var initialTouchY = 0f
    private var initialHeight = 0
    private var screenHeight = 0
    private var isDismissing = false
    private var initialPopupHeight = 0

    // For swipe gesture
    private var gestureDetector: GestureDetector? = null

    // For edge back gesture detection
    private var edgeSwipeStartX = 0f
    private var edgeSwipeStartY = 0f
    private val EDGE_THRESHOLD = 50 // pixels from edge to detect back gesture

    // For tab animation
    private var tabIndicator: View? = null
    private var tabIndicatorWidth = 0
    private val TAB_ANIM_DURATION = 250L

    // For header status
    private var hasStartedAnswering = false
    private var isAllAnswersComplete = false
    private var currentActionIconRes = R.drawable.ic_stop_white  // Track current icon to avoid redundant animations

    // For tracking API calls to support cancellation
    private var ocrCall: Call? = null
    private var fastCalls: MutableMap<Int, Call> = mutableMapOf()
    private var deepCalls: MutableMap<Int, Call> = mutableMapOf()
    private var isFastModeStopped = false
    private var isDeepModeStopped = false
    private var selectedFastModelId: String = ""
    private var selectedDeepModelId: String = ""
    private var fastQuestionModelIds: MutableMap<Int, String> = mutableMapOf()
    private var deepQuestionModelIds: MutableMap<Int, String> = mutableMapOf()
    private var modelMenuPopup: PopupWindow? = null
    private var copyMenuPopup: PopupWindow? = null
    private var fastModeScrollY: Int = 0
    private var deepModeScrollY: Int = 0
    private var scrollRestoreToken: Int = 0
    private var isApplyingScrollRestore: Boolean = false

    companion object {
        private const val CHANNEL_ID = "answer_popup_channel"
        private const val NOTIFICATION_ID = 2001

        private var pendingBitmap: Bitmap? = null
        private var isServiceRunning = false

        fun show(context: Context, bitmap: Bitmap) {
            pendingBitmap = bitmap
            val intent = Intent(context, AnswerPopupService::class.java)
            intent.putExtra("action", "show")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, AnswerPopupService::class.java)
            intent.putExtra("action", "dismiss")
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        screenHeight = resources.displayMetrics.heightPixels
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        when (intent?.getStringExtra("action")) {
            "show" -> {
                if (!isPopupShowing) {
                    setupViews()
                    showWithAnimation()
                }
                pendingBitmap?.let {
                    processBitmap(it)
                    pendingBitmap = null
                }
            }
            "dismiss" -> {
                dismissWithAnimation()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Answer Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows AI answer popup"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("AI Question Solver")
        .setContentText("Processing your question...")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun setupViews() {
        if (isPopupShowing) return

        try {
            // Create overlay (semi-transparent background)
            overlayView = FrameLayout(this).apply {
                setBackgroundColor(0x80000000.toInt())
                // Click handler will be set in setupBackGesture
            }

            overlayParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
            }

            // Create popup
            popupView = LayoutInflater.from(this).inflate(R.layout.layout_answer_popup, null)

            initialPopupHeight = (screenHeight * 2 / 3)

            popupParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                gravity = Gravity.BOTTOM
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = initialPopupHeight
                y = 0 // Final position at bottom
            }

            // Start with popup translated off screen
            popupView?.translationY = initialPopupHeight.toFloat()

            windowManager.addView(overlayView, overlayParams)
            windowManager.addView(popupView, popupParams)
            isPopupShowing = true
            isDismissing = false

            setupDragHandle()
            setupTabs()
            setupRetakeButton()
            setupActionButton()
            setupSwipeGesture()
            setupScrollTracking()
            setupBackGesture()
            applyPopupTheme()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to show popup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showWithAnimation() {
        // Fade in overlay
        overlayView?.alpha = 0f
        overlayView?.animate()?.alpha(1f)?.setDuration(250)?.start()

        // Slide up popup using view translation (smoother than WindowManager params)
        popupView?.animate()
            ?.translationY(0f)
            ?.setDuration(300)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
    }

    private fun dismissWithAnimation() {
        if (isDismissing || !isPopupShowing) return
        isDismissing = true

        // Fade out overlay
        overlayView?.animate()?.alpha(0f)?.setDuration(200)?.start()

        // Slide down popup using view translation
        val targetTranslation = popupParams.height.toFloat()
        popupView?.animate()
            ?.translationY(targetTranslation)
            ?.setDuration(250)
            ?.setInterpolator(DecelerateInterpolator())
            ?.withEndAction {
                dismissPopup()
            }
            ?.start()
    }

    private fun setupDragHandle() {
        val view = popupView ?: return
        val dragHandle = view.findViewById<View>(R.id.dragHandle)
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchY = event.rawY
                    initialHeight = popupParams.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialTouchY - event.rawY
                    val newHeight = (initialHeight + deltaY).toInt()
                    val minHeight = screenHeight / 3
                    val maxHeight = (screenHeight * 0.9).toInt()

                    popupParams.height = newHeight.coerceIn(minHeight, maxHeight)
                    popupParams.y = 0
                    try {
                        windowManager.updateViewLayout(popupView, popupParams)
                    } catch (e: Exception) {
                        // View might be detached
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTabs() {
        val view = popupView ?: return
        val tabFast = view.findViewById<TextView>(R.id.tabFast)
        val tabDeep = view.findViewById<TextView>(R.id.tabDeep)
        val tabContainer = view.findViewById<FrameLayout>(R.id.tabContainer)
        tabIndicator = view.findViewById(R.id.tabIndicator)

        // Set indicator width after layout
        tabContainer.post {
            val containerWidth = tabContainer.width
            val margin = (3 * resources.displayMetrics.density).toInt() // 3dp margin
            tabIndicatorWidth = (containerWidth - margin * 2) / 2

            // Set indicator width programmatically
            tabIndicator?.layoutParams?.width = tabIndicatorWidth
            tabIndicator?.requestLayout()
        }

        tabFast.setOnClickListener {
            if (!isFastMode) {
                switchToFastMode()
            }
        }

        tabDeep.setOnClickListener {
            if (isFastMode) {
                switchToDeepMode()
            }
        }
    }

    private fun setupRetakeButton() {
        val view = popupView ?: return
        view.findViewById<View>(R.id.btnRetake).setOnClickListener {
            if (ScreenshotService.isServiceRunning) {
                dismissWithAnimation()
                handler.postDelayed({
                    ScreenshotService.requestScreenshot()
                }, 400)
            } else {
                Toast.makeText(this, "Please enable screenshot first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupActionButton() {
        val view = popupView ?: return
        view.findViewById<View>(R.id.btnAction).setOnClickListener {
            stopCurrentModeRequests()
        }
    }

    private fun stopCurrentModeRequests() {
        // Check if any question in current mode has started outputting answers
        val answers = if (isFastMode) fastAnswers else deepAnswers
        val hasAnyAnswerOutput = currentQuestions.any { question ->
            answers[question.id]?.let { answer ->
                answer.text.isNotEmpty() || answer.thinkingText.isNotEmpty() || answer.isThinking
            } == true
        }

        // If no answer output yet, treat as OCR phase and stop everything
        // If has answer output, treat as answer phase and only stop current mode
        if (!hasAnyAnswerOutput) {
            // No answer output yet, stop everything (OCR phase behavior)
            stopAllRequests()
            return
        }

        // Has answer output - stop only current mode's requests (answer phase behavior)
        // Also cancel OCR if still running (for remaining questions)
        ocrCall?.cancel()
        ocrCall = null

        if (isFastMode) {
            // Stop fast mode requests
            fastCalls.values.forEach { it.cancel() }
            fastCalls.clear()
            isFastModeStopped = true
            // Mark all fast answers as stopped
            currentQuestions.forEach { question ->
                fastAnswers[question.id]?.let {
                    markThinkingCompleted(it)
                    it.isComplete = true
                    it.isStopped = true
                }
            }
        } else {
            // Stop deep mode requests
            deepCalls.values.forEach { it.cancel() }
            deepCalls.clear()
            isDeepModeStopped = true
            // Mark all deep answers as stopped
            currentQuestions.forEach { question ->
                deepAnswers[question.id]?.let {
                    markThinkingCompleted(it)
                    it.isComplete = true
                    it.isStopped = true
                }
            }
        }

        // Update UI to show stopped state
        handler.post {
            val view = popupView ?: return@post
            // Hide loading spinner
            view.findViewById<ProgressBar>(R.id.headerLoading)?.visibility = View.GONE
            // Update header text
            view.findViewById<TextView>(R.id.tvHeaderTitle)?.text = "已停止"
            // Update action button to arrow icon with animation
            animateActionButtonIcon(R.drawable.ic_arrow_up_white)

            // Update all cards to show stopped state
            val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return@post
            val recognizedQuestionIds = currentQuestions.map { it.id }.toSet()

            for (i in 0 until container.childCount) {
                val childView = container.getChildAt(i) ?: continue
                val tag = childView.tag as? String ?: continue
                val questionId = tag.removePrefix("question_").toIntOrNull() ?: continue

                if (questionId in recognizedQuestionIds) {
                    // Fully recognized question - update answer title and show retry button
                    childView.findViewById<TextView>(R.id.tvAnswerTitle)?.text = "已停止"
                    showRetryButton(childView, questionId)
                } else {
                    // Still being OCR'd - update both titles to "已停止" and hide retry buttons
                    childView.findViewById<TextView>(R.id.tvQuestionTitle)?.text = "已停止"
                    childView.findViewById<TextView>(R.id.tvAnswerTitle)?.text = "已停止"
                    hideRetryButtons(childView, isFastMode, keepModelButton = false)
                }
            }

            displayAnswersForMode(isFastMode)
        }

        // Check if all answers are complete
        checkAllAnswersComplete()
    }

    private fun stopAllRequests() {
        // Cancel OCR call
        ocrCall?.cancel()
        ocrCall = null

        // Cancel all fast mode calls
        fastCalls.values.forEach { it.cancel() }
        fastCalls.clear()
        isFastModeStopped = true

        // Cancel all deep mode calls
        deepCalls.values.forEach { it.cancel() }
        deepCalls.clear()
        isDeepModeStopped = true

        // Mark all answers as stopped
        currentQuestions.forEach { question ->
            fastAnswers[question.id]?.let {
                markThinkingCompleted(it)
                it.isComplete = true
                it.isStopped = true
            }
            deepAnswers[question.id]?.let {
                markThinkingCompleted(it)
                it.isComplete = true
                it.isStopped = true
            }
        }

        // Update UI to show stopped state
        handler.post {
            val view = popupView ?: return@post
            // Hide loading spinner
            view.findViewById<ProgressBar>(R.id.headerLoading)?.visibility = View.GONE
            // Update header text
            view.findViewById<TextView>(R.id.tvHeaderTitle)?.text = "已停止"
            // Update action button to arrow icon with animation
            animateActionButtonIcon(R.drawable.ic_arrow_up_white)

            // Update answer titles to show stopped state (no retry button in OCR phase)
            val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return@post
            for (i in 0 until container.childCount) {
                container.getChildAt(i)?.let { childView ->
                    childView.findViewById<TextView>(R.id.tvQuestionTitle)?.text = "已停止"
                    childView.findViewById<TextView>(R.id.tvAnswerTitle)?.text = "已停止"
                    // Hide retry buttons - can't retry incomplete OCR
                    hideRetryButtons(childView, isFastMode, keepModelButton = false)
                }
            }

            displayAnswersForMode(isFastMode)
        }
    }

    private fun cancelAllRequests() {
        // Cancel OCR call
        ocrCall?.cancel()
        ocrCall = null

        // Cancel all fast mode calls
        fastCalls.values.forEach { it.cancel() }
        fastCalls.clear()

        // Cancel all deep mode calls
        deepCalls.values.forEach { it.cancel() }
        deepCalls.clear()
    }

    private fun setupSwipeGesture() {
        val view = popupView ?: return
        val scrollView = view.findViewById<View>(R.id.scrollView) ?: return

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                // Only handle horizontal swipes (when horizontal movement > vertical)
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe right - switch to Fast mode
                            if (!isFastMode) {
                                switchToFastMode()
                            }
                        } else {
                            // Swipe left - switch to Deep mode
                            if (isFastMode) {
                                switchToDeepMode()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        scrollView.setOnTouchListener { v, event ->
            gestureDetector?.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                cancelPendingScrollRestore()
            }
            false // Let scroll view handle its own scrolling
        }
    }

    private fun setupScrollTracking() {
        val view = popupView ?: return
        val scrollView = view.findViewById<ScrollView>(R.id.scrollView) ?: return
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (isApplyingScrollRestore) return@addOnScrollChangedListener
            saveScrollForMode(isFastMode, scrollView.scrollY)
        }
    }

    private fun saveScrollForMode(isFast: Boolean, scrollY: Int) {
        if (isFast) {
            fastModeScrollY = scrollY.coerceAtLeast(0)
        } else {
            deepModeScrollY = scrollY.coerceAtLeast(0)
        }
    }

    private fun captureCurrentModeScroll() {
        val view = popupView ?: return
        val scrollView = view.findViewById<ScrollView>(R.id.scrollView) ?: return
        saveScrollForMode(isFastMode, scrollView.scrollY)
    }

    private fun cancelPendingScrollRestore() {
        scrollRestoreToken++
    }

    private fun restoreScrollForMode(isFast: Boolean) {
        val view = popupView ?: return
        val scrollView = view.findViewById<ScrollView>(R.id.scrollView) ?: return
        val targetScrollY = if (isFast) fastModeScrollY else deepModeScrollY

        val restoreToken = ++scrollRestoreToken
        val restoreDelays = longArrayOf(0L, 180L, 360L)
        restoreDelays.forEach { delay ->
            scrollView.postDelayed({
                if (restoreToken != scrollRestoreToken) return@postDelayed
                if (isFastMode != isFast) return@postDelayed
                val child = scrollView.getChildAt(0) ?: return@postDelayed
                val maxScroll = (child.height - scrollView.height).coerceAtLeast(0)
                val resolvedY = targetScrollY.coerceIn(0, maxScroll)
                isApplyingScrollRestore = true
                scrollView.scrollTo(0, resolvedY)
                isApplyingScrollRestore = false
            }, delay)
        }
    }

    private fun setupBackGesture() {
        // Normal overlay click = dismiss directly
        // Edge swipe (back gesture simulation) = show reminder dialog
        overlayView?.setOnTouchListener { _, event ->
            val screenWidth = resources.displayMetrics.widthPixels

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    edgeSwipeStartX = event.rawX
                    edgeSwipeStartY = event.rawY
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - edgeSwipeStartX
                    val deltaY = Math.abs(event.rawY - edgeSwipeStartY)

                    // Check if started from left edge and swiped right (back gesture)
                    if (edgeSwipeStartX < EDGE_THRESHOLD && deltaX > 100 && deltaY < 100) {
                        // Back gesture detected - show reminder dialog
                        showBackGestureReminder()
                        true
                    } else if (deltaX < 20 && deltaY < 20) {
                        // Simple tap - dismiss directly
                        dismissWithAnimation()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun showBackGestureReminder() {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("提示")
            .setMessage("点击灰色区域可以关闭窗口")
            .setPositiveButton("知道了", null)
            .create()

        // Need to set window type for showing dialog from service
        dialog.window?.setType(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
        )
        dialog.show()
    }

    private fun showModelSwitchMenu(anchorView: View, questionId: Int, forFastMode: Boolean) {
        if (questionId <= 0) return
        val modelIds = getModelListForMode(forFastMode)
        if (modelIds.isEmpty()) {
            Toast.makeText(this, "请先在设置中添加模型", Toast.LENGTH_SHORT).show()
            return
        }

        modelMenuPopup?.dismiss()
        copyMenuPopup?.dismiss()
        val currentModel = getSelectedModelForQuestion(questionId, forFastMode)
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)

        val menuView = LayoutInflater.from(this).inflate(R.layout.popup_model_switch_menu, null)
        val root = menuView.findViewById<LinearLayout>(R.id.modelMenuRoot)
        val tvTitle = menuView.findViewById<TextView>(R.id.tvModelMenuTitle)
        val tvSubtitle = menuView.findViewById<TextView>(R.id.tvModelMenuSubtitle)
        val optionsContainer = menuView.findViewById<LinearLayout>(R.id.modelMenuOptionsContainer)

        tvTitle.text = if (forFastMode) "极速模型" else "深度模型"
        tvSubtitle.text = "题目$questionId · 切换模型"
        styleModelMenuPopup(root, tvTitle, tvSubtitle, isLightGreenGray)

        optionsContainer.removeAllViews()
        modelIds.forEachIndexed { index, modelId ->
            val optionView = LayoutInflater.from(this)
                .inflate(R.layout.item_model_menu_option, optionsContainer, false)
            val tvModelName = optionView.findViewById<TextView>(R.id.tvModelMenuName)
            val tvModelDesc = optionView.findViewById<TextView>(R.id.tvModelMenuDesc)
            val ivCheck = optionView.findViewById<ImageView>(R.id.ivModelMenuCheck)
            val selected = modelId == currentModel

            tvModelName.text = modelId
            tvModelDesc.text = describeModelForDisplay(modelId, index, modelIds.size, forFastMode)
            styleModelMenuOption(optionView, tvModelName, tvModelDesc, ivCheck, selected, isLightGreenGray)

            optionView.setOnClickListener {
                if (modelId == getSelectedModelForQuestion(questionId, forFastMode)) {
                    modelMenuPopup?.dismiss()
                    return@setOnClickListener
                }
                setSelectedModelForQuestion(questionId, forFastMode, modelId)
                if (isFastMode == forFastMode) {
                    modelMenuPopup?.dismiss()
                    Toast.makeText(this, "题目$questionId 已切换模型：$modelId，正在自动重试", Toast.LENGTH_SHORT).show()
                    retryQuestion(questionId)
                } else {
                    updateVisibleModelButtons()
                    modelMenuPopup?.dismiss()
                    Toast.makeText(this, "题目$questionId 已切换模型：$modelId", Toast.LENGTH_SHORT).show()
                }
            }
            optionsContainer.addView(optionView)
        }

        val menuWidth = estimateModelMenuWidth()
        val popupWindow = PopupWindow(menuView, menuWidth, WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10)
            }
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { modelMenuPopup = null }
        }

        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val menuHeight = menuView.measuredHeight.coerceAtLeast(dpInt(120))
        val yOffset = -(anchorView.height + menuHeight + dpInt(6))

        popupWindow.showAsDropDown(anchorView, 0, yOffset, Gravity.START)
        modelMenuPopup = popupWindow
    }

    private fun styleModelMenuPopup(
        root: LinearLayout?,
        tvTitle: TextView?,
        tvSubtitle: TextView?,
        isLightGreenGray: Boolean
    ) {
        if (isLightGreenGray) {
            root?.setBackgroundResource(R.drawable.bg_model_menu_surface)
            tvTitle?.setTextColor(0xFF17322B.toInt())
            tvSubtitle?.setTextColor(0xFF4D6860.toInt())
        } else {
            root?.setBackgroundResource(R.drawable.bg_model_menu_surface_light_brown_black)
            tvTitle?.setTextColor(0xFF2C201C.toInt())
            tvSubtitle?.setTextColor(0xFF725B52.toInt())
        }
    }

    private fun styleModelMenuOption(
        optionView: View,
        tvModelName: TextView,
        tvModelDesc: TextView,
        ivCheck: ImageView,
        selected: Boolean,
        isLightGreenGray: Boolean
    ) {
        if (isLightGreenGray) {
            optionView.setBackgroundResource(
                if (selected) R.drawable.bg_model_menu_item_selected else R.drawable.bg_model_menu_item
            )
            tvModelName.setTextColor(if (selected) 0xFF0F8F71.toInt() else 0xFF243036.toInt())
            tvModelDesc.setTextColor(if (selected) 0xFF2D7563.toInt() else 0xFF5F6E74.toInt())
            ivCheck.setColorFilter(0xFF0F8F71.toInt())
        } else {
            optionView.setBackgroundResource(
                if (selected) R.drawable.bg_model_menu_item_selected_light_brown_black
                else R.drawable.bg_model_menu_item_light_brown_black
            )
            tvModelName.setTextColor(if (selected) 0xFFA75E41.toInt() else 0xFF2E2523.toInt())
            tvModelDesc.setTextColor(if (selected) 0xFF8C5D49.toInt() else 0xFF7B6A64.toInt())
            ivCheck.setColorFilter(0xFFA75E41.toInt())
        }
        ivCheck.visibility = if (selected) View.VISIBLE else View.GONE
    }

    private fun estimateModelMenuWidth(): Int {
        val popupWidth = popupView?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return (popupWidth * 0.62f).toInt().coerceIn(dpInt(220), dpInt(320))
    }

    private fun showCopyMenu(anchorView: View, questionId: Int) {
        if (questionId <= 0) return
        copyMenuPopup?.dismiss()
        modelMenuPopup?.dismiss()

        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        val menuView = LayoutInflater.from(this).inflate(R.layout.popup_copy_menu, null)
        val root = menuView.findViewById<LinearLayout>(R.id.copyMenuRoot)
        val viewHandle = menuView.findViewById<View>(R.id.viewCopyMenuHandle)
        val tvTitle = menuView.findViewById<TextView>(R.id.tvCopyMenuTitle)
        val tvSubtitle = menuView.findViewById<TextView>(R.id.tvCopyMenuSubtitle)
        val btnCopyQuestionAndAnswer = menuView.findViewById<TextView>(R.id.btnCopyQuestionAndAnswer)
        val btnCopyAnswerOnly = menuView.findViewById<TextView>(R.id.btnCopyAnswerOnly)

        if (isLightGreenGray) {
            root.setBackgroundResource(R.drawable.bg_model_menu_surface)
            viewHandle.setBackgroundColor(0xFF9BBEB2.toInt())
            tvTitle.setTextColor(0xFF17322B.toInt())
            tvSubtitle.setTextColor(0xFF5F7B71.toInt())
            btnCopyQuestionAndAnswer.setBackgroundResource(R.drawable.bg_model_menu_item)
            btnCopyQuestionAndAnswer.setTextColor(0xFF243036.toInt())
            btnCopyAnswerOnly.setBackgroundResource(R.drawable.bg_model_menu_item)
            btnCopyAnswerOnly.setTextColor(0xFF243036.toInt())
        } else {
            root.setBackgroundResource(R.drawable.bg_model_menu_surface_light_brown_black)
            viewHandle.setBackgroundColor(0xFFB69684.toInt())
            tvTitle.setTextColor(0xFF2C201C.toInt())
            tvSubtitle.setTextColor(0xFF7D685F.toInt())
            btnCopyQuestionAndAnswer.setBackgroundResource(R.drawable.bg_model_menu_item_light_brown_black)
            btnCopyQuestionAndAnswer.setTextColor(0xFF2E2523.toInt())
            btnCopyAnswerOnly.setBackgroundResource(R.drawable.bg_model_menu_item_light_brown_black)
            btnCopyAnswerOnly.setTextColor(0xFF2E2523.toInt())
        }

        btnCopyQuestionAndAnswer.setOnClickListener {
            val content = buildCopyContent(questionId, includeQuestion = true)
            if (content.isBlank()) {
                Toast.makeText(this, "暂无可复制内容", Toast.LENGTH_SHORT).show()
            } else {
                copyToClipboard("question_answer_$questionId", content)
                Toast.makeText(this, "题与解已复制", Toast.LENGTH_SHORT).show()
            }
            copyMenuPopup?.dismiss()
        }

        btnCopyAnswerOnly.setOnClickListener {
            val content = buildCopyContent(questionId, includeQuestion = false)
            if (content.isBlank()) {
                Toast.makeText(this, "暂无可复制解答", Toast.LENGTH_SHORT).show()
            } else {
                copyToClipboard("answer_$questionId", content)
                Toast.makeText(this, "解答已复制", Toast.LENGTH_SHORT).show()
            }
            copyMenuPopup?.dismiss()
        }

        val menuWidth = estimateCopyMenuWidth()
        val popupWindow = PopupWindow(menuView, menuWidth, WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10)
            }
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { copyMenuPopup = null }
        }

        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val menuHeight = menuView.measuredHeight.coerceAtLeast(dpInt(104))
        val yOffset = -(anchorView.height + menuHeight + dpInt(6))

        popupWindow.showAsDropDown(anchorView, 0, yOffset, Gravity.START)
        copyMenuPopup = popupWindow
    }

    private fun estimateCopyMenuWidth(): Int {
        val popupWidth = popupView?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return (popupWidth * 0.52f).toInt().coerceIn(dpInt(180), dpInt(280))
    }

    private fun buildCopyContent(questionId: Int, includeQuestion: Boolean): String {
        val answer = if (isFastMode) fastAnswers[questionId] else deepAnswers[questionId]
        val answerText = when {
            answer?.error != null -> "错误: ${answer.error}"
            !answer?.text.isNullOrBlank() -> answer?.text.orEmpty()
            else -> ""
        }.trim()

        if (!includeQuestion) return answerText

        val questionText = currentQuestions.find { it.id == questionId }?.text?.trim().orEmpty()
        val sections = mutableListOf<String>()
        if (questionText.isNotBlank()) {
            sections.add("题目：\n$questionText")
        }
        if (answerText.isNotBlank()) {
            sections.add("解答：\n$answerText")
        }
        return sections.joinToString("\n\n").trim()
    }

    private fun copyToClipboard(label: String, content: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
    }

    private fun describeModelForDisplay(modelId: String, index: Int, total: Int, isFastMode: Boolean): String {
        return when {
            index == 0 && total > 1 -> "列表首选模型，常用于默认解题。"
            modelId.contains("mini", ignoreCase = true) -> "响应更快，适合常规题目与快速重试。"
            modelId.contains("o3", ignoreCase = true) ||
                    modelId.contains("reason", ignoreCase = true) -> "推理更深入，适合多步分析题。"
            isFastMode -> "用于极速模式，平衡速度与准确率。"
            else -> "用于深度模式，适合复杂场景推导。"
        }
    }

    private fun updateVisibleModelButtons() {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        for (i in 0 until container.childCount) {
            val itemView = container.getChildAt(i) ?: continue
            val questionId = getQuestionIdFromItemView(itemView) ?: continue
            updateModelSwitchButton(itemView, questionId, isFastMode)
        }
    }

    private fun dp(value: Int): Float {
        return value * resources.displayMetrics.density
    }

    private fun dpInt(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun applyPopupTheme() {
        val view = popupView ?: return
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)

        // Main container background
        val rootLayout = view as? LinearLayout

        // Tab area
        val tabAreaBg = view.findViewById<FrameLayout>(R.id.tabAreaBg)
        val tabContainer = view.findViewById<FrameLayout>(R.id.tabContainer)
        val tabIndicator = view.findViewById<View>(R.id.tabIndicator)
        val tabFast = view.findViewById<TextView>(R.id.tabFast)
        val tabDeep = view.findViewById<TextView>(R.id.tabDeep)

        // Bottom bar
        val bottomBar = view.findViewById<View>(R.id.btnRetake)?.parent?.parent as? LinearLayout

        // Scroll content area
        val scrollView = view.findViewById<View>(R.id.scrollView)
        val answersContainer = view.findViewById<LinearLayout>(R.id.answersContainer)

        if (isLightGreenGray) {
            // 浅绿灰主题
            val primaryColor = 0xFF10A37F.toInt()
            val backgroundColor = 0xFFFFFFFF.toInt()
            val surfaceColor = 0xFFF5F5F5.toInt()
            val textPrimary = 0xFF202123.toInt()
            val textSecondary = 0xFF6E6E80.toInt()

            // Main background
            rootLayout?.setBackgroundResource(R.drawable.bg_answer_popup)

            // Tab area background with rounded corners
            tabAreaBg?.setBackgroundResource(R.drawable.bg_tab_area)
            bottomBar?.setBackgroundColor(backgroundColor)

            // Tab indicator - use green for 浅绿灰 theme
            tabIndicator?.setBackgroundResource(R.drawable.bg_tab_indicator_light_green_gray)

            // Tab container - lighter background
            tabContainer?.setBackgroundResource(R.drawable.bg_tab_container_light_green_gray)

            // Update tab text colors based on current mode
            if (isFastMode) {
                tabFast?.setTextColor(0xFFFFFFFF.toInt())
                tabDeep?.setTextColor(textSecondary)
            } else {
                tabFast?.setTextColor(textSecondary)
                tabDeep?.setTextColor(0xFFFFFFFF.toInt())
            }

            // Retake button
            view.findViewById<TextView>(R.id.btnRetake)?.setBackgroundResource(R.drawable.bg_button_retake_light_green_gray)

            // Action button (circular theme color)
            view.findViewById<ImageView>(R.id.btnAction)?.setBackgroundResource(R.drawable.bg_action_button_circle)

        } else {
            // 浅棕黑主题 - 暖橙色按钮，黑色文字
            val primaryColor = 0xFFDA7A5A.toInt()  // 暖橙色用于按钮
            val backgroundColor = 0xFFFAF9F5.toInt()
            val surfaceColor = 0xFFE8E5DF.toInt()
            val textPrimary = 0xFF141413.toInt()  // 黑色用于文字
            val textSecondary = 0xFF666666.toInt()

            // Main background
            rootLayout?.setBackgroundResource(R.drawable.bg_answer_popup_light_brown_black)

            // Tab area background with rounded corners
            tabAreaBg?.setBackgroundResource(R.drawable.bg_tab_area_light_brown_black)
            bottomBar?.setBackgroundColor(backgroundColor)

            // Tab indicator - dark for 浅棕黑 theme
            tabIndicator?.setBackgroundResource(R.drawable.bg_tab_indicator_light_brown_black)

            // Tab container - light background
            tabContainer?.setBackgroundResource(R.drawable.bg_tab_container_light_brown_black)

            // Update tab text colors based on current mode
            if (isFastMode) {
                tabFast?.setTextColor(0xFFFFFFFF.toInt())
                tabDeep?.setTextColor(textSecondary)
            } else {
                tabFast?.setTextColor(textSecondary)
                tabDeep?.setTextColor(0xFFFFFFFF.toInt())
            }

            // Retake button
            view.findViewById<TextView>(R.id.btnRetake)?.setBackgroundResource(R.drawable.bg_button_retake_light_brown_black)

            // Action button (circular theme color)
            view.findViewById<ImageView>(R.id.btnAction)?.setBackgroundResource(R.drawable.bg_action_button_circle_light_brown_black)
        }

        answersContainer?.let { container ->
            for (i in 0 until container.childCount) {
                container.getChildAt(i)?.let { itemView ->
                    applyThemeToQuestionCard(itemView)
                }
            }
        }
        updateVisibleModelButtons()
    }

    private fun applyThemeToQuestionCard(itemView: View) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        itemView.findViewById<View>(R.id.questionSection)?.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_question_card
            else R.drawable.bg_question_card_light_brown_black
        )

        itemView.findViewById<View>(R.id.thinkingSection)?.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_thinking_card
            else R.drawable.bg_thinking_card_light_brown_black
        )
        itemView.findViewById<View>(R.id.thinkingIndicator)?.setBackgroundResource(
            if (isLightGreenGray) R.drawable.indicator_orange
            else R.drawable.indicator_orange_light_brown_black
        )

        val thinkingTitle = itemView.findViewById<TextView>(R.id.tvThinkingTitle)
        val thinkingDuration = itemView.findViewById<TextView>(R.id.tvThinkingDuration)
        val thinkingToggle = itemView.findViewById<TextView>(R.id.tvThinkingToggle)
        val thinkingText = itemView.findViewById<TextView>(R.id.tvThinkingText)
        val btnCopyBottom = itemView.findViewById<TextView>(R.id.btnCopyBottom)
        val btnModelSwitchBottom = itemView.findViewById<TextView>(R.id.btnModelSwitchBottom)

        if (isLightGreenGray) {
            thinkingTitle?.setTextColor(0xFF7A4B00.toInt())
            thinkingDuration?.setTextColor(0xFF9A7B47.toInt())
            thinkingToggle?.setTextColor(0xFF9A7B47.toInt())
            thinkingText?.setTextColor(0xFF4E3A1F.toInt())
            btnCopyBottom?.setBackgroundResource(R.drawable.bg_copy_button)
            btnCopyBottom?.setTextColor(0xFF0F8F71.toInt())
            btnModelSwitchBottom?.setBackgroundResource(R.drawable.bg_model_switch_button)
            btnModelSwitchBottom?.setTextColor(0xFF0F8F71.toInt())
        } else {
            thinkingTitle?.setTextColor(0xFF5F2F14.toInt())
            thinkingDuration?.setTextColor(0xFF8B5A40.toInt())
            thinkingToggle?.setTextColor(0xFF8B5A40.toInt())
            thinkingText?.setTextColor(0xFF4A2A1D.toInt())
            btnCopyBottom?.setBackgroundResource(R.drawable.bg_copy_button_light_brown_black)
            btnCopyBottom?.setTextColor(0xFFA75E41.toInt())
            btnModelSwitchBottom?.setBackgroundResource(R.drawable.bg_model_switch_button_light_brown_black)
            btnModelSwitchBottom?.setTextColor(0xFFA75E41.toInt())
        }
    }

    private fun switchToFastMode() {
        val view = popupView ?: return
        val tabFast = view.findViewById<TextView>(R.id.tabFast)
        val tabDeep = view.findViewById<TextView>(R.id.tabDeep)
        val container = view.findViewById<LinearLayout>(R.id.answersContainer)

        captureCurrentModeScroll()

        isFastMode = true

        // Animate indicator sliding to left
        tabIndicator?.animate()
            ?.translationX(0f)
            ?.setDuration(TAB_ANIM_DURATION)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.start()

        // Animate text colors
        animateTextColor(tabFast, 0xFF757575.toInt(), 0xFFFFFFFF.toInt())
        animateTextColor(tabDeep, 0xFFFFFFFF.toInt(), 0xFF757575.toInt())

        // Content slide animation from left with fade
        container?.let {
            it.alpha = 0.3f
            it.translationX = -it.width * 0.3f
            it.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(TAB_ANIM_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        displayAnswersForMode(true)
        restoreScrollForMode(true)
        updateHeaderForCurrentMode()
    }

    private fun switchToDeepMode() {
        val view = popupView ?: return
        val tabFast = view.findViewById<TextView>(R.id.tabFast)
        val tabDeep = view.findViewById<TextView>(R.id.tabDeep)
        val container = view.findViewById<LinearLayout>(R.id.answersContainer)

        captureCurrentModeScroll()

        isFastMode = false

        // Animate indicator sliding to right
        tabIndicator?.animate()
            ?.translationX(tabIndicatorWidth.toFloat())
            ?.setDuration(TAB_ANIM_DURATION)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.start()

        // Animate text colors
        animateTextColor(tabFast, 0xFFFFFFFF.toInt(), 0xFF757575.toInt())
        animateTextColor(tabDeep, 0xFF757575.toInt(), 0xFFFFFFFF.toInt())

        // Content slide animation from right with fade
        container?.let {
            it.alpha = 0.3f
            it.translationX = it.width * 0.3f
            it.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(TAB_ANIM_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        displayAnswersForMode(false)
        restoreScrollForMode(false)
        updateHeaderForCurrentMode()
    }

    private fun animateTextColor(textView: TextView, fromColor: Int, toColor: Int) {
        ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
            duration = TAB_ANIM_DURATION
            addUpdateListener { animator ->
                textView.setTextColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun updateHeaderForCurrentMode() {
        val view = popupView ?: return
        val answers = if (isFastMode) fastAnswers else deepAnswers
        val isStopped = if (isFastMode) isFastModeStopped else isDeepModeStopped

        // Check if current mode is stopped
        if (isStopped) {
            view.findViewById<ProgressBar>(R.id.headerLoading)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.tvHeaderTitle)?.text = "已停止"
            animateActionButtonIcon(R.drawable.ic_arrow_up_white)
            return
        }

        // Check if all answers in current mode are complete
        val allComplete = currentQuestions.all { question ->
            answers[question.id]?.isComplete == true
        }

        if (allComplete && currentQuestions.isNotEmpty()) {
            view.findViewById<ProgressBar>(R.id.headerLoading)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.tvHeaderTitle)?.text = "已完成解答"
            animateActionButtonIcon(R.drawable.ic_arrow_up_white)
        } else {
            view.findViewById<ProgressBar>(R.id.headerLoading)?.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvHeaderTitle)?.text = "解答中..."
            animateActionButtonIcon(R.drawable.ic_stop_white)
        }
    }

    private fun getDefaultModelForMode(isFast: Boolean): String {
        return if (isFast) {
            if (selectedFastModelId.isBlank()) {
                selectedFastModelId = AISettings.getSelectedFastModel(this)
            }
            selectedFastModelId.ifBlank { AISettings.getFastConfig(this).modelId }
        } else {
            if (selectedDeepModelId.isBlank()) {
                selectedDeepModelId = AISettings.getSelectedDeepModel(this)
            }
            selectedDeepModelId.ifBlank { AISettings.getDeepConfig(this).modelId }
        }
    }

    private fun getModelListForMode(isFast: Boolean): List<String> {
        return if (isFast) AISettings.getFastModelList(this) else AISettings.getDeepModelList(this)
    }

    private fun ensureQuestionModelInitialized(questionId: Int, isFast: Boolean) {
        if (questionId <= 0) return
        val modelMap = if (isFast) fastQuestionModelIds else deepQuestionModelIds
        if (modelMap.containsKey(questionId)) return
        val defaultModel = getDefaultModelForMode(isFast)
        modelMap[questionId] = defaultModel
    }

    private fun getSelectedModelForQuestion(questionId: Int, isFast: Boolean): String {
        if (questionId <= 0) return getDefaultModelForMode(isFast)

        val availableModels = getModelListForMode(isFast)
        val fallbackModel = availableModels.firstOrNull() ?: getDefaultModelForMode(isFast)
        val modelMap = if (isFast) fastQuestionModelIds else deepQuestionModelIds

        ensureQuestionModelInitialized(questionId, isFast)
        val current = modelMap[questionId].orEmpty()
        val resolved = if (current.isNotBlank() && (availableModels.isEmpty() || availableModels.contains(current))) {
            current
        } else {
            fallbackModel
        }
        modelMap[questionId] = resolved
        return resolved
    }

    private fun setSelectedModelForQuestion(questionId: Int, isFast: Boolean, modelId: String) {
        if (questionId <= 0) return
        val normalized = modelId.trim()
        if (normalized.isBlank()) return
        val modelMap = if (isFast) fastQuestionModelIds else deepQuestionModelIds
        modelMap[questionId] = normalized
    }

    private fun getModeConfig(questionId: Int, isFast: Boolean): AIConfig {
        val baseConfig = if (isFast) AISettings.getFastConfig(this) else AISettings.getDeepConfig(this)
        val selectedModel = getSelectedModelForQuestion(questionId, isFast)
        return baseConfig.copy(modelId = selectedModel)
    }

    fun processBitmap(bitmap: Bitmap) {
        currentBitmap = bitmap
        // Reset cached answers
        fastAnswers.clear()
        deepAnswers.clear()
        fastAnswerViews.clear()
        deepAnswerViews.clear()
        questionTexts.clear()
        fastQuestionModelIds.clear()
        deepQuestionModelIds.clear()
        isFastSolving = false
        isDeepSolving = false
        hasStartedAnswering = false
        isAllAnswersComplete = false
        currentQuestions = mutableListOf()
        currentActionIconRes = R.drawable.ic_stop_white  // Reset icon state

        // Reset stopped states and clear previous calls
        isFastModeStopped = false
        isDeepModeStopped = false
        fastModeScrollY = 0
        deepModeScrollY = 0
        cancelPendingScrollRestore()
        cancelAllRequests()

        // Load selected models at the start of each solve session
        selectedFastModelId = AISettings.getSelectedFastModel(this).trim()
        selectedDeepModelId = AISettings.getSelectedDeepModel(this).trim()

        showOCRStreaming()

        val config = AISettings.getOCRConfig(this@AnswerPopupService)
        if (!config.isValid() || config.apiKey.isBlank()) {
            showReminder("请先到设置中配置API Key")
            return
        }

        val visionAPI = VisionAPI(config)
        var currentStreamingIndex = 1

        ocrCall = visionAPI.extractQuestionsStreaming(bitmap, object : OCRStreamingCallback {
            override fun onChunk(text: String, currentQuestionIndex: Int) {
                handler.post {
                    // 如果题目索引变了，说明进入了新题目
                    if (currentQuestionIndex != currentStreamingIndex) {
                        currentStreamingIndex = currentQuestionIndex
                        // 为新题目创建卡片
                        addNewQuestionCard(currentQuestionIndex)
                    }
                    appendOCRTextToQuestion(text, currentStreamingIndex)
                }
            }

            override fun onQuestionReady(question: Question) {
                handler.post {
                    // 更新题目卡片的标题
                    updateQuestionCardTitle(question.id)
                    (currentQuestions as MutableList).add(question)
                    // 立即开始解答这道题
                    startSolvingQuestion(question)
                }
            }

            override fun onNoQuestionDetected() {
                handler.post {
                    // Cancel all pending requests (also clears ocrCall)
                    cancelAllRequests()
                    // Show no questions detected UI
                    showNoQuestionsDetected()
                    // Update header
                    val view = popupView ?: return@post
                    view.findViewById<ProgressBar>(R.id.headerLoading)?.visibility = View.GONE
                    view.findViewById<TextView>(R.id.tvHeaderTitle)?.text = "未识别到题目"
                    animateActionButtonIcon(R.drawable.ic_arrow_up_white)
                }
            }

            override fun onComplete() {
                handler.post {
                    ocrCall = null  // Clear OCR call reference when complete
                    if (currentQuestions.isEmpty()) {
                        showNoQuestionsDetected()
                    }
                    // 题目已经在 onQuestionReady 中开始解答了
                }
            }

            override fun onError(error: Exception) {
                handler.post {
                    ocrCall = null  // Clear OCR call reference on error
                    showError("OCR失败: ${error.message}")
                }
            }
        })
    }

    private fun showOCRStreaming() {
        handler.post {
            val view = popupView ?: return@post
            view.findViewById<View>(R.id.loadingView)?.visibility = View.GONE
            val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return@post
            container.visibility = View.VISIBLE
            container.removeAllViews()

            // 创建第一个题目卡片
            addNewQuestionCard(1)
        }
    }

    private fun addNewQuestionCard(questionIndex: Int) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return

        ensureQuestionModelInitialized(questionIndex, true)
        ensureQuestionModelInitialized(questionIndex, false)

        val itemView = LayoutInflater.from(this)
            .inflate(R.layout.item_question_answer, container, false)
        itemView.tag = "question_$questionIndex"
        itemView.findViewById<TextView>(R.id.tvQuestionTitle).text = "识别中..."
        itemView.findViewById<TextView>(R.id.tvQuestionText).text = ""
        itemView.findViewById<TextView>(R.id.tvAnswerText).text = ""
        itemView.findViewById<TextView>(R.id.tvThinkingText).text = ""
        itemView.findViewById<View>(R.id.thinkingSection).visibility = View.GONE
        applyThemeToQuestionCard(itemView)
        showBottomActionRow(itemView, questionIndex, isFastMode, showRetry = false)

        container.addView(itemView)

        // Initialize StringBuilder for this question
        questionTexts[questionIndex] = StringBuilder()
    }

    private fun appendOCRTextToQuestion(text: String, questionIndex: Int) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        val questionView = container.findViewWithTag<View>("question_$questionIndex") ?: return
        val tvQuestion = questionView.findViewById<TextView>(R.id.tvQuestionText)

        // Accumulate text
        questionTexts[questionIndex]?.append(text)

        // Render with Markdown and LaTeX support (with newline conversion for OCR text)
        val fullText = questionTexts[questionIndex]?.toString() ?: text
        MarkdownRenderer.renderQuestionText(this@AnswerPopupService, tvQuestion, fullText)
    }

    private fun updateQuestionCardTitle(questionId: Int) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        val questionView = container.findViewWithTag<View>("question_$questionId") ?: return
        questionView.findViewById<TextView>(R.id.tvQuestionTitle).text = "题目$questionId"
        // 保存view引用用于后续答案更新
        fastAnswerViews[questionId] = questionView
    }

    private fun appendOCRText(text: String) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        val ocrView = container.findViewWithTag<View>("ocr_streaming") ?: return
        val tvQuestion = ocrView.findViewById<TextView>(R.id.tvQuestionText)

        // Accumulate and render with Markdown support (with newline conversion for OCR text)
        questionTexts[0]?.append(text) ?: run { questionTexts[0] = StringBuilder(text) }
        val fullText = questionTexts[0]?.toString() ?: text
        MarkdownRenderer.renderQuestionText(this@AnswerPopupService, tvQuestion, fullText)
    }

    private fun showLoading(text: String) {
        handler.post {
            val view = popupView ?: return@post
            view.findViewById<View>(R.id.loadingView)?.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvLoadingText)?.text = text
            view.findViewById<LinearLayout>(R.id.answersContainer)?.visibility = View.GONE
        }
    }

    private fun hideLoading() {
        handler.post {
            val view = popupView ?: return@post
            view.findViewById<View>(R.id.loadingView)?.visibility = View.GONE
            view.findViewById<LinearLayout>(R.id.answersContainer)?.visibility = View.VISIBLE
        }
    }

    private fun updateHeaderToAnswering() {
        if (hasStartedAnswering) return
        hasStartedAnswering = true
        handler.post {
            val view = popupView ?: return@post
            view.findViewById<TextView>(R.id.tvHeaderTitle)?.text = "解答中..."
        }
    }

    private fun checkAllAnswersComplete() {
        if (isAllAnswersComplete) return
        if (currentQuestions.isEmpty()) return

        // Check if all fast answers are complete (fast mode is always enabled)
        val allFastComplete = currentQuestions.all { question ->
            fastAnswers[question.id]?.isComplete == true
        }

        // Check if deep answers are complete (only if deep config is valid)
        val deepConfig = AISettings.getDeepConfig(this)
        val allDeepComplete = if (deepConfig.isValid() && deepConfig.apiKey.isNotBlank()) {
            currentQuestions.all { question ->
                deepAnswers[question.id]?.isComplete == true
            }
        } else {
            true // No deep mode, consider it complete
        }

        if (allFastComplete && allDeepComplete) {
            isAllAnswersComplete = true
            // Use updateHeaderForCurrentMode to respect the current mode's stopped state
            handler.post {
                updateHeaderForCurrentMode()
            }
        }
    }

    private fun animateActionButtonIcon(newIconRes: Int) {
        // Skip animation if icon is already the same
        if (newIconRes == currentActionIconRes) return
        currentActionIconRes = newIconRes

        val view = popupView ?: return
        val btnAction = view.findViewById<ImageView>(R.id.btnAction) ?: return

        // Scale down and fade out
        btnAction.animate()
            .scaleX(0.6f)
            .scaleY(0.6f)
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                // Change icon while invisible
                btnAction.setImageResource(newIconRes)
                // Scale up and fade in with overshoot
                btnAction.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.5f))
                    .start()
            }
            .start()
    }

    private fun showError(message: String) {
        handler.post {
            hideLoading()
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showReminder(message: String) {
        handler.post {
            hideLoading()
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            // Close the popup since no API is configured
            dismissWithAnimation()
        }
    }

    private fun showNoQuestionsDetected() {
        handler.post {
            hideLoading()
            val view = popupView ?: return@post
            val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return@post
            container.removeAllViews()

            // Create a centered message view
            val messageView = LayoutInflater.from(this)
                .inflate(R.layout.item_question_answer, container, false)

            messageView.findViewById<TextView>(R.id.tvQuestionTitle).apply {
                text = "未识别到题目"
                setTextColor(0xFFF44336.toInt()) // Red
            }
            messageView.findViewById<TextView>(R.id.tvQuestionText).visibility = View.GONE
            messageView.findViewById<TextView>(R.id.tvAnswerText).apply {
                text = "请切换到包含题目的界面后，点击「再拍一题」重新截图识别。\n\n" +
                        "提示：\n" +
                        "• 确保题目文字清晰可见\n" +
                        "• 避免截取过多无关内容\n" +
                        "• 支持数学、物理、化学等学科题目"
                setTextColor(0xFF757575.toInt())
            }

            container.addView(messageView)
        }
    }

    private fun displayQuestions(questions: List<Question>) {
        hideLoading()
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        container.removeAllViews()

        questions.forEachIndexed { index, question ->
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_question_answer, container, false)
            itemView.tag = "question_${question.id}"

            ensureQuestionModelInitialized(question.id, true)
            ensureQuestionModelInitialized(question.id, false)

            itemView.findViewById<TextView>(R.id.tvQuestionTitle).text = "问题${index + 1}"
            // Render question text with Markdown and LaTeX support (with newline conversion)
            val tvQuestionText = itemView.findViewById<TextView>(R.id.tvQuestionText)
            MarkdownRenderer.renderQuestionText(this@AnswerPopupService, tvQuestionText, question.text)
            itemView.findViewById<TextView>(R.id.tvAnswerText).text = ""
            itemView.findViewById<TextView>(R.id.tvThinkingText).text = ""
            itemView.findViewById<View>(R.id.thinkingSection).visibility = View.GONE
            applyThemeToQuestionCard(itemView)
            showBottomActionRow(itemView, question.id, isFastMode, showRetry = false)

            container.addView(itemView)

            // Store views for both modes
            fastAnswerViews[question.id] = itemView
        }
    }

    private fun displayAnswersForMode(isFast: Boolean) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return

        val answers = if (isFast) fastAnswers else deepAnswers

        // Update answer text and title for current mode
        currentQuestions.forEach { question ->
            val answer = answers[question.id]
            // Use tag-based lookup for more reliable view finding
            val answerView = container.findViewWithTag<View>("question_${question.id}")
                ?: run {
                    val index = currentQuestions.indexOf(question)
                    if (index >= 0 && index < container.childCount) {
                        container.getChildAt(index)
                    } else null
                }

            answerView?.findViewById<TextView>(R.id.tvAnswerText)?.let { textView ->
                // Show error message if error exists, otherwise show answer text
                val displayText = if (answer?.error != null) {
                    "错误: ${answer.error}"
                } else {
                    answer?.text ?: ""
                }
                if (displayText.isNotEmpty()) {
                    MarkdownRenderer.renderAIResponse(this@AnswerPopupService, textView, displayText)
                } else {
                    // Clear the cached content so that when switching back to a mode with content,
                    // the renderer won't skip rendering due to stale cache
                    MarkdownRenderer.clearViewCache(textView)
                    textView.text = ""
                }
            }

            answerView?.let {
                renderThinkingSection(it, question.id, answer, isFast)
            }

            // Update answer title based on completion and stopped status
            val titleText = when {
                answer?.error != null -> "请求错误"
                answer?.isStopped == true -> "已停止"
                answer?.isComplete == true -> "解答${question.id}"
                else -> "解答中..."
            }
            answerView?.findViewById<TextView>(R.id.tvAnswerTitle)?.text = titleText
            answerView?.let { updateModelSwitchButton(it, question.id, isFast) }

            // Show/hide retry buttons based on state
            when {
                answer?.isComplete == true || answer?.isStopped == true || answer?.error != null -> {
                    // Show both buttons for completed, stopped, or error states
                    answerView?.let { showRetryButton(it, question.id, isFast) }
                }
                !answer?.text.isNullOrEmpty() || answer?.isThinking == true || !answer?.thinkingText.isNullOrEmpty() -> {
                    // Streaming state: only show header button, not bottom button
                    answerView?.let { showHeaderRetryButton(it, question.id, isFast) }
                }
                else -> {
                    // Hide all retry buttons when not started yet
                    answerView?.let { hideRetryButtons(it, isFast) }
                }
            }
        }
    }

    private fun startSolvingBothModes(questions: List<Question>) {
        questions.forEach { question ->
            startSolvingQuestion(question)
        }
    }

    private fun startSolvingQuestion(question: Question) {
        ensureQuestionModelInitialized(question.id, true)
        ensureQuestionModelInitialized(question.id, false)
        val fastConfig = getModeConfig(question.id, true)
        val deepConfig = getModeConfig(question.id, false)

        if (!fastConfig.isValid() || fastConfig.apiKey.isBlank()) {
            return
        }

        // Initialize answers for this question
        fastAnswers[question.id] = Answer(question.id)
        deepAnswers[question.id] = Answer(question.id)

        // Start fast mode solving (only if not stopped)
        if (!isFastModeStopped) {
            val fastChatAPI = ChatAPI(fastConfig)
            val fastCall = fastChatAPI.solveQuestion(question, object : StreamingCallback {
                override fun onChunk(text: String) {
                    handler.post {
                        updateHeaderToAnswering()
                        fastAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            // Show header retry button when first chunk arrives (not bottom)
                            val isFirstChunk = answer.text.isEmpty()
                            answer.text += text
                            if (isFastMode) {
                                updateAnswerText(question.id, answer.text)
                                if (isFirstChunk) {
                                    showHeaderRetryButtonForQuestion(question.id)
                                }
                            }
                        }
                    }
                }

                override fun onComplete() {
                    handler.post {
                        fastCalls.remove(question.id)
                        fastAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            answer.isComplete = true
                        }
                        if (isFastMode) {
                            updateAnswerTitleComplete(question.id)
                            renderThinkingSectionForQuestion(question.id, fastAnswers[question.id], true)
                        }
                        checkAllAnswersComplete()
                    }
                }

                override fun onError(error: Exception) {
                    handler.post {
                        fastCalls.remove(question.id)
                        fastAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            answer.error = error.message
                            answer.isComplete = true
                            if (isFastMode) {
                                updateAnswerText(question.id, "错误: ${error.message}")
                                updateAnswerTitleWithError(question.id)
                                renderThinkingSectionForQuestion(question.id, answer, true)
                            }
                        }
                    }
                }
            })
            fastCalls[question.id] = fastCall
        }

        // Start deep mode solving (in parallel, only if not stopped)
        if (!isDeepModeStopped && deepConfig.isValid() && deepConfig.apiKey.isNotBlank()) {
            val deepChatAPI = ChatAPI(deepConfig)
            val deepCall = deepChatAPI.solveQuestion(question, object : StreamingCallback {
                override fun onThinkingStart() {
                    handler.post {
                        updateHeaderToAnswering()
                        deepAnswers[question.id]?.let { answer ->
                            markThinkingStarted(answer)
                            if (!isFastMode) {
                                renderThinkingSectionForQuestion(question.id, answer, false)
                            }
                        }
                    }
                }

                override fun onThinkingChunk(text: String) {
                    handler.post {
                        deepAnswers[question.id]?.let { answer ->
                            markThinkingStarted(answer)
                            answer.thinkingText += text
                            if (!isFastMode) {
                                renderThinkingSectionForQuestion(question.id, answer, false)
                            }
                        }
                    }
                }

                override fun onThinkingComplete() {
                    handler.post {
                        deepAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            if (!isFastMode) {
                                renderThinkingSectionForQuestion(question.id, answer, false)
                            }
                        }
                    }
                }

                override fun onChunk(text: String) {
                    handler.post {
                        updateHeaderToAnswering()
                        deepAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            // Show header retry button when first chunk arrives (not bottom)
                            val isFirstChunk = answer.text.isEmpty()
                            answer.text += text
                            if (!isFastMode) {
                                updateAnswerText(question.id, answer.text)
                                renderThinkingSectionForQuestion(question.id, answer, false)
                                if (isFirstChunk) {
                                    showHeaderRetryButtonForQuestion(question.id)
                                }
                            }
                        }
                    }
                }

                override fun onComplete() {
                    handler.post {
                        deepCalls.remove(question.id)
                        deepAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            answer.isComplete = true
                        }
                        if (!isFastMode) {
                            updateAnswerTitleComplete(question.id)
                            renderThinkingSectionForQuestion(question.id, deepAnswers[question.id], false)
                        }
                        checkAllAnswersComplete()
                    }
                }

                override fun onError(error: Exception) {
                    handler.post {
                        deepCalls.remove(question.id)
                        deepAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            answer.error = error.message
                            answer.isComplete = true
                            if (!isFastMode) {
                                updateAnswerText(question.id, "错误: ${error.message}")
                                updateAnswerTitleWithError(question.id)
                                renderThinkingSectionForQuestion(question.id, answer, false)
                            }
                        }
                    }
                }
            })
            deepCalls[question.id] = deepCall
        }
    }

    private fun updateAnswerText(questionId: Int, text: String) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return

        // Use tag-based lookup for more reliable view finding
        val itemView = container.findViewWithTag<View>("question_$questionId")
            ?: run {
                val index = currentQuestions.indexOfFirst { it.id == questionId }
                if (index >= 0 && index < container.childCount) {
                    container.getChildAt(index)
                } else null
            } ?: return
        itemView.findViewById<TextView>(R.id.tvAnswerText)?.let { textView ->
            MarkdownRenderer.renderAIResponse(this@AnswerPopupService, textView, text)
        }
    }

    private fun markThinkingStarted(answer: Answer) {
        if (!answer.isThinking) {
            answer.isThinking = true
            if (answer.thinkingStartAtMs <= 0L) {
                answer.thinkingStartAtMs = System.currentTimeMillis()
            }
        }
    }

    private fun markThinkingCompleted(answer: Answer) {
        val now = System.currentTimeMillis()
        if (answer.thinkingStartAtMs > 0L) {
            answer.thinkingDurationMs += (now - answer.thinkingStartAtMs).coerceAtLeast(0L)
            answer.thinkingStartAtMs = 0L
        }
        answer.isThinking = false
    }

    private fun formatThinkingDuration(durationMs: Long): String {
        if (durationMs <= 0L) return ""
        return if (durationMs < 1000L) {
            "${durationMs}ms"
        } else {
            String.format("%.1fs", durationMs / 1000f)
        }
    }

    private fun renderThinkingSectionForQuestion(questionId: Int, answer: Answer?, isFast: Boolean) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        val itemView = container.findViewWithTag<View>("question_$questionId")
            ?: run {
                val index = currentQuestions.indexOfFirst { it.id == questionId }
                if (index >= 0 && index < container.childCount) {
                    container.getChildAt(index)
                } else null
            } ?: return
        renderThinkingSection(itemView, questionId, answer, isFast)
    }

    private fun renderThinkingSection(itemView: View, questionId: Int, answer: Answer?, isFast: Boolean) {
        val thinkingSection = itemView.findViewById<View>(R.id.thinkingSection) ?: return
        val thinkingHeader = itemView.findViewById<View>(R.id.thinkingHeader)
        val tvThinkingTitle = itemView.findViewById<TextView>(R.id.tvThinkingTitle)
        val tvThinkingDuration = itemView.findViewById<TextView>(R.id.tvThinkingDuration)
        val tvThinkingToggle = itemView.findViewById<TextView>(R.id.tvThinkingToggle)
        val tvThinkingText = itemView.findViewById<TextView>(R.id.tvThinkingText)

        if (isFast || answer == null) {
            thinkingSection.visibility = View.GONE
            return
        }

        val hasThinking = answer.thinkingText.isNotBlank() || answer.isThinking || answer.thinkingDurationMs > 0L
        if (!hasThinking) {
            thinkingSection.visibility = View.GONE
            return
        }

        thinkingSection.visibility = View.VISIBLE
        applyThemeToQuestionCard(itemView)

        if (answer.isThinking && answer.thinkingStartAtMs <= 0L) {
            answer.thinkingStartAtMs = System.currentTimeMillis()
        }

        val duration = if (answer.isThinking && answer.thinkingStartAtMs > 0L) {
            (System.currentTimeMillis() - answer.thinkingStartAtMs).coerceAtLeast(0L)
        } else {
            answer.thinkingDurationMs
        }

        tvThinkingTitle?.text = if (answer.isThinking) "思考中..." else "思考过程"
        tvThinkingDuration?.text = formatThinkingDuration(duration)
        tvThinkingToggle?.text = if (answer.thinkingExpanded) "收起" else "展开"

        if (answer.thinkingExpanded) {
            tvThinkingText?.visibility = View.VISIBLE
            val thinkingText = answer.thinkingText.trim()
            if (thinkingText.isNotEmpty()) {
                MarkdownRenderer.renderAIResponse(this@AnswerPopupService, tvThinkingText, thinkingText)
            } else {
                tvThinkingText?.text = "正在思考..."
            }
        } else {
            tvThinkingText?.visibility = View.GONE
        }

        thinkingHeader?.setOnClickListener {
            answer.thinkingExpanded = !answer.thinkingExpanded
            renderThinkingSection(itemView, questionId, answer, isFast)
        }
    }

    private fun updateAnswerTitleComplete(questionId: Int) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return

        // Use tag-based lookup for more reliable view finding
        val itemView = container.findViewWithTag<View>("question_$questionId")
            ?: run {
                val index = currentQuestions.indexOfFirst { it.id == questionId }
                if (index >= 0 && index < container.childCount) {
                    container.getChildAt(index)
                } else null
            } ?: return
        itemView.findViewById<TextView>(R.id.tvAnswerTitle)?.text = "解答$questionId"
        // Show retry button and set click listener
        showRetryButton(itemView, questionId, isFastMode)
    }

    private fun showRetryButton(itemView: View, questionId: Int, isFast: Boolean = isFastMode) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        val bgRes = if (isLightGreenGray) R.drawable.bg_retry_button else R.drawable.bg_retry_button_light_brown_black

        // Header retry button
        val btnRetry = itemView.findViewById<TextView>(R.id.btnRetry)
        btnRetry?.let {
            it.setBackgroundResource(bgRes)
            it.visibility = View.VISIBLE
            it.setOnClickListener { retryQuestion(questionId) }
        }

        // Bottom actions: model switch + retry
        showBottomActionRow(itemView, questionId, isFast, showRetry = true)
    }

    // Show only header retry button (for streaming state)
    private fun showHeaderRetryButton(itemView: View, questionId: Int, isFast: Boolean = isFastMode) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        val bgRes = if (isLightGreenGray) R.drawable.bg_retry_button else R.drawable.bg_retry_button_light_brown_black

        // Show header button
        val btnRetry = itemView.findViewById<TextView>(R.id.btnRetry)
        btnRetry?.let {
            it.setBackgroundResource(bgRes)
            it.visibility = View.VISIBLE
            it.setOnClickListener { retryQuestion(questionId) }
        }

        // Bottom actions: keep model switch visible, hide retry button
        showBottomActionRow(itemView, questionId, isFast, showRetry = false)
    }

    // Show only header retry button for a question (for streaming state)
    private fun showHeaderRetryButtonForQuestion(questionId: Int) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        val itemView = container.findViewWithTag<View>("question_$questionId")
            ?: run {
                val index = currentQuestions.indexOfFirst { it.id == questionId }
                if (index >= 0 && index < container.childCount) {
                    container.getChildAt(index)
                } else null
            } ?: return
        showHeaderRetryButton(itemView, questionId, isFastMode)
    }

    private fun getQuestionIdFromItemView(itemView: View): Int? {
        val tag = itemView.tag as? String ?: return null
        return tag.removePrefix("question_").toIntOrNull()
    }

    private fun showBottomActionRow(itemView: View, questionId: Int, isFast: Boolean, showRetry: Boolean) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        val retryBgRes = if (isLightGreenGray) R.drawable.bg_retry_button else R.drawable.bg_retry_button_light_brown_black

        val bottomContainer = itemView.findViewById<View>(R.id.bottomRetryContainer)
        val btnCopy = itemView.findViewById<TextView>(R.id.btnCopyBottom)
        val btnModelSwitch = itemView.findViewById<TextView>(R.id.btnModelSwitchBottom)
        val btnRetryBottom = itemView.findViewById<TextView>(R.id.btnRetryBottom)

        // Show model switch row together with bottom retry button
        bottomContainer?.visibility = if (showRetry && questionId > 0) View.VISIBLE else View.GONE

        btnModelSwitch?.let {
            updateModelSwitchButton(itemView, questionId, isFast)
            it.setOnClickListener {
                if (isFastMode == isFast && questionId > 0) {
                    showModelSwitchMenu(it, questionId, isFast)
                }
            }
        }
        btnCopy?.setOnClickListener {
            if (questionId > 0) {
                showCopyMenu(it, questionId)
            }
        }

        btnRetryBottom?.let {
            if (showRetry) {
                it.visibility = View.VISIBLE
                it.setBackgroundResource(retryBgRes)
                it.setOnClickListener { retryQuestion(questionId) }
            } else {
                it.visibility = View.GONE
            }
        }
    }

    private fun updateModelSwitchButton(itemView: View, questionId: Int, isFast: Boolean) {
        val modelName = getSelectedModelForQuestion(questionId, isFast)
        val button = itemView.findViewById<TextView>(R.id.btnModelSwitchBottom) ?: return
        button.text = "$modelName ▾"
        button.visibility = View.VISIBLE
        applyThemeToQuestionCard(itemView)
    }

    private fun hideRetryButtons(
        itemView: View,
        isFast: Boolean = isFastMode,
        keepModelButton: Boolean = true
    ) {
        itemView.findViewById<TextView>(R.id.btnRetry)?.visibility = View.GONE
        if (keepModelButton) {
            val questionId = getQuestionIdFromItemView(itemView) ?: -1
            showBottomActionRow(itemView, questionId, isFast, showRetry = false)
        } else {
            itemView.findViewById<TextView>(R.id.btnRetryBottom)?.visibility = View.GONE
            itemView.findViewById<TextView>(R.id.btnModelSwitchBottom)?.visibility = View.GONE
            itemView.findViewById<View>(R.id.bottomRetryContainer)?.visibility = View.GONE
        }
    }

    private fun retryQuestion(questionId: Int) {
        val question = currentQuestions.find { it.id == questionId } ?: return

        // Cancel existing call for this question in current mode
        if (isFastMode) {
            fastCalls[questionId]?.cancel()
            fastCalls.remove(questionId)
            // Reset answer state
            fastAnswers[questionId] = Answer(questionId)
            isFastModeStopped = false  // Allow retry even if mode was stopped
        } else {
            deepCalls[questionId]?.cancel()
            deepCalls.remove(questionId)
            // Reset answer state
            deepAnswers[questionId] = Answer(questionId)
            isDeepModeStopped = false  // Allow retry even if mode was stopped
        }

        // Update UI to show retrying state
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        // Use tag-based lookup for more reliable view finding
        val itemView = container.findViewWithTag<View>("question_$questionId")
            ?: run {
                val index = currentQuestions.indexOfFirst { it.id == questionId }
                if (index >= 0 && index < container.childCount) {
                    container.getChildAt(index)
                } else null
            }
        itemView?.let {
            it.findViewById<TextView>(R.id.tvAnswerTitle)?.text = "解答中..."
            it.findViewById<TextView>(R.id.tvAnswerText)?.text = ""
            it.findViewById<TextView>(R.id.tvThinkingText)?.text = ""
            it.findViewById<View>(R.id.thinkingSection)?.visibility = View.GONE
            hideRetryButtons(it)
        }

        // Update header
        updateHeaderForCurrentMode()

        // Start solving again for current mode only
        retrySolvingQuestion(question)
    }

    private fun retrySolvingQuestion(question: Question) {
        ensureQuestionModelInitialized(question.id, isFastMode)
        val config = getModeConfig(question.id, isFastMode)

        if (!config.isValid() || config.apiKey.isBlank()) {
            return
        }

        // Capture current mode at start time to avoid issues if user switches mode during request
        val wasInFastMode = isFastMode

        val chatAPI = ChatAPI(config)
        val call = chatAPI.solveQuestion(question, object : StreamingCallback {
            override fun onThinkingStart() {
                if (wasInFastMode) return
                handler.post {
                    deepAnswers[question.id]?.let { answer ->
                        markThinkingStarted(answer)
                        if (!isFastMode) {
                            renderThinkingSectionForQuestion(question.id, answer, false)
                        }
                    }
                }
            }

            override fun onThinkingChunk(text: String) {
                if (wasInFastMode) return
                handler.post {
                    deepAnswers[question.id]?.let { answer ->
                        markThinkingStarted(answer)
                        answer.thinkingText += text
                        if (!isFastMode) {
                            renderThinkingSectionForQuestion(question.id, answer, false)
                        }
                    }
                }
            }

            override fun onThinkingComplete() {
                if (wasInFastMode) return
                handler.post {
                    deepAnswers[question.id]?.let { answer ->
                        markThinkingCompleted(answer)
                        if (!isFastMode) {
                            renderThinkingSectionForQuestion(question.id, answer, false)
                        }
                    }
                }
            }

            override fun onChunk(text: String) {
                handler.post {
                    val answers = if (wasInFastMode) fastAnswers else deepAnswers
                    answers[question.id]?.let { answer ->
                        markThinkingCompleted(answer)
                        // Show header retry button when first chunk arrives (not bottom)
                        val isFirstChunk = answer.text.isEmpty()
                        answer.text += text
                        // Only update UI if still viewing the same mode
                        if (isFastMode == wasInFastMode) {
                            updateAnswerText(question.id, answer.text)
                            renderThinkingSectionForQuestion(question.id, answer, wasInFastMode)
                            if (isFirstChunk) {
                                showHeaderRetryButtonForQuestion(question.id)
                            }
                        }
                    }
                }
            }

            override fun onComplete() {
                handler.post {
                    if (wasInFastMode) {
                        fastCalls.remove(question.id)
                        fastAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            answer.isComplete = true
                        }
                    } else {
                        deepCalls.remove(question.id)
                        deepAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            answer.isComplete = true
                        }
                    }
                    // Only update UI if still viewing the same mode
                    if (isFastMode == wasInFastMode) {
                        updateAnswerTitleComplete(question.id)
                        val answers = if (wasInFastMode) fastAnswers else deepAnswers
                        renderThinkingSectionForQuestion(question.id, answers[question.id], wasInFastMode)
                        updateHeaderForCurrentMode()
                    }
                    checkAllAnswersComplete()
                }
            }

            override fun onError(error: Exception) {
                handler.post {
                    if (wasInFastMode) {
                        fastCalls.remove(question.id)
                        fastAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            answer.error = error.message
                            answer.isComplete = true
                        }
                    } else {
                        deepCalls.remove(question.id)
                        deepAnswers[question.id]?.let { answer ->
                            markThinkingCompleted(answer)
                            answer.error = error.message
                            answer.isComplete = true
                        }
                    }
                    // Only update UI if still viewing the same mode
                    if (isFastMode == wasInFastMode) {
                        updateAnswerText(question.id, "错误: ${error.message}")
                        val answers = if (wasInFastMode) fastAnswers else deepAnswers
                        renderThinkingSectionForQuestion(question.id, answers[question.id], wasInFastMode)
                        updateAnswerTitleWithError(question.id)
                    }
                    checkAllAnswersComplete()
                }
            }
        })

        if (wasInFastMode) {
            fastCalls[question.id] = call
        } else {
            deepCalls[question.id] = call
        }
    }

    private fun updateAnswerTitleWithError(questionId: Int) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return

        // Use tag-based lookup for more reliable view finding
        val itemView = container.findViewWithTag<View>("question_$questionId")
            ?: run {
                val index = currentQuestions.indexOfFirst { it.id == questionId }
                if (index >= 0 && index < container.childCount) {
                    container.getChildAt(index)
                } else null
            } ?: return
        itemView.findViewById<TextView>(R.id.tvAnswerTitle)?.text = "请求错误"
        // Show retry button for error state
        showRetryButton(itemView, questionId)
    }

    private fun dismissPopup() {
        isPopupShowing = false
        cancelPendingScrollRestore()
        modelMenuPopup?.dismiss()
        modelMenuPopup = null
        copyMenuPopup?.dismiss()
        copyMenuPopup = null

        // Cancel all API requests first
        cancelAllRequests()

        try {
            // Hide views before removing to prevent flash
            overlayView?.visibility = View.GONE
            popupView?.visibility = View.GONE
            overlayView?.let { windowManager.removeView(it) }
            popupView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            // View might not be attached
        }
        overlayView = null
        popupView = null
        currentBitmap?.recycle()
        currentBitmap = null
        job.cancel()
        stopForeground(true)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isPopupShowing = false
        cancelPendingScrollRestore()
        modelMenuPopup?.dismiss()
        modelMenuPopup = null
        copyMenuPopup?.dismiss()
        copyMenuPopup = null

        // Cancel all API requests
        cancelAllRequests()

        try {
            overlayView?.let { windowManager.removeView(it) }
            popupView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            // Already removed
        }
        currentBitmap?.recycle()
        job.cancel()
    }
}

package com.yy.perfectfloatwindow.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.yy.perfectfloatwindow.R
import com.yy.perfectfloatwindow.data.AISettings
import com.yy.perfectfloatwindow.data.Answer
import com.yy.perfectfloatwindow.data.Question
import com.yy.perfectfloatwindow.data.ThemeManager
import com.yy.perfectfloatwindow.network.ChatAPI
import com.yy.perfectfloatwindow.network.OCRStreamingCallback
import com.yy.perfectfloatwindow.network.StreamingCallback
import com.yy.perfectfloatwindow.network.VisionAPI
import com.yy.perfectfloatwindow.screenshot.ScreenshotService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
            setupSwipeGesture()
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
            false // Let scroll view handle its own scrolling
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

    private fun applyPopupTheme() {
        val view = popupView ?: return
        val isChatGPT = ThemeManager.isChatGPTTheme(this)

        // Main container background
        val rootLayout = view as? LinearLayout

        // Tab area
        val tabAreaBg = view.findViewById<FrameLayout>(R.id.tabContainer)?.parent as? FrameLayout
        val tabContainer = view.findViewById<FrameLayout>(R.id.tabContainer)
        val tabIndicator = view.findViewById<View>(R.id.tabIndicator)
        val tabFast = view.findViewById<TextView>(R.id.tabFast)
        val tabDeep = view.findViewById<TextView>(R.id.tabDeep)

        // Bottom bar
        val bottomBar = view.findViewById<View>(R.id.btnRetake)?.parent?.parent as? LinearLayout

        // Scroll content area
        val scrollView = view.findViewById<View>(R.id.scrollView)
        val answersContainer = view.findViewById<LinearLayout>(R.id.answersContainer)

        if (isChatGPT) {
            // ChatGPT Theme - Light with green accent
            val primaryColor = 0xFF10A37F.toInt()
            val backgroundColor = 0xFFFFFFFF.toInt()
            val surfaceColor = 0xFFF5F5F5.toInt()
            val textPrimary = 0xFF202123.toInt()
            val textSecondary = 0xFF6E6E80.toInt()

            // Main background
            rootLayout?.setBackgroundResource(R.drawable.bg_answer_popup)

            // Background colors
            tabAreaBg?.setBackgroundColor(surfaceColor)
            bottomBar?.setBackgroundColor(backgroundColor)

            // Tab indicator - use green for ChatGPT theme
            tabIndicator?.setBackgroundResource(R.drawable.bg_tab_indicator_chatgpt)

            // Tab container - lighter background
            tabContainer?.setBackgroundResource(R.drawable.bg_tab_container_chatgpt)

            // Update tab text colors based on current mode
            if (isFastMode) {
                tabFast?.setTextColor(0xFFFFFFFF.toInt())
                tabDeep?.setTextColor(textSecondary)
            } else {
                tabFast?.setTextColor(textSecondary)
                tabDeep?.setTextColor(0xFFFFFFFF.toInt())
            }

            // Retake button
            view.findViewById<TextView>(R.id.btnRetake)?.setBackgroundResource(R.drawable.bg_button_retake_chatgpt)

        } else {
            // Claude Theme (浅棕黑)
            val primaryColor = 0xFF141413.toInt()
            val backgroundColor = 0xFFFAF9F5.toInt()
            val surfaceColor = 0xFFE8E5DF.toInt()
            val textPrimary = 0xFF141413.toInt()
            val textSecondary = 0xFF666666.toInt()

            // Main background
            rootLayout?.setBackgroundResource(R.drawable.bg_answer_popup_netflix)

            // Background colors
            tabAreaBg?.setBackgroundColor(backgroundColor)
            bottomBar?.setBackgroundColor(backgroundColor)

            // Tab indicator - dark for Claude theme
            tabIndicator?.setBackgroundResource(R.drawable.bg_tab_indicator_netflix)

            // Tab container - light background
            tabContainer?.setBackgroundResource(R.drawable.bg_tab_container_netflix)

            // Update tab text colors based on current mode
            if (isFastMode) {
                tabFast?.setTextColor(0xFFFFFFFF.toInt())
                tabDeep?.setTextColor(textSecondary)
            } else {
                tabFast?.setTextColor(textSecondary)
                tabDeep?.setTextColor(0xFFFFFFFF.toInt())
            }

            // Retake button
            view.findViewById<TextView>(R.id.btnRetake)?.setBackgroundResource(R.drawable.bg_button_retake_netflix)
        }
    }

    private fun switchToFastMode() {
        val view = popupView ?: return
        val tabFast = view.findViewById<TextView>(R.id.tabFast)
        val tabDeep = view.findViewById<TextView>(R.id.tabDeep)
        val container = view.findViewById<LinearLayout>(R.id.answersContainer)

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
    }

    private fun switchToDeepMode() {
        val view = popupView ?: return
        val tabFast = view.findViewById<TextView>(R.id.tabFast)
        val tabDeep = view.findViewById<TextView>(R.id.tabDeep)
        val container = view.findViewById<LinearLayout>(R.id.answersContainer)

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

    fun processBitmap(bitmap: Bitmap) {
        currentBitmap = bitmap
        // Reset cached answers
        fastAnswers.clear()
        deepAnswers.clear()
        fastAnswerViews.clear()
        deepAnswerViews.clear()
        isFastSolving = false
        isDeepSolving = false
        hasStartedAnswering = false
        currentQuestions = mutableListOf()

        showOCRStreaming()

        val config = AISettings.getOCRConfig(this@AnswerPopupService)
        if (!config.isValid() || config.apiKey.isBlank()) {
            showReminder("请先到设置中配置API Key")
            return
        }

        val visionAPI = VisionAPI(config)
        var currentStreamingIndex = 1

        visionAPI.extractQuestionsStreaming(bitmap, object : OCRStreamingCallback {
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

            override fun onComplete() {
                handler.post {
                    if (currentQuestions.isEmpty()) {
                        showNoQuestionsDetected()
                    }
                    // 题目已经在 onQuestionReady 中开始解答了
                }
            }

            override fun onError(error: Exception) {
                handler.post {
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

        val itemView = LayoutInflater.from(this)
            .inflate(R.layout.item_question_answer, container, false)
        itemView.tag = "question_$questionIndex"
        itemView.findViewById<TextView>(R.id.tvQuestionTitle).text = "识别中..."
        itemView.findViewById<TextView>(R.id.tvQuestionText).text = ""
        itemView.findViewById<TextView>(R.id.tvAnswerText).text = ""
        container.addView(itemView)
    }

    private fun appendOCRTextToQuestion(text: String, questionIndex: Int) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        val questionView = container.findViewWithTag<View>("question_$questionIndex") ?: return
        val tvQuestion = questionView.findViewById<TextView>(R.id.tvQuestionText)
        tvQuestion.append(text)
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
        tvQuestion.append(text)
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

            itemView.findViewById<TextView>(R.id.tvQuestionTitle).text = "问题${index + 1}"
            itemView.findViewById<TextView>(R.id.tvQuestionText).text = question.text
            itemView.findViewById<TextView>(R.id.tvAnswerText).text = ""

            container.addView(itemView)

            // Store views for both modes
            fastAnswerViews[question.id] = itemView
        }
    }

    private fun displayAnswersForMode(isFast: Boolean) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return

        val answers = if (isFast) fastAnswers else deepAnswers

        // Update answer text for current mode
        currentQuestions.forEach { question ->
            val answer = answers[question.id]
            val answerView = container.findViewWithTag<View>("answer_${question.id}")
                ?: container.getChildAt(currentQuestions.indexOf(question))

            answerView?.findViewById<TextView>(R.id.tvAnswerText)?.text = answer?.text ?: ""
        }
    }

    private fun startSolvingBothModes(questions: List<Question>) {
        questions.forEach { question ->
            startSolvingQuestion(question)
        }
    }

    private fun startSolvingQuestion(question: Question) {
        val fastConfig = AISettings.getFastConfig(this)
        val deepConfig = AISettings.getDeepConfig(this)

        if (!fastConfig.isValid() || fastConfig.apiKey.isBlank()) {
            return
        }

        // Initialize answers for this question
        fastAnswers[question.id] = Answer(question.id)
        deepAnswers[question.id] = Answer(question.id)

        // Start fast mode solving
        val fastChatAPI = ChatAPI(fastConfig)
        fastChatAPI.solveQuestion(question, object : StreamingCallback {
            override fun onChunk(text: String) {
                handler.post {
                    updateHeaderToAnswering()
                    fastAnswers[question.id]?.let { answer ->
                        answer.text += text
                        if (isFastMode) {
                            updateAnswerText(question.id, answer.text)
                        }
                    }
                }
            }

            override fun onComplete() {
                handler.post {
                    fastAnswers[question.id]?.isComplete = true
                }
            }

            override fun onError(error: Exception) {
                handler.post {
                    fastAnswers[question.id]?.let { answer ->
                        answer.error = error.message
                        if (isFastMode) {
                            updateAnswerText(question.id, "错误: ${error.message}")
                        }
                    }
                }
            }
        })

        // Start deep mode solving (in parallel)
        if (deepConfig.isValid() && deepConfig.apiKey.isNotBlank()) {
            val deepChatAPI = ChatAPI(deepConfig)
            deepChatAPI.solveQuestion(question, object : StreamingCallback {
                override fun onChunk(text: String) {
                    handler.post {
                        updateHeaderToAnswering()
                        deepAnswers[question.id]?.let { answer ->
                            answer.text += text
                            if (!isFastMode) {
                                updateAnswerText(question.id, answer.text)
                            }
                        }
                    }
                }

                override fun onComplete() {
                    handler.post {
                        deepAnswers[question.id]?.isComplete = true
                    }
                }

                override fun onError(error: Exception) {
                    handler.post {
                        deepAnswers[question.id]?.let { answer ->
                            answer.error = error.message
                            if (!isFastMode) {
                                updateAnswerText(question.id, "错误: ${error.message}")
                            }
                        }
                    }
                }
            })
        }
    }

    private fun updateAnswerText(questionId: Int, text: String) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return

        val index = currentQuestions.indexOfFirst { it.id == questionId }
        if (index >= 0 && index < container.childCount) {
            container.getChildAt(index)?.findViewById<TextView>(R.id.tvAnswerText)?.text = text
        }
    }

    private fun dismissPopup() {
        isPopupShowing = false
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

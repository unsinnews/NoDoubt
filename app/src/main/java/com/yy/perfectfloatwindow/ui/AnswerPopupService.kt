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
import android.widget.ImageView
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
        // Check if OCR is still running (no questions ready yet or OCR call active)
        val isOCRPhase = ocrCall != null || currentQuestions.isEmpty()

        if (isOCRPhase) {
            // In OCR phase, stop everything (both modes)
            stopAllRequests()
            return
        }

        // Normal mode: only stop current mode's requests
        if (isFastMode) {
            // Stop fast mode requests
            fastCalls.values.forEach { it.cancel() }
            fastCalls.clear()
            isFastModeStopped = true
            // Mark all fast answers as stopped
            currentQuestions.forEach { question ->
                fastAnswers[question.id]?.let {
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

            // Update answer titles to show stopped state and show retry buttons
            val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return@post
            currentQuestions.forEach { question ->
                val index = currentQuestions.indexOf(question)
                if (index >= 0 && index < container.childCount) {
                    val itemView = container.getChildAt(index) ?: return@forEach
                    itemView.findViewById<TextView>(R.id.tvAnswerTitle)?.text = "已停止"
                    showRetryButton(itemView, question.id)
                }
            }
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
                it.isComplete = true
                it.isStopped = true
            }
            deepAnswers[question.id]?.let {
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
                    hideRetryButtons(childView)
                }
            }
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
        updateHeaderForCurrentMode()
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

    fun processBitmap(bitmap: Bitmap) {
        currentBitmap = bitmap
        // Reset cached answers
        fastAnswers.clear()
        deepAnswers.clear()
        fastAnswerViews.clear()
        deepAnswerViews.clear()
        questionTexts.clear()
        isFastSolving = false
        isDeepSolving = false
        hasStartedAnswering = false
        isAllAnswersComplete = false
        currentQuestions = mutableListOf()
        currentActionIconRes = R.drawable.ic_stop_white  // Reset icon state

        // Reset stopped states and clear previous calls
        isFastModeStopped = false
        isDeepModeStopped = false
        cancelAllRequests()

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

        val itemView = LayoutInflater.from(this)
            .inflate(R.layout.item_question_answer, container, false)
        itemView.tag = "question_$questionIndex"
        itemView.findViewById<TextView>(R.id.tvQuestionTitle).text = "识别中..."
        itemView.findViewById<TextView>(R.id.tvQuestionText).text = ""
        itemView.findViewById<TextView>(R.id.tvAnswerText).text = ""

        // Apply theme to question card
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        itemView.findViewById<View>(R.id.questionSection)?.setBackgroundResource(
            if (isLightGreenGray) R.drawable.bg_question_card
            else R.drawable.bg_question_card_light_brown_black
        )

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

        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)

        questions.forEachIndexed { index, question ->
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_question_answer, container, false)

            itemView.findViewById<TextView>(R.id.tvQuestionTitle).text = "问题${index + 1}"
            // Render question text with Markdown and LaTeX support (with newline conversion)
            val tvQuestionText = itemView.findViewById<TextView>(R.id.tvQuestionText)
            MarkdownRenderer.renderQuestionText(this@AnswerPopupService, tvQuestionText, question.text)
            itemView.findViewById<TextView>(R.id.tvAnswerText).text = ""

            // Apply theme to question card
            itemView.findViewById<View>(R.id.questionSection)?.setBackgroundResource(
                if (isLightGreenGray) R.drawable.bg_question_card
                else R.drawable.bg_question_card_light_brown_black
            )

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
            val index = currentQuestions.indexOf(question)
            val answerView = if (index >= 0 && index < container.childCount) {
                container.getChildAt(index)
            } else {
                container.findViewWithTag<View>("question_${question.id}")
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
                    textView.text = ""
                }
            }

            // Update answer title based on completion and stopped status
            val titleText = when {
                answer?.error != null -> "请求错误"
                answer?.isStopped == true -> "已停止"
                answer?.isComplete == true -> "解答${question.id}"
                else -> "解答中..."
            }
            answerView?.findViewById<TextView>(R.id.tvAnswerTitle)?.text = titleText

            // Show/hide retry buttons based on state
            if (answer?.isComplete == true || answer?.isStopped == true || answer?.error != null) {
                // Show both header and bottom retry buttons for completed, stopped, or error states
                answerView?.let { showRetryButton(it, question.id) }
            } else if (!answer?.text.isNullOrEmpty()) {
                // Show only bottom retry button when answer is streaming (has content but not complete)
                answerView?.let {
                    it.findViewById<TextView>(R.id.btnRetry)?.visibility = View.GONE
                    showBottomRetryButton(it, question.id)
                }
            } else {
                // Hide all retry buttons when not started yet
                answerView?.let { hideRetryButtons(it) }
            }
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

        // Start fast mode solving (only if not stopped)
        if (!isFastModeStopped) {
            val fastChatAPI = ChatAPI(fastConfig)
            val fastCall = fastChatAPI.solveQuestion(question, object : StreamingCallback {
                override fun onChunk(text: String) {
                    handler.post {
                        updateHeaderToAnswering()
                        fastAnswers[question.id]?.let { answer ->
                            // Show bottom retry button when first chunk arrives
                            val isFirstChunk = answer.text.isEmpty()
                            answer.text += text
                            if (isFastMode) {
                                updateAnswerText(question.id, answer.text)
                                if (isFirstChunk) {
                                    showBottomRetryButtonForQuestion(question.id)
                                }
                            }
                        }
                    }
                }

                override fun onComplete() {
                    handler.post {
                        fastCalls.remove(question.id)
                        fastAnswers[question.id]?.isComplete = true
                        if (isFastMode) {
                            updateAnswerTitleComplete(question.id)
                        }
                        checkAllAnswersComplete()
                    }
                }

                override fun onError(error: Exception) {
                    handler.post {
                        fastCalls.remove(question.id)
                        fastAnswers[question.id]?.let { answer ->
                            answer.error = error.message
                            answer.isComplete = true
                            if (isFastMode) {
                                updateAnswerText(question.id, "错误: ${error.message}")
                                updateAnswerTitleWithError(question.id)
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
                override fun onChunk(text: String) {
                    handler.post {
                        updateHeaderToAnswering()
                        deepAnswers[question.id]?.let { answer ->
                            // Show bottom retry button when first chunk arrives
                            val isFirstChunk = answer.text.isEmpty()
                            answer.text += text
                            if (!isFastMode) {
                                updateAnswerText(question.id, answer.text)
                                if (isFirstChunk) {
                                    showBottomRetryButtonForQuestion(question.id)
                                }
                            }
                        }
                    }
                }

                override fun onComplete() {
                    handler.post {
                        deepCalls.remove(question.id)
                        deepAnswers[question.id]?.isComplete = true
                        if (!isFastMode) {
                            updateAnswerTitleComplete(question.id)
                        }
                        checkAllAnswersComplete()
                    }
                }

                override fun onError(error: Exception) {
                    handler.post {
                        deepCalls.remove(question.id)
                        deepAnswers[question.id]?.let { answer ->
                            answer.error = error.message
                            answer.isComplete = true
                            if (!isFastMode) {
                                updateAnswerText(question.id, "错误: ${error.message}")
                                updateAnswerTitleWithError(question.id)
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

        val index = currentQuestions.indexOfFirst { it.id == questionId }
        if (index >= 0 && index < container.childCount) {
            container.getChildAt(index)?.findViewById<TextView>(R.id.tvAnswerText)?.let { textView ->
                MarkdownRenderer.renderAIResponse(this@AnswerPopupService, textView, text)
            }
        }
    }

    private fun updateAnswerTitleComplete(questionId: Int) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return

        val index = currentQuestions.indexOfFirst { it.id == questionId }
        if (index >= 0 && index < container.childCount) {
            val itemView = container.getChildAt(index) ?: return
            itemView.findViewById<TextView>(R.id.tvAnswerTitle)?.text = "解答$questionId"
            // Show retry button and set click listener
            showRetryButton(itemView, questionId)
        }
    }

    private fun showRetryButton(itemView: View, questionId: Int) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        val bgRes = if (isLightGreenGray) R.drawable.bg_retry_button else R.drawable.bg_retry_button_light_brown_black

        // Header retry button
        val btnRetry = itemView.findViewById<TextView>(R.id.btnRetry)
        btnRetry?.let {
            it.setBackgroundResource(bgRes)
            it.visibility = View.VISIBLE
            it.setOnClickListener { retryQuestion(questionId) }
        }

        // Bottom retry button (at end of answer)
        showBottomRetryButton(itemView, questionId)
    }

    private fun showBottomRetryButton(itemView: View, questionId: Int) {
        val isLightGreenGray = ThemeManager.isLightGreenGrayTheme(this)
        val bgRes = if (isLightGreenGray) R.drawable.bg_retry_button else R.drawable.bg_retry_button_light_brown_black

        val bottomContainer = itemView.findViewById<View>(R.id.bottomRetryContainer)
        val btnRetryBottom = itemView.findViewById<TextView>(R.id.btnRetryBottom)
        bottomContainer?.visibility = View.VISIBLE
        btnRetryBottom?.let {
            it.setBackgroundResource(bgRes)
            it.setOnClickListener { retryQuestion(questionId) }
        }
    }

    private fun showBottomRetryButtonForQuestion(questionId: Int) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        val index = currentQuestions.indexOfFirst { it.id == questionId }
        if (index >= 0 && index < container.childCount) {
            val itemView = container.getChildAt(index) ?: return
            showBottomRetryButton(itemView, questionId)
        }
    }

    private fun hideRetryButtons(itemView: View) {
        itemView.findViewById<TextView>(R.id.btnRetry)?.visibility = View.GONE
        itemView.findViewById<View>(R.id.bottomRetryContainer)?.visibility = View.GONE
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
        val index = currentQuestions.indexOfFirst { it.id == questionId }
        if (index >= 0 && index < container.childCount) {
            val itemView = container.getChildAt(index) ?: return
            itemView.findViewById<TextView>(R.id.tvAnswerTitle)?.text = "解答中..."
            itemView.findViewById<TextView>(R.id.tvAnswerText)?.text = ""
            hideRetryButtons(itemView)
        }

        // Update header
        updateHeaderForCurrentMode()

        // Start solving again for current mode only
        retrySolvingQuestion(question)
    }

    private fun retrySolvingQuestion(question: Question) {
        val config = if (isFastMode) {
            AISettings.getFastConfig(this)
        } else {
            AISettings.getDeepConfig(this)
        }

        if (!config.isValid() || config.apiKey.isBlank()) {
            return
        }

        // Capture current mode at start time to avoid issues if user switches mode during request
        val wasInFastMode = isFastMode

        val chatAPI = ChatAPI(config)
        val call = chatAPI.solveQuestion(question, object : StreamingCallback {
            override fun onChunk(text: String) {
                handler.post {
                    val answers = if (wasInFastMode) fastAnswers else deepAnswers
                    answers[question.id]?.let { answer ->
                        // Show bottom retry button when first chunk arrives
                        val isFirstChunk = answer.text.isEmpty()
                        answer.text += text
                        // Only update UI if still viewing the same mode
                        if (isFastMode == wasInFastMode) {
                            updateAnswerText(question.id, answer.text)
                            if (isFirstChunk) {
                                showBottomRetryButtonForQuestion(question.id)
                            }
                        }
                    }
                }
            }

            override fun onComplete() {
                handler.post {
                    if (wasInFastMode) {
                        fastCalls.remove(question.id)
                        fastAnswers[question.id]?.isComplete = true
                    } else {
                        deepCalls.remove(question.id)
                        deepAnswers[question.id]?.isComplete = true
                    }
                    // Only update UI if still viewing the same mode
                    if (isFastMode == wasInFastMode) {
                        updateAnswerTitleComplete(question.id)
                        updateHeaderForCurrentMode()
                    }
                }
            }

            override fun onError(error: Exception) {
                handler.post {
                    if (wasInFastMode) {
                        fastCalls.remove(question.id)
                        fastAnswers[question.id]?.let { answer ->
                            answer.error = error.message
                            answer.isComplete = true
                        }
                    } else {
                        deepCalls.remove(question.id)
                        deepAnswers[question.id]?.let { answer ->
                            answer.error = error.message
                            answer.isComplete = true
                        }
                    }
                    // Only update UI if still viewing the same mode
                    if (isFastMode == wasInFastMode) {
                        updateAnswerText(question.id, "错误: ${error.message}")
                        updateAnswerTitleWithError(question.id)
                    }
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

        val index = currentQuestions.indexOfFirst { it.id == questionId }
        if (index >= 0 && index < container.childCount) {
            val itemView = container.getChildAt(index) ?: return
            itemView.findViewById<TextView>(R.id.tvAnswerTitle)?.text = "请求错误"
            // Show retry button for error state
            showRetryButton(itemView, questionId)
        }
    }

    private fun dismissPopup() {
        isPopupShowing = false

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

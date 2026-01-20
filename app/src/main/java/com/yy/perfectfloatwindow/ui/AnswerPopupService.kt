package com.yy.perfectfloatwindow.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.yy.perfectfloatwindow.R
import com.yy.perfectfloatwindow.data.AISettings
import com.yy.perfectfloatwindow.data.Answer
import com.yy.perfectfloatwindow.data.Question
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
                setOnClickListener { dismissWithAnimation() }
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
                y = initialPopupHeight // Start off screen (below bottom)
            }

            windowManager.addView(overlayView, overlayParams)
            windowManager.addView(popupView, popupParams)
            isPopupShowing = true
            isDismissing = false

            setupDragHandle()
            setupTabs()
            setupRetakeButton()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to show popup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showWithAnimation() {
        // Fade in overlay
        overlayView?.alpha = 0f
        overlayView?.animate()?.alpha(1f)?.setDuration(250)?.start()

        // Slide up popup from bottom
        val startY = initialPopupHeight
        val animator = ValueAnimator.ofInt(startY, 0)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            if (popupView != null && isPopupShowing) {
                popupParams.y = animation.animatedValue as Int
                try {
                    windowManager.updateViewLayout(popupView, popupParams)
                } catch (e: Exception) {
                    // View might be detached
                }
            }
        }
        animator.start()
    }

    private fun dismissWithAnimation() {
        if (isDismissing || !isPopupShowing) return
        isDismissing = true

        // Fade out overlay
        overlayView?.animate()?.alpha(0f)?.setDuration(200)?.start()

        // Slide down popup - use current y position as start
        val currentY = popupParams.y
        val targetY = popupParams.height
        val animator = ValueAnimator.ofInt(currentY, targetY)
        animator.duration = 250
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            if (popupView != null) {
                popupParams.y = animation.animatedValue as Int
                try {
                    windowManager.updateViewLayout(popupView, popupParams)
                } catch (e: Exception) {
                    // View might be detached
                }
            }
        }
        animator.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                dismissPopup()
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {
                dismissPopup()
            }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        animator.start()
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

        tabFast.setOnClickListener {
            if (!isFastMode) {
                isFastMode = true
                tabFast.setBackgroundResource(R.drawable.bg_tab_selected)
                tabFast.setTextColor(0xFFFFFFFF.toInt())
                tabDeep.setBackgroundResource(R.drawable.bg_tab_unselected)
                tabDeep.setTextColor(0xFF757575.toInt())

                // Switch to fast mode display
                displayAnswersForMode(true)
            }
        }

        tabDeep.setOnClickListener {
            if (isFastMode) {
                isFastMode = false
                tabDeep.setBackgroundResource(R.drawable.bg_tab_selected)
                tabDeep.setTextColor(0xFFFFFFFF.toInt())
                tabFast.setBackgroundResource(R.drawable.bg_tab_unselected)
                tabFast.setTextColor(0xFF757575.toInt())

                // Switch to deep mode display
                displayAnswersForMode(false)
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

    fun processBitmap(bitmap: Bitmap) {
        currentBitmap = bitmap
        // Reset cached answers
        fastAnswers.clear()
        deepAnswers.clear()
        fastAnswerViews.clear()
        deepAnswerViews.clear()
        isFastSolving = false
        isDeepSolving = false

        showOCRStreaming()

        val config = AISettings.getOCRConfig(this@AnswerPopupService)
        if (!config.isValid() || config.apiKey.isBlank()) {
            showError("请先在设置中配置API")
            return
        }

        val visionAPI = VisionAPI(config)
        visionAPI.extractQuestionsStreaming(bitmap, object : OCRStreamingCallback {
            override fun onChunk(text: String) {
                handler.post {
                    appendOCRText(text)
                }
            }

            override fun onQuestionsReady(questions: List<Question>) {
                handler.post {
                    if (questions.isEmpty()) {
                        showError("未能识别到题目")
                    } else {
                        currentQuestions = questions
                        displayQuestions(questions)
                        // Start solving for both modes simultaneously
                        startSolvingBothModes(questions)
                    }
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

            // Add a streaming OCR text view
            val ocrView = LayoutInflater.from(this)
                .inflate(R.layout.item_question_answer, container, false)
            ocrView.tag = "ocr_streaming"
            ocrView.findViewById<TextView>(R.id.tvQuestionTitle).text = "识别中..."
            ocrView.findViewById<TextView>(R.id.tvQuestionText).visibility = View.GONE
            ocrView.findViewById<TextView>(R.id.tvAnswerText).text = ""
            container.addView(ocrView)
        }
    }

    private fun appendOCRText(text: String) {
        val view = popupView ?: return
        val container = view.findViewById<LinearLayout>(R.id.answersContainer) ?: return
        val ocrView = container.findViewWithTag<View>("ocr_streaming") ?: return
        val tvAnswer = ocrView.findViewById<TextView>(R.id.tvAnswerText)
        tvAnswer.append(text)
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

    private fun showError(message: String) {
        handler.post {
            hideLoading()
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        showLoading("正在解答...")

        val fastConfig = AISettings.getFastConfig(this)
        val deepConfig = AISettings.getDeepConfig(this)

        if (!fastConfig.isValid() || fastConfig.apiKey.isBlank()) {
            showError("请先在设置中配置API")
            return
        }

        // Initialize answers for both modes
        questions.forEach { question ->
            fastAnswers[question.id] = Answer(question.id)
            deepAnswers[question.id] = Answer(question.id)
        }

        // Start fast mode solving
        isFastSolving = true
        val fastChatAPI = ChatAPI(fastConfig)
        questions.forEachIndexed { index, question ->
            handler.postDelayed({
                if (index == 0) hideLoading()

                fastChatAPI.solveQuestion(question, object : StreamingCallback {
                    override fun onChunk(text: String) {
                        handler.post {
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
            }, index * 300L)
        }

        // Start deep mode solving (in parallel)
        if (deepConfig.isValid() && deepConfig.apiKey.isNotBlank()) {
            isDeepSolving = true
            val deepChatAPI = ChatAPI(deepConfig)
            questions.forEachIndexed { index, question ->
                handler.postDelayed({
                    deepChatAPI.solveQuestion(question, object : StreamingCallback {
                        override fun onChunk(text: String) {
                            handler.post {
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
                }, index * 300L)
            }
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

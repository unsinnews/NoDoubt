package com.nodoubt.platform.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import com.nodoubt.platform.R
import kotlin.math.abs
import kotlin.math.roundToInt

class BubbleWindow(private val context: Context, private val onClick: () -> Unit) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val preferences = context.applicationContext.getSharedPreferences("nodoubt_overlay_position", Context.MODE_PRIVATE)
    private val density get() = context.resources.displayMetrics.density
    private var position = readPosition()
    private var geometry: BubblePositioning? = null
    private var requestedSizeDp = 48
    private var attached = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private val params = WindowManager.LayoutParams(
        (48 * density).roundToInt(), (48 * density).roundToInt(),
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        // Drag coordinates are physical, including under RTL; safe insets are applied below.
        gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= 30) {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    private val view = ImageView(context).apply {
        setImageResource(R.drawable.ic_bubble)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = "截图识题，可拖动"
        elevation = 4 * density
        setOnClickListener { onClick() }
        setOnTouchListener { _, event -> touch(event) }
        setOnApplyWindowInsetsListener { _, insets ->
            refreshPosition()
            insets
        }
    }

    fun show(sizeDp: Int, background: Int, foreground: Int, border: Int) {
        requestedSizeDp = sizeDp.coerceIn(48, 80)
        refreshPosition(updateWindow = false)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(background); setStroke(density.roundToInt().coerceAtLeast(1), border)
        }
        view.setColorFilter(foreground)
        if (!attached) { manager.addView(view, params); attached = true }
        else manager.updateViewLayout(view, params)
        view.visibility = View.VISIBLE
    }

    fun hide() { if (attached) view.visibility = View.GONE }
    fun reveal() {
        if (attached) { refreshPosition(); view.visibility = View.VISIBLE }
    }
    fun close() {
        savePosition()
        if (attached) { manager.removeView(view); attached = false }
    }

    private fun refreshPosition(updateWindow: Boolean = true) {
        geometry = BubblePositioning(screenBounds(), (requestedSizeDp * density).roundToInt(), (12 * density).roundToInt())
        val inset = (12 * density).roundToInt()
        view.setPadding(inset, inset, inset, inset)
        applyPlacement(requireNotNull(geometry).place(position), updateWindow)
    }

    private fun applyPlacement(next: BubblePlacement, updateWindow: Boolean = true) {
        val changed = params.x != next.x || params.y != next.y || params.width != next.size || params.height != next.size
        params.x = next.x; params.y = next.y; params.width = next.size; params.height = next.size
        if (changed && attached && updateWindow) manager.updateViewLayout(view, params)
    }

    private fun touch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX; val dy = event.rawY - downY
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (abs(dx) > slop || abs(dy) > slop) moved = true
                if (moved) dragTo(event.rawX, event.rawY)
            }
            MotionEvent.ACTION_UP -> if (moved) {
                // The final move can be coalesced on a busy device; release is authoritative.
                dragTo(event.rawX, event.rawY)
                savePosition()
            } else view.performClick()
            MotionEvent.ACTION_CANCEL -> { if (moved) savePosition(); moved = true }
        }
        return true
    }

    private fun dragTo(rawX: Float, rawY: Float) {
        geometry?.let { limits ->
            val next = limits.clamp((startX + rawX - downX).roundToInt(), (startY + rawY - downY).roundToInt())
            position = limits.relative(next.x, next.y)
            applyPlacement(next)
        }
    }

    private fun readPosition(): BubbleRelativePosition? = runCatching {
        if (!preferences.contains("x") || !preferences.contains("y")) null
        else BubbleRelativePosition(preferences.getFloat("x", 1f), preferences.getFloat("y", .75f))
            .takeIf { it.x.isFinite() && it.y.isFinite() }
    }.getOrNull()

    private fun savePosition() {
        position?.let { preferences.edit().putFloat("x", it.x.coerceIn(0f, 1f)).putFloat("y", it.y.coerceIn(0f, 1f)).apply() }
    }

    @Suppress("DEPRECATION")
    private fun screenBounds(): BubbleScreenBounds {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return BubbleScreenBounds(metrics.bounds.width(), metrics.bounds.height(), insets.left, insets.top, insets.right, insets.bottom)
        }
        val display = manager.defaultDisplay
        val real = DisplayMetrics().also { display.getRealMetrics(it) }
        val available = DisplayMetrics().also { display.getMetrics(it) }
        val cutout = if (Build.VERSION.SDK_INT >= 29) display.cutout else null
        val statusId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusHeight = if (statusId == 0) 0 else context.resources.getDimensionPixelSize(statusId)
        return legacyBubbleScreenBounds(real.widthPixels, real.heightPixels, available.widthPixels, available.heightPixels,
            statusHeight, navigationOnLeft = display.rotation == Surface.ROTATION_270,
            cutout = BubbleSystemInsets(cutout?.safeInsetLeft ?: 0, cutout?.safeInsetTop ?: 0,
                cutout?.safeInsetRight ?: 0, cutout?.safeInsetBottom ?: 0))
    }
}

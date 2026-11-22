package com.nodoubt.platform.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Colors of the current theme; overlays follow the app theme too. */
data class BubbleMenuColors(val background: Int, val text: Int, val muted: Int, val danger: Int, val divider: Int)

data class BubbleMenuItem(val label: String, val enabled: Boolean = true, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * The bubble's long-press menu: a compact card beside the bubble over a transparent full-screen catcher.
 * Tapping outside closes it. It is a plain View so it needs no lifecycle owner.
 */
class BubbleMenu(private val context: Context) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density get() = context.resources.displayMetrics.density
    private var root: FrameLayout? = null
    val showing: Boolean get() = root != null

    fun show(anchor: Rect, colors: BubbleMenuColors, items: List<BubbleMenuItem>) {
        dismiss()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = 14 * density; setColor(colors.background) }
            elevation = 12 * density
            clipToOutline = true
        }
        items.forEachIndexed { index, item ->
            if (index > 0) card.addView(View(context).apply { setBackgroundColor(colors.divider) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, density.roundToInt().coerceAtLeast(1)))
            card.addView(row(item, colors), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        }
        val width = dp(228)
        val height = items.size * dp(52) + (items.size - 1)
        val frame = FrameLayout(context).apply {
            // Transparent catcher: any tap outside the card closes the menu.
            setOnClickListener { dismiss() }
            contentDescription = "关闭菜单"
        }
        val metrics = context.resources.displayMetrics
        val gap = dp(8)
        val onRight = anchor.centerX() > metrics.widthPixels / 2
        val x = (if (onRight) anchor.right - width else anchor.left).coerceIn(gap, metrics.widthPixels - width - gap)
        val below = anchor.bottom + gap + height <= metrics.heightPixels - dp(48)
        val y = (if (below) anchor.bottom + gap else anchor.top - gap - height).coerceAtLeast(gap)
        frame.addView(card, FrameLayout.LayoutParams(width, height).apply { leftMargin = x; topMargin = y })
        card.setOnClickListener { }
        manager.addView(frame, params())
        root = frame
        card.alpha = 0f
        card.scaleX = .92f
        card.scaleY = .92f
        card.pivotX = if (onRight) width.toFloat() else 0f
        card.pivotY = if (below) 0f else height.toFloat()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
    }

    fun dismiss() {
        val view = root ?: return
        root = null
        runCatching { manager.removeView(view) }
    }

    private fun row(item: BubbleMenuItem, colors: BubbleMenuColors) = TextView(context).apply {
        text = item.label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = if (item.danger) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(when { !item.enabled -> colors.muted; item.danger -> colors.danger; else -> colors.text })
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), 0, dp(18), 0)
        isEnabled = item.enabled
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(40, 128, 128, 128)), null, ColorDrawable(Color.WHITE))
        setOnClickListener { dismiss(); item.onClick() }
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        // Same physical coordinates as the bubble window, so the card sits right beside it.
        gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= 30) {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun dp(value: Int) = (value * density).roundToInt()
}

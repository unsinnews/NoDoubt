package com.nodoubt.platform.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/**
 * Service-owned bottom answer sheet with its own full-screen dismiss backdrop.
 *
 * The window keeps the tallest sheet height and the visible sheet is sized inside it by Compose.
 * Resizing the window itself on every drag event re-laid out the surface asynchronously, so the
 * bottom bar was drawn at stale positions and jumped; resizing in Compose happens within one frame.
 * Taps on the transparent area above the sheet dismiss it, like the backdrop.
 */
class OverlayWindow(private val context: Context) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val view = ComposeView(context)
    private val backdrop = View(context).apply {
        setBackgroundColor(Color.argb(128, 0, 0, 0))
        contentDescription = "关闭答案"
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private var attached = false
    private var closed = false
    private var dismiss: () -> Unit = {}
    private var screenHeight = displayHeight()
    private var requestedHeightFraction = 2f / 3f
    private var inputEnabled = false
    private var sheetHeight by mutableIntStateOf(0)
    val heightFraction: Float get() = sheetHeight.toFloat() / screenHeight.coerceAtLeast(1)
    private val windowType = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    private val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    private val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
        (screenHeight * MAX_FRACTION).roundToInt(), windowType, flags, PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.BOTTOM
        x = 0
        y = 0
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }
    private val backdropParams = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT, windowType, flags, PixelFormat.TRANSLUCENT)

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        backdrop.setOnClickListener { dismiss() }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /** Calling code cancels active work in onDismiss, then calls hide(). */
    fun show(onDismiss: () -> Unit = {}, content: @Composable () -> Unit) {
        check(!closed) { "This answer window has been closed." }
        dismiss = onDismiss
        screenHeight = displayHeight()
        requestedHeightFraction = 2f / 3f
        params.height = windowHeight()
        sheetHeight = requestedSheetHeight()
        view.setContent { SheetFrame(content) }
        val wasVisible = attached && view.visibility == View.VISIBLE
        if (!attached) {
            manager.addView(backdrop, backdropParams)
            try { manager.addView(view, params) }
            catch (error: Exception) { manager.removeView(backdrop); throw error }
            attached = true
        } else manager.updateViewLayout(view, params)
        view.animate().cancel()
        backdrop.animate().cancel()
        backdrop.visibility = View.VISIBLE
        view.visibility = View.VISIBLE
        registry.currentState = Lifecycle.State.RESUMED
        if (!wasVisible && animationsEnabled()) {
            backdrop.alpha = 0f
            view.translationY = sheetHeight.toFloat()
            backdrop.animate().alpha(1f).setDuration(200).start()
            view.animate().translationY(0f).setDuration(220).start()
        } else {
            backdrop.alpha = 1f
            view.translationY = 0f
        }
    }

    /** Immediate hiding is also used before taking a clean screenshot. */
    fun hide() {
        if (closed) return
        setInputEnabled(false)
        view.animate().cancel()
        backdrop.animate().cancel()
        if (attached) {
            view.visibility = View.GONE
            backdrop.visibility = View.GONE
        }
        registry.currentState = Lifecycle.State.CREATED
    }

    /** Only the inline budget editor takes keyboard focus; outside taps still reach the backdrop. */
    fun setInputEnabled(enabled: Boolean) {
        if (closed) return
        inputEnabled = enabled
        if (!enabled) {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
        val next = if (enabled) flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() else flags
        // A non-focusable backdrop otherwise sits above the IME and can intercept its keys.
        val nextBackdrop = if (enabled) flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM else flags
        sheetHeight = requestedSheetHeight()
        if (params.flags == next && backdropParams.flags == nextBackdrop) return
        params.flags = next
        backdropParams.flags = nextBackdrop
        if (attached) {
            manager.updateViewLayout(backdrop, backdropParams)
            manager.updateViewLayout(view, params)
        }
    }

    /** Raw screen-coordinate vertical drag; upwards expands, downwards contracts. */
    fun resizeBy(dy: Float) {
        if (!attached || closed || !dy.isFinite()) return
        requestedHeightFraction = (requestedHeightFraction - dy / screenHeight.coerceAtLeast(1))
            .coerceIn(MIN_FRACTION, MAX_FRACTION)
        sheetHeight = requestedSheetHeight()
    }

    /** Keep the user's chosen sheet proportion when the display rotates. */
    fun updateBounds() {
        screenHeight = displayHeight()
        params.height = windowHeight()
        sheetHeight = requestedSheetHeight()
        if (attached && !closed) {
            manager.updateViewLayout(backdrop, backdropParams)
            manager.updateViewLayout(view, params)
        }
    }

    fun close() {
        if (closed) return
        hide()
        if (attached) {
            manager.removeView(view)
            manager.removeView(backdrop)
            attached = false
        }
        closed = true
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    private fun displayHeight(): Int = if (Build.VERSION.SDK_INT >= 30) manager.currentWindowMetrics.bounds.height()
        else context.resources.displayMetrics.heightPixels

    private fun windowHeight(): Int = (screenHeight * MAX_FRACTION).roundToInt()

    /** The editor's minimum never overwrites the user's drag target, including across rotation. */
    private fun requestedSheetHeight(): Int = (screenHeight *
        if (inputEnabled) requestedHeightFraction.coerceAtLeast(2f / 3f) else requestedHeightFraction).roundToInt()

    @Composable
    private fun SheetFrame(content: @Composable () -> Unit) {
        val height = with(LocalDensity.current) { sheetHeight.toDp() }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } }
                .semantics { contentDescription = "关闭答案"; onClick { dismiss(); true } })
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height)) { content() }
        }
    }

    private companion object {
        const val MIN_FRACTION = 1f / 3f
        const val MAX_FRACTION = .9f
    }

    private fun animationsEnabled(): Boolean = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
}

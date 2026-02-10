package com.nodoubt.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.util.Log
import android.util.LruCache
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import ru.noties.jlatexmath.JLatexMathDrawable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Custom ImageSpan that centers the image vertically with the text.
 */
class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val drawable = drawable
        val rect = drawable.bounds

        if (fm != null) {
            val fontMetrics = paint.fontMetricsInt
            val imageHeight = rect.height()
            val textHeight = fontMetrics.descent - fontMetrics.ascent

            // Center the image vertically
            val centerY = fontMetrics.ascent + textHeight / 2
            fm.ascent = centerY - imageHeight / 2
            fm.top = fm.ascent
            fm.descent = centerY + imageHeight / 2
            fm.bottom = fm.descent
        }

        return rect.width()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val drawable = drawable
        val fontMetrics = paint.fontMetricsInt
        val imageHeight = drawable.bounds.height()
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        // Calculate Y position to center the image
        val transY = y + fontMetrics.ascent + (textHeight - imageHeight) / 2

        canvas.save()
        canvas.translate(x, transY.toFloat())
        drawable.draw(canvas)
        canvas.restore()
    }
}

/**
 * Utility class for rendering Markdown and LaTeX in TextViews.
 */
object MarkdownRenderer {

    private const val TAG = "MarkdownRenderer"
    private const val DEBOUNCE_DELAY = 150L
    private const val STREAM_FRAME_DELAY = 48L

    @Volatile
    private var basicMarkwon: Markwon? = null

    @Volatile
    private var latexMarkwon: Markwon? = null

    @Volatile
    private var latexFailed = false

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val latexCache = LruCache<String, Bitmap>(50)
    private val lastRenderedContent = ConcurrentHashMap<Int, String>()
    private val pendingRenders = ConcurrentHashMap<Int, Runnable>()
    private val streamingContent = ConcurrentHashMap<Int, String>()
    private val pendingStreamingRenders = ConcurrentHashMap<Int, Runnable>()
    private val lastStreamingRenderAt = ConcurrentHashMap<Int, Long>()

    private fun getBasicMarkwon(context: Context): Markwon {
        return basicMarkwon ?: synchronized(this) {
            basicMarkwon ?: Markwon.builder(context.applicationContext)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(HtmlPlugin.create())
                .build()
                .also {
                    basicMarkwon = it
                    Log.d(TAG, "Basic Markwon created")
                }
        }
    }

    private fun getLatexMarkwon(context: Context): Markwon? {
        if (latexFailed) return null

        return latexMarkwon ?: synchronized(this) {
            if (latexFailed) return null

            try {
                val textSize = 18f * context.resources.displayMetrics.scaledDensity

                Markwon.builder(context.applicationContext)
                    .usePlugin(MarkwonInlineParserPlugin.create())
                    .usePlugin(JLatexMathPlugin.create(textSize) { builder ->
                        builder.inlinesEnabled(true)
                        builder.blocksEnabled(true)
                        builder.executorService(executor)
                    })
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(context))
                    .usePlugin(HtmlPlugin.create())
                    .build()
                    .also {
                        latexMarkwon = it
                        Log.d(TAG, "LaTeX Markwon created successfully")
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create LaTeX Markwon", e)
                latexFailed = true
                null
            }
        }
    }

    private fun renderLatexToBitmap(latex: String, textSize: Float, maxWidth: Int = 0): Bitmap? {
        val cacheKey = "$latex|$textSize|$maxWidth"

        latexCache.get(cacheKey)?.let { return it }

        return try {
            val builder = JLatexMathDrawable.builder(latex)
                .textSize(textSize)
                .color(Color.BLACK)

            // For block formulas, limit width to fit screen
            if (maxWidth > 0) {
                builder.fitCanvas(true)
            }

            val drawable = builder.build()

            var width = drawable.intrinsicWidth
            var height = drawable.intrinsicHeight

            if (width <= 0 || height <= 0) return null

            // Scale down if too wide (for block formulas)
            if (maxWidth > 0 && width > maxWidth) {
                val scale = maxWidth.toFloat() / width
                width = maxWidth
                height = (height * scale).toInt()
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            if (maxWidth > 0 && drawable.intrinsicWidth > maxWidth) {
                val scale = maxWidth.toFloat() / drawable.intrinsicWidth
                canvas.scale(scale, scale)
            }

            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            drawable.draw(canvas)

            latexCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render LaTeX: $latex", e)
            null
        }
    }

    private fun normalizeDelimiters(text: String): String {
        return text
            .replace("\\[", "$$")
            .replace("\\]", "$$")
            .replace("\\(", "$")
            .replace("\\)", "$")
    }

    private fun normalizeQuestionText(text: String): String {
        return text
            .replace("\\[", "$$")
            .replace("\\]", "$$")
            .replace("\\(", "$")
            .replace("\\)", "$")
    }

    private fun processLatex(context: Context, textView: TextView, text: String, isQuestionText: Boolean = false) {
        val normalized = if (isQuestionText) normalizeQuestionText(text) else normalizeDelimiters(text)
        val textSize = textView.textSize
        val maxWidth = textView.width.takeIf { it > 0 } ?: (context.resources.displayMetrics.widthPixels - 48)

        // Use basic Markwon only (no JLatexMathPlugin) to avoid async flicker
        val markwon = getBasicMarkwon(context)

        // Match block formulas first: $$...$$
        val blockPattern = Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
        // Match inline formulas: $...$
        val inlinePattern = Regex("""(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)""")

        var processedText = normalized
        val placeholders = mutableListOf<Triple<String, String, Boolean>>() // placeholder, latex, isBlock

        // Process block formulas
        var blockIndex = 0
        blockPattern.findAll(normalized).forEach { match ->
            val placeholder = "⟦BLOCK$blockIndex⟧"
            placeholders.add(Triple(placeholder, match.groupValues[1].trim(), true))
            processedText = processedText.replaceFirst(match.value, placeholder)
            blockIndex++
        }

        // Process inline formulas
        var inlineIndex = 0
        inlinePattern.findAll(processedText).toList().forEach { match ->
            val placeholder = "⟦INLINE$inlineIndex⟧"
            placeholders.add(Triple(placeholder, match.groupValues[1], false))
            processedText = processedText.replaceFirst(match.value, placeholder)
            inlineIndex++
        }

        if (placeholders.isEmpty()) {
            val spanned = markwon.toMarkdown(normalized)
            textView.setText(spanned, TextView.BufferType.SPANNABLE)
            return
        }

        // Render markdown WITHOUT LaTeX plugin (no async rendering)
        val markdownSpanned = markwon.toMarkdown(processedText)
        val spannable = SpannableStringBuilder(markdownSpanned)

        // Process all placeholders synchronously
        placeholders.forEach { (placeholder, latex, isBlock) ->
            val start = spannable.indexOf(placeholder)
            if (start >= 0) {
                val renderSize = if (isBlock) textSize * 1.2f else textSize * 0.9f
                val bitmap = renderLatexToBitmap(latex, renderSize, if (isBlock) maxWidth else 0)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(context.resources, bitmap)
                    drawable.setBounds(0, 0, bitmap.width, bitmap.height)

                    spannable.replace(start, start + placeholder.length, "\uFFFC")
                    val imageSpan = if (isBlock) {
                        ImageSpan(drawable, ImageSpan.ALIGN_BASELINE)
                    } else {
                        CenteredImageSpan(drawable)
                    }
                    spannable.setSpan(imageSpan, start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    // Fallback: show raw LaTeX
                    spannable.replace(start, start + placeholder.length, latex)
                }
            }
        }

        // Single atomic update
        textView.setText(spannable, TextView.BufferType.SPANNABLE)
    }

    fun renderAIResponse(context: Context, textView: TextView, text: String) {
        val viewId = System.identityHashCode(textView)
        pendingStreamingRenders[viewId]?.let { mainHandler.removeCallbacks(it) }
        pendingStreamingRenders.remove(viewId)
        streamingContent.remove(viewId)
        lastStreamingRenderAt.remove(viewId)

        if (text.isEmpty()) {
            textView.text = ""
            return
        }

        if (lastRenderedContent[viewId] == text) {
            return
        }

        pendingRenders[viewId]?.let { mainHandler.removeCallbacks(it) }

        val renderTask = Runnable {
            try {
                lastRenderedContent[viewId] = text
                processLatex(context, textView, text)
            } catch (e: Throwable) {
                Log.e(TAG, "Render error", e)
                try {
                    getBasicMarkwon(context).setMarkdown(textView, text)
                } catch (e2: Throwable) {
                    textView.text = text
                }
            } finally {
                pendingRenders.remove(viewId)
            }
        }

        pendingRenders[viewId] = renderTask
        mainHandler.postDelayed(renderTask, DEBOUNCE_DELAY)
    }

    fun renderAIResponseStreaming(textView: TextView, text: String) {
        val viewId = System.identityHashCode(textView)

        if (text.isEmpty()) {
            textView.text = ""
            pendingStreamingRenders[viewId]?.let { mainHandler.removeCallbacks(it) }
            pendingStreamingRenders.remove(viewId)
            streamingContent.remove(viewId)
            lastStreamingRenderAt.remove(viewId)
            return
        }

        // Streaming phase: skip expensive markdown task and use lightweight plain text updates.
        pendingRenders[viewId]?.let { mainHandler.removeCallbacks(it) }
        pendingRenders.remove(viewId)
        val previous = streamingContent.put(viewId, text)
        val hasPendingRender = pendingStreamingRenders.containsKey(viewId)
        if (previous == text && hasPendingRender) {
            return
        }
        scheduleStreamingRender(viewId, textView)
    }

    private fun scheduleStreamingRender(viewId: Int, textView: TextView) {
        if (pendingStreamingRenders.containsKey(viewId)) {
            return
        }
        val applyTask = Runnable {
            pendingStreamingRenders.remove(viewId)
            val latest = streamingContent[viewId] ?: return@Runnable
            if (textView.text?.toString() != latest) {
                textView.text = latest
            }
            lastStreamingRenderAt[viewId] = SystemClock.uptimeMillis()
            // If text changed while this frame was rendering, queue another frame.
            if (streamingContent[viewId] != latest) {
                scheduleStreamingRender(viewId, textView)
            }
        }

        val now = SystemClock.uptimeMillis()
        val last = lastStreamingRenderAt[viewId] ?: 0L
        val delay = (STREAM_FRAME_DELAY - (now - last)).coerceAtLeast(0L)
        pendingStreamingRenders[viewId] = applyTask
        if (delay == 0L) {
            mainHandler.post(applyTask)
        } else {
            mainHandler.postDelayed(applyTask, delay)
        }
    }

    fun flushAIResponse(context: Context, textView: TextView, text: String) {
        val viewId = System.identityHashCode(textView)
        pendingStreamingRenders[viewId]?.let { mainHandler.removeCallbacks(it) }
        pendingStreamingRenders.remove(viewId)
        streamingContent.remove(viewId)
        lastStreamingRenderAt.remove(viewId)
        renderAIResponse(context, textView, text)
    }

    fun renderQuestionText(context: Context, textView: TextView, text: String) {
        if (text.isEmpty()) {
            textView.text = ""
            return
        }

        val viewId = System.identityHashCode(textView)

        if (lastRenderedContent[viewId] == text) {
            return
        }

        pendingRenders[viewId]?.let { mainHandler.removeCallbacks(it) }

        val renderTask = Runnable {
            try {
                lastRenderedContent[viewId] = text
                processLatex(context, textView, text, isQuestionText = true)
            } catch (e: Throwable) {
                Log.e(TAG, "Render error", e)
                try {
                    getBasicMarkwon(context).setMarkdown(textView, text)
                } catch (e2: Throwable) {
                    textView.text = text
                }
            } finally {
                pendingRenders.remove(viewId)
            }
        }

        pendingRenders[viewId] = renderTask
        mainHandler.postDelayed(renderTask, DEBOUNCE_DELAY)
    }

    fun clearViewCache(textView: TextView) {
        val viewId = System.identityHashCode(textView)
        lastRenderedContent.remove(viewId)
        pendingRenders[viewId]?.let { mainHandler.removeCallbacks(it) }
        pendingRenders.remove(viewId)
        streamingContent.remove(viewId)
        pendingStreamingRenders[viewId]?.let { mainHandler.removeCallbacks(it) }
        pendingStreamingRenders.remove(viewId)
        lastStreamingRenderAt.remove(viewId)
    }

    fun clearCache() {
        pendingRenders.values.forEach { mainHandler.removeCallbacks(it) }
        pendingStreamingRenders.values.forEach { mainHandler.removeCallbacks(it) }
        pendingRenders.clear()
        pendingStreamingRenders.clear()
        latexCache.evictAll()
        lastRenderedContent.clear()
        streamingContent.clear()
        lastStreamingRenderAt.clear()
    }
}

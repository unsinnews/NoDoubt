package com.yy.perfectfloatwindow.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
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
import java.lang.ref.WeakReference
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

    private fun renderLatexToBitmap(latex: String, textSize: Float): Bitmap? {
        val cacheKey = "$latex|$textSize"

        latexCache.get(cacheKey)?.let { return it }

        return try {
            val drawable = JLatexMathDrawable.builder(latex)
                .textSize(textSize)
                .color(Color.BLACK)
                .build()

            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight

            if (width <= 0 || height <= 0) return null

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
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

    private fun processInlineLatex(context: Context, textView: TextView, text: String) {
        val normalized = normalizeDelimiters(text)
        val textSize = textView.textSize

        val markwon = getLatexMarkwon(context) ?: getBasicMarkwon(context)

        val inlinePattern = Regex("""(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)""")
        val matches = inlinePattern.findAll(normalized).toList()

        if (matches.isEmpty()) {
            markwon.setMarkdown(textView, normalized)
            return
        }

        var processedText = normalized
        val placeholders = mutableListOf<Pair<String, String>>()

        matches.forEachIndexed { index, match ->
            val placeholder = "\u0000LTEX$index\u0000"
            placeholders.add(placeholder to match.groupValues[1])
            processedText = processedText.replaceFirst(match.value, placeholder)
        }

        markwon.setMarkdown(textView, processedText)

        val spannable = SpannableStringBuilder(textView.text)

        placeholders.forEach { (placeholder, latex) ->
            val start = spannable.indexOf(placeholder)
            if (start >= 0) {
                val bitmap = renderLatexToBitmap(latex, textSize * 0.9f)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(context.resources, bitmap)
                    drawable.setBounds(0, 0, bitmap.width, bitmap.height)

                    spannable.replace(start, start + placeholder.length, "\uFFFC")
                    // Use CenteredImageSpan for vertical centering
                    val imageSpan = CenteredImageSpan(drawable)
                    spannable.setSpan(imageSpan, start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    spannable.replace(start, start + placeholder.length, latex)
                }
            }
        }

        textView.text = spannable
    }

    fun renderAIResponse(context: Context, textView: TextView, text: String) {
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
                processInlineLatex(context, textView, text)
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

    fun clearCache() {
        latexCache.evictAll()
        lastRenderedContent.clear()
    }
}

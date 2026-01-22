package com.yy.perfectfloatwindow.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Utility class for rendering Markdown and LaTeX in TextViews.
 */
object MarkdownRenderer {

    private const val TAG = "MarkdownRenderer"
    private const val DEBOUNCE_DELAY = 150L // ms

    @Volatile
    private var basicMarkwon: Markwon? = null

    @Volatile
    private var latexMarkwon: Markwon? = null

    @Volatile
    private var latexFailed = false

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Cache for rendered LaTeX bitmaps (key: latex string + size, value: bitmap)
    private val latexCache = LruCache<String, Bitmap>(50)

    // Track last rendered content to avoid redundant renders
    private val lastRenderedContent = ConcurrentHashMap<Int, String>()

    // Debounce handlers per TextView
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

    /**
     * Render a LaTeX formula to a Bitmap with caching.
     */
    private fun renderLatexToBitmap(latex: String, textSize: Float): Bitmap? {
        val cacheKey = "$latex|$textSize"

        // Check cache first
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

            // Cache the result
            latexCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render LaTeX: $latex", e)
            null
        }
    }

    /**
     * Convert \[...\] to $$...$$ and \(...\) to $...$
     */
    private fun normalizeDelimiters(text: String): String {
        return text
            .replace("\\[", "$$")
            .replace("\\]", "$$")
            .replace("\\(", "$")
            .replace("\\)", "$")
    }

    /**
     * Process text with inline LaTeX rendering.
     */
    private fun processInlineLatex(context: Context, textView: TextView, text: String) {
        val normalized = normalizeDelimiters(text)
        val textSize = textView.textSize

        val markwon = getLatexMarkwon(context) ?: getBasicMarkwon(context)

        // Find all inline math $...$ (not $$)
        val inlinePattern = Regex("""(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)""")
        val matches = inlinePattern.findAll(normalized).toList()

        if (matches.isEmpty()) {
            markwon.setMarkdown(textView, normalized)
            return
        }

        // Replace inline math with placeholders
        var processedText = normalized
        val placeholders = mutableListOf<Pair<String, String>>()

        matches.forEachIndexed { index, match ->
            val placeholder = "\u0000LTEX$index\u0000"
            placeholders.add(placeholder to match.groupValues[1])
            processedText = processedText.replaceFirst(match.value, placeholder)
        }

        // Render markdown first
        markwon.setMarkdown(textView, processedText)

        // Replace placeholders with rendered LaTeX
        val spannable = SpannableStringBuilder(textView.text)

        placeholders.forEach { (placeholder, latex) ->
            val start = spannable.indexOf(placeholder)
            if (start >= 0) {
                val bitmap = renderLatexToBitmap(latex, textSize * 0.85f)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(context.resources, bitmap)
                    drawable.setBounds(0, 0, bitmap.width, bitmap.height)

                    spannable.replace(start, start + placeholder.length, "\uFFFC")
                    val imageSpan = ImageSpan(drawable, ImageSpan.ALIGN_BASELINE)
                    spannable.setSpan(imageSpan, start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    spannable.replace(start, start + placeholder.length, latex)
                }
            }
        }

        textView.text = spannable
    }

    /**
     * Render AI response with debouncing and caching.
     */
    fun renderAIResponse(context: Context, textView: TextView, text: String) {
        if (text.isEmpty()) {
            textView.text = ""
            return
        }

        val viewId = System.identityHashCode(textView)

        // Skip if content hasn't changed
        if (lastRenderedContent[viewId] == text) {
            return
        }

        // Cancel any pending render for this view
        pendingRenders[viewId]?.let { mainHandler.removeCallbacks(it) }

        // Schedule debounced render
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

    /**
     * Clear cache (call when memory is low)
     */
    fun clearCache() {
        latexCache.evictAll()
        lastRenderedContent.clear()
    }
}

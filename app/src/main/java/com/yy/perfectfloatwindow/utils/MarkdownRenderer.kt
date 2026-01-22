package com.yy.perfectfloatwindow.utils

import android.content.Context
import android.util.Log
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.util.concurrent.Executors

/**
 * Utility class for rendering Markdown and LaTeX in TextViews.
 */
object MarkdownRenderer {

    private const val TAG = "MarkdownRenderer"

    @Volatile
    private var basicMarkwon: Markwon? = null

    @Volatile
    private var latexMarkwon: Markwon? = null

    @Volatile
    private var latexFailed = false

    private val executor = Executors.newSingleThreadExecutor()

    private fun getBasicMarkwon(context: Context): Markwon {
        return basicMarkwon ?: synchronized(this) {
            basicMarkwon ?: Markwon.builder(context.applicationContext)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(LinkifyPlugin.create())
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
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(context))
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(LinkifyPlugin.create())
                    .usePlugin(JLatexMathPlugin.create(textSize) { builder ->
                        builder.inlinesEnabled(true)
                        builder.blocksEnabled(true)
                        builder.executorService(executor)
                    })
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
     * Convert \[...\] to $$...$$ and \(...\) to $...$
     * Using StringBuilder to avoid regex replacement issues with $ character
     */
    private fun preprocessLatex(text: String): String {
        val sb = StringBuilder(text)

        // Replace \[ with $$
        var index = sb.indexOf("\\[")
        while (index != -1) {
            sb.replace(index, index + 2, "\$\$")
            index = sb.indexOf("\\[", index + 2)
        }

        // Replace \] with $$
        index = sb.indexOf("\\]")
        while (index != -1) {
            sb.replace(index, index + 2, "\$\$")
            index = sb.indexOf("\\]", index + 2)
        }

        // Replace \( with $
        index = sb.indexOf("\\(")
        while (index != -1) {
            sb.replace(index, index + 2, "\$")
            index = sb.indexOf("\\(", index + 1)
        }

        // Replace \) with $
        index = sb.indexOf("\\)")
        while (index != -1) {
            sb.replace(index, index + 2, "\$")
            index = sb.indexOf("\\)", index + 1)
        }

        return sb.toString()
    }

    /**
     * Render AI response with Markdown and LaTeX support.
     */
    fun renderAIResponse(context: Context, textView: TextView, text: String) {
        if (text.isEmpty()) {
            textView.text = ""
            return
        }

        val processed = preprocessLatex(text)

        // Try LaTeX rendering
        try {
            val latex = getLatexMarkwon(context)
            if (latex != null) {
                latex.setMarkdown(textView, processed)
                return
            }
        } catch (e: Throwable) {
            Log.e(TAG, "LaTeX render error", e)
            latexFailed = true
            latexMarkwon = null
        }

        // Fallback to basic Markdown
        try {
            getBasicMarkwon(context).setMarkdown(textView, processed)
        } catch (e: Throwable) {
            Log.e(TAG, "Basic render error", e)
            textView.text = text
        }
    }
}

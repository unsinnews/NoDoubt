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
     * Check if a LaTeX expression is simple enough to display as italic text.
     * Simple means: single letter, or simple variable like x, y, a, b, R, etc.
     */
    private fun isSimpleExpression(content: String): Boolean {
        val trimmed = content.trim()
        // Single letter or number
        if (trimmed.length == 1) return true
        // Simple variable with subscript like x_1, a_n (but not complex)
        if (trimmed.matches(Regex("^[a-zA-Z](_[a-zA-Z0-9])?$"))) return true
        // Very short and no complex LaTeX commands
        if (trimmed.length <= 3 && !trimmed.contains("\\")) return true
        return false
    }

    /**
     * Check if content contains complex LaTeX that needs rendering.
     */
    private fun isComplexLatex(content: String): Boolean {
        // Contains LaTeX commands like \frac, \sqrt, \sum, etc.
        return content.contains("\\frac") ||
                content.contains("\\sqrt") ||
                content.contains("\\sum") ||
                content.contains("\\int") ||
                content.contains("\\prod") ||
                content.contains("\\lim") ||
                content.contains("\\infty") ||
                content.contains("\\partial") ||
                content.contains("\\alpha") ||
                content.contains("\\beta") ||
                content.contains("\\gamma") ||
                content.contains("\\theta") ||
                content.contains("\\pi") ||
                content.contains("\\cdot") ||
                content.contains("\\times") ||
                content.contains("\\div") ||
                content.contains("\\pm") ||
                content.contains("\\leq") ||
                content.contains("\\geq") ||
                content.contains("\\neq") ||
                content.contains("\\approx") ||
                content.contains("^{") ||
                content.contains("_{") ||
                content.contains("\\left") ||
                content.contains("\\right") ||
                content.contains("\\begin") ||
                content.contains("\\end")
    }

    /**
     * Convert LaTeX delimiters for rendering.
     * - Simple inline math: convert to italic *...*
     * - Complex inline math: convert to block $$...$$
     * - Block math: keep as $$...$$
     */
    private fun preprocessLatex(text: String): String {
        var result = text

        // Convert \[...\] to $$...$$ (block math)
        result = result.replace("\\[", "$$")
        result = result.replace("\\]", "$$")

        // Convert \(...\) based on complexity
        val displayPattern = Regex("""\\\((.*?)\\\)""", RegexOption.DOT_MATCHES_ALL)
        result = displayPattern.replace(result) { match ->
            val content = match.groupValues[1]
            when {
                isSimpleExpression(content) -> "*${content.trim()}*"
                isComplexLatex(content) -> "\n$$${content}$$\n"
                else -> "*${content.trim()}*"
            }
        }

        // Process inline $...$ (not $$)
        val inlinePattern = Regex("""(?<!\$)\$(?!\$)(.*?)(?<!\$)\$(?!\$)""")
        result = inlinePattern.replace(result) { match ->
            val content = match.groupValues[1]
            when {
                isSimpleExpression(content) -> "*${content.trim()}*"
                isComplexLatex(content) -> "\n$$${content}$$\n"
                else -> "*${content.trim()}*"
            }
        }

        // Clean up extra newlines
        result = result.replace(Regex("\n{3,}"), "\n\n")

        return result
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

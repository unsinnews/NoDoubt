package com.yy.perfectfloatwindow.utils

import android.content.Context
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Utility class for rendering Markdown and LaTeX math formulas in TextViews.
 *
 * Supports:
 * - Standard Markdown syntax (headers, bold, italic, lists, code blocks, etc.)
 * - LaTeX math formulas (inline $...$ and block $$...$$)
 * - Tables
 * - Strikethrough
 * - HTML tags
 * - Auto-linking URLs
 */
object MarkdownRenderer {

    @Volatile
    private var markwon: Markwon? = null

    /**
     * Get or create the Markwon instance with all plugins configured.
     */
    fun getInstance(context: Context): Markwon {
        return markwon ?: synchronized(this) {
            markwon ?: createMarkwon(context.applicationContext).also { markwon = it }
        }
    }

    private fun createMarkwon(context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(JLatexMathPlugin.create(getTextSizeForLatex(context)) { builder ->
                // Configure LaTeX rendering
                builder.inlinesEnabled(true)  // Enable inline math $...$
                builder.blocksEnabled(true)   // Enable block math $$...$$
            })
            .build()
    }

    /**
     * Calculate appropriate text size for LaTeX formulas based on context.
     */
    private fun getTextSizeForLatex(context: Context): Float {
        // Use 14sp as base size, converted to pixels
        val density = context.resources.displayMetrics.scaledDensity
        return 14f * density
    }

    /**
     * Render markdown text to a TextView.
     * This will parse the markdown and apply spans to the TextView.
     */
    fun render(context: Context, textView: TextView, markdown: String) {
        val instance = getInstance(context)
        instance.setMarkdown(textView, markdown)
    }

    /**
     * Render markdown and return the styled CharSequence.
     * Useful when you need to manipulate the text before setting it.
     */
    fun toMarkdown(context: Context, markdown: String): CharSequence {
        val instance = getInstance(context)
        return instance.toMarkdown(markdown)
    }

    /**
     * Preprocess AI response text to handle common formatting issues.
     * - Normalizes LaTeX delimiters
     * - Handles escaped characters
     */
    fun preprocessAIResponse(text: String): String {
        var processed = text

        // Convert \[ \] to $$ $$ for block math (common in AI responses)
        processed = processed.replace("\\[", "$$")
        processed = processed.replace("\\]", "$$")

        // Convert \( \) to $ $ for inline math (common in AI responses)
        processed = processed.replace("\\(", "$")
        processed = processed.replace("\\)", "$")

        return processed
    }

    /**
     * Render AI response with preprocessing.
     */
    fun renderAIResponse(context: Context, textView: TextView, text: String) {
        val processed = preprocessAIResponse(text)
        render(context, textView, processed)
    }
}

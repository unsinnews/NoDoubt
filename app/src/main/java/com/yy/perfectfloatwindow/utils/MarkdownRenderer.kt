package com.yy.perfectfloatwindow.utils

import android.content.Context
import android.util.Log
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Utility class for rendering Markdown in TextViews.
 */
object MarkdownRenderer {

    private const val TAG = "MarkdownRenderer"

    @Volatile
    private var markwon: Markwon? = null

    /**
     * Get or create the Markwon instance.
     */
    private fun getInstance(context: Context): Markwon {
        return markwon ?: synchronized(this) {
            markwon ?: createMarkwon(context.applicationContext).also {
                markwon = it
                Log.d(TAG, "Markwon instance created successfully")
            }
        }
    }

    private fun createMarkwon(context: Context): Markwon {
        Log.d(TAG, "Creating Markwon instance...")
        return Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    /**
     * Render AI response with Markdown support.
     */
    fun renderAIResponse(context: Context, textView: TextView, text: String) {
        if (text.isEmpty()) {
            textView.text = ""
            return
        }

        try {
            Log.d(TAG, "renderAIResponse called, text length: ${text.length}")
            val markwonInstance = getInstance(context)
            Log.d(TAG, "Got Markwon instance, setting markdown...")
            markwonInstance.setMarkdown(textView, text)
            Log.d(TAG, "Markdown set successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Markdown rendering failed: ${e.message}", e)
            textView.text = text
        }
    }
}

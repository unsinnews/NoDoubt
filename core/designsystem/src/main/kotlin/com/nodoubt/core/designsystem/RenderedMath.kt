package com.nodoubt.core.designsystem

import android.graphics.drawable.Drawable
import android.text.Spanned
import io.noties.markwon.ext.latex.JLatexAsyncDrawableSpan
import kotlinx.coroutines.asCoroutineDispatcher
import ru.noties.jlatexmath.JLatexMathDrawable
import java.util.concurrent.Executors

/**
 * Markwon renders formulas asynchronously and shows their raw LaTeX until each one is ready. Streaming
 * re-parses the answer many times a second and every re-parse cancels those renders, so formulas stayed
 * as raw text and the lines jumped when they finally rendered. Instead, each parse gets its formulas drawn
 * here before it is shown, reusing earlier results, so the view never displays a placeholder.
 */
internal class RenderedMath(private val textSize: Float, private val color: Int) {

    /** Attaches finished formula drawables to [spanned]; unparseable formulas fall back to Markwon. */
    fun prerender(spanned: Spanned) = synchronized(MathThread) {
        spanned.getSpans(0, spanned.length, JLatexAsyncDrawableSpan::class.java).forEach { span ->
            val drawable = span.drawable
            if (drawable.hasResult()) return@forEach
            // Display blocks use the public span class; inline formulas use its package-private subclass.
            val block = span.javaClass == JLatexAsyncDrawableSpan::class.java
            val key = "$textSize:$color:${if (block) "b" else "i"}:${drawable.destination}"
            val result = MathThread.cache[key] ?: runCatching {
                JLatexMathDrawable.builder(drawable.destination).textSize(textSize).color(color)
                    .apply { if (block) align(JLatexMathDrawable.ALIGN_CENTER) }.build()
            }.getOrNull()?.also { MathThread.cache[key] = it } ?: return@forEach
            drawable.result = result
        }
    }
}

/**
 * JLatexMath keeps global parser state, so all formula rendering shares one thread and lock. Finished
 * formulas are shared app-wide, so switching modes or reopening the sheet redraws them instantly.
 */
internal object MathThread {
    val cache = object : LinkedHashMap<String, Drawable>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable>?) = size > 400
    }
    val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "nodoubt-math").apply { isDaemon = true } }
        .asCoroutineDispatcher()
}

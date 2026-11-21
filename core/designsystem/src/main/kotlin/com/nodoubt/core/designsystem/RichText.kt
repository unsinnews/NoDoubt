package com.nodoubt.core.designsystem

import android.text.Spanned
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Retained tabs must not attach a native view until their content is placed. */
val LocalRichTextVisible = staticCompositionLocalOf { true }

/**
 * Markdown with math. [streaming] hides a formula whose closing delimiter has not arrived yet, so raw
 * LaTeX never flashes before it renders. [tailLines] > 0 shows only the last that many lines, pinned
 * to the end as text grows; [onLineCount] reports the full line count.
 */
@OptIn(FlowPreview::class)
@Composable
fun RichText(
    text: String, modifier: Modifier = Modifier, fontSize: Int = 16, compact: Boolean = false,
    streaming: Boolean = false, tailLines: Int = 0, onLineCount: ((Int) -> Unit)? = null,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    if (!LocalRichTextVisible.current) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val textColor = (if (color == androidx.compose.ui.graphics.Color.Unspecified) MaterialTheme.colorScheme.onSurface
        else color).toArgb()
    val linkColor = LocalNoDoubtTokens.current.foregroundAccent.toArgb()
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(executor) { onDispose { executor.shutdownNow() } }
    val mathSize = fontSize * density.density * density.fontScale
    val markwon = remember(context, fontSize, density.fontScale, textColor) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(mathSize) {
                it.inlinesEnabled(true)
                it.blocksEnabled(true)
                it.executorService(executor)
                it.theme().textColor(textColor)
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }
    // Formulas are drawn before a parse is shown, so updates never flash raw LaTeX or shift lines.
    val renderedMath = remember(markwon) { RenderedMath(mathSize, textColor) }
    val latestText by rememberUpdatedState(text)
    val latestStreaming by rememberUpdatedState(streaming)
    fun prepare(value: String, open: Boolean) = normalizeMathDelimiters(value).let { if (open) hideOpenMath(it) else it }
    var parsed by remember(markwon) {
        mutableStateOf<Spanned>(markwon.toMarkdown(prepare(text, streaming)).also(renderedMath::prerender))
    }
    LaunchedEffect(markwon) {
        snapshotFlow { latestText to latestStreaming }.sample(70).collectLatest { (value, open) ->
            val next = withContext(Dispatchers.Default) { markwon.toMarkdown(prepare(value, open)) }
            parsed = withContext(MathThread.dispatcher) { next.also(renderedMath::prerender) }
        }
    }
    AndroidView(
        factory = {
            TailTextView(it).apply {
                setPadding(0, 0, 0, 0)
                this.tailLines = tailLines
                if (tailLines > 0) {
                    maxLines = tailLines
                    isVerticalScrollBarEnabled = false
                } else setTextIsSelectable(true)
            }
        },
        modifier = modifier.fillMaxWidth(),
        update = { view ->
            view.onLineCount = onLineCount
            view.textSize = fontSize.toFloat()
            view.includeFontPadding = !compact
            view.setLineSpacing(0f, if (compact) 1.28f else 1.4f)
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            val rendered = parsed
            if (view.tag !== rendered) {
                markwon.setParsedMarkdown(view, rendered)
                view.tag = rendered
                view.requestLayout()
                view.invalidate()
            }
        }
    )
}

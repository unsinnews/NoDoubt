package com.nodoubt.core.designsystem

import android.text.Spanned
import android.widget.TextView
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

@OptIn(FlowPreview::class)
@Composable
fun RichText(text: String, modifier: Modifier = Modifier, fontSize: Int = 16, compact: Boolean = false) {
    if (!LocalRichTextVisible.current) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = LocalNoDoubtTokens.current.foregroundAccent.toArgb()
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(executor) { onDispose { executor.shutdownNow() } }
    val markwon = remember(context, fontSize, density.fontScale, color) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(fontSize * density.density * density.fontScale) {
                it.inlinesEnabled(true)
                it.blocksEnabled(true)
                it.executorService(executor)
                it.theme().textColor(color)
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }
    val latestText by rememberUpdatedState(text)
    var parsed by remember(markwon) {
        mutableStateOf<Spanned>(markwon.toMarkdown(normalizeMathDelimiters(text)))
    }
    LaunchedEffect(markwon) {
        snapshotFlow { latestText }.sample(70).collectLatest { value ->
            parsed = withContext(Dispatchers.Default) {
                markwon.toMarkdown(normalizeMathDelimiters(value))
            }
        }
    }
    AndroidView(
        factory = { TextView(it).apply { setTextIsSelectable(true); setPadding(0, 0, 0, 0) } },
        modifier = modifier.fillMaxWidth(),
        update = { view ->
            view.textSize = fontSize.toFloat()
            view.includeFontPadding = !compact
            view.setLineSpacing(0f, if (compact) 1.28f else 1.4f)
            view.setTextColor(color)
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

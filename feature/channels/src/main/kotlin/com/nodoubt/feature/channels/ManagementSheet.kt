package com.nodoubt.feature.channels

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nodoubt.core.designsystem.*

/** A searchable multi-select row with a trailing check, matching [ItemRow] spacing. */
@Composable
internal fun SelectableRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    ItemRow(title, subtitle, onClick = if (enabled) onClick else null) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (checked) Symbol(UiSymbol.CHECK, color = LocalNoDoubtTokens.current.foregroundAccent.copy(alpha = if (enabled) 1f else .4f))
        }
    }
}

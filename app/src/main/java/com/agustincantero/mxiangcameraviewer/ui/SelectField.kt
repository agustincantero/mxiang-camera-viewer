package com.agustincantero.mxiangcameraviewer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.agustincantero.mxiangcameraviewer.R
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurface
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurfaceVariant

// A field that opens a list of choices, drawn like the text fields around it: outlined, with its name floating on the border and the current choice inside. It always has a choice, so the label always sits on the border, and the arrow at the end says it opens.
@Composable
fun SelectField(label: String, value: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }

    FieldFrame(
        label = label,
        highlighted = focused,
        modifier = modifier,
        fieldModifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
            Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}

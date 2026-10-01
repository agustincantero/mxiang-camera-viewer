package com.agustincantero.mxiangcameraviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurfaceVariant
import com.agustincantero.mxiangcameraviewer.ui.theme.Outline
import com.agustincantero.mxiangcameraviewer.ui.theme.Primary
import com.agustincantero.mxiangcameraviewer.ui.theme.SurfaceContainer

// The frame every field of the login shares: its name in plain text above, and under it the outlined box, which is what takes focus and clicks, so [fieldModifier] goes on the box. The name turns blue along with the border when the field has the focus.
@Composable
fun FieldFrame(
    label: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (highlighted) Primary else OnSurfaceVariant)
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(SurfaceContainer)
                .border(if (highlighted) 3.dp else 1.dp, if (highlighted) Primary else Outline, shape)
                .then(fieldModifier)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
            content = content,
        )
    }
}

package com.agustincantero.mxiangcameraviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.agustincantero.mxiangcameraviewer.R
import com.agustincantero.mxiangcameraviewer.data.Region
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurface
import com.agustincantero.mxiangcameraviewer.ui.theme.Primary
import com.agustincantero.mxiangcameraviewer.ui.theme.SurfaceContainerHigh

// Every region at once, with the current one marked and focused, instead of a button that steps to the next one on each press. A dialog window owns the focus, and Back closes it.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RegionDialog(current: Region, onSelect: (Region) -> Unit, onDismiss: () -> Unit) {
    val focusOnCurrent = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { focusOnCurrent.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(440.dp).background(SurfaceContainerHigh, RoundedCornerShape(28.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.login_region_title),
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Region.entries.forEach { region ->
                val selected = region == current
                ListItem(
                    selected = selected,
                    onClick = { onSelect(region) },
                    modifier = if (selected) Modifier.focusRequester(focusOnCurrent) else Modifier,
                    headlineContent = { Text(stringResource(region.label), style = MaterialTheme.typography.bodyLarge) },
                    // The mark says which one is chosen; colour alone would not, as the guide warns.
                    trailingContent = if (selected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

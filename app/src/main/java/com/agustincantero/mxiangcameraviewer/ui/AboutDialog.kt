package com.agustincantero.mxiangcameraviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.agustincantero.mxiangcameraviewer.R
import com.agustincantero.mxiangcameraviewer.ui.theme.OnPrimary
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurface
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurfaceVariant
import com.agustincantero.mxiangcameraviewer.ui.theme.Primary
import com.agustincantero.mxiangcameraviewer.ui.theme.Surface

// The usual About screen: who the app is, what version, who it is not, and whose work it stands on. Only text, since a TV has no browser to follow a link to, and Back closes it.
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Surface), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(760.dp).padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(64.dp).background(Primary, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_videocam), contentDescription = null, tint = OnPrimary, modifier = Modifier.size(36.dp))
                }
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = OnSurface)
                Text(
                    stringResource(R.string.about_version, appVersionName()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceVariant,
                )
                Text(
                    stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    stringResource(R.string.about_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.about_credits),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

package com.agustincantero.mxiangcameraviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.agustincantero.mxiangcameraviewer.R
import com.agustincantero.mxiangcameraviewer.data.Account
import com.agustincantero.mxiangcameraviewer.data.Region
import com.agustincantero.mxiangcameraviewer.ui.theme.Error
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurface
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurfaceVariant
import com.agustincantero.mxiangcameraviewer.ui.theme.Surface
import com.agustincantero.mxiangcameraviewer.ui.theme.SurfaceContainer
import com.agustincantero.mxiangcameraviewer.ui.theme.SurfaceContainerHigh

// A right overlay panel. It is a dialog window on purpose: that window owns the focus, so the remote cannot wander back into the cameras behind it, and Back closes it.
@Composable
fun AccountPanel(account: Account, onSignOut: () -> Unit, onDismiss: () -> Unit) {
    val focus = rememberInitialFocus()
    var showAbout by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Transparent, Surface.copy(alpha = 0.85f), Surface.copy(alpha = 0.97f))),
            ),
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(380.dp)
                    .fillMaxHeight()
                    .background(SurfaceContainer, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .padding(start = 40.dp, end = 58.dp, top = 28.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(R.string.account_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TwoLine(stringResource(R.string.account_user_id), account.userId)
                TwoLine(stringResource(R.string.account_region), stringResource(Region.fromCode(account.region).label))
                Spacer(Modifier.weight(1f))
                // About takes the focus, so the first press here can never sign anyone out.
                OutlinedButton(onClick = { showAbout = true }, modifier = Modifier.focusRequester(focus)) {
                    Text(stringResource(R.string.about_button))
                }
                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.colors(contentColor = Error, focusedContainerColor = Error, focusedContentColor = Surface),
                ) {
                    Text(stringResource(R.string.action_sign_out))
                }
            }
        }
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

// The version this build was given in version.txt.
@Composable
fun appVersionName(): String {
    val context = LocalContext.current
    return remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
}

// A list item with two lines, as the guide has it: no container, and not a button.
@Composable
private fun TwoLine(overline: String, headline: String) {
    Column {
        Text(overline, style = MaterialTheme.typography.labelLarge, color = OnSurfaceVariant)
        Text(headline, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
    }
}

// Cancel takes the focus, because signing out is the one action here that cannot be taken back.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val focus = rememberInitialFocus()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(SurfaceContainerHigh, RoundedCornerShape(28.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.signout_title), style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            Text(
                stringResource(R.string.signout_message),
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant,
            )
            Row(
                Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.colors(contentColor = Error, focusedContainerColor = Error, focusedContentColor = Surface),
                ) {
                    Text(stringResource(R.string.action_sign_out))
                }
                Button(onClick = onDismiss, modifier = Modifier.focusRequester(focus)) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}



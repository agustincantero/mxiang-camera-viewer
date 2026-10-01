package com.agustincantero.mxiangcameraviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.agustincantero.mxiangcameraviewer.R
import com.agustincantero.mxiangcameraviewer.data.Region
import com.agustincantero.mxiangcameraviewer.ui.theme.Error
import com.agustincantero.mxiangcameraviewer.ui.theme.OnPrimary
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurface
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurfaceVariant
import com.agustincantero.mxiangcameraviewer.ui.theme.Primary

@Composable
fun LoginScreen(
    state: LoginUiState,
    onRegion: (Region) -> Unit,
    onSignIn: (username: String, password: String) -> Unit,
    onCaptcha: (String) -> Unit,
    onVerify: (String) -> Unit,
    onBack: () -> Unit,
) {
    val focus = rememberInitialFocus(state.step)
    // Kept here rather than in the fields, so going back from a later step finds them as they were left.
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // There is no Back button on screen; the remote's own leaves a step and returns to the credentials.
    BackHandler(enabled = state.step != LoginStep.Credentials) { onBack() }

    Row(
        Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        // Left: who this is and what is asked for.
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(48.dp).background(Primary, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_videocam), contentDescription = null, tint = OnPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(
                    when (state.step) {
                        LoginStep.Credentials -> R.string.login_title
                        LoginStep.Captcha -> R.string.login_captcha_title
                        LoginStep.Verify -> R.string.login_verify_title
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when (state.step) {
                    LoginStep.Credentials -> stringResource(R.string.login_subtitle)
                    LoginStep.Captcha -> stringResource(R.string.login_captcha_subtitle)
                    LoginStep.Verify ->
                        if (state.verifyTarget.isEmpty()) stringResource(R.string.login_verify_sent_unknown)
                        else stringResource(R.string.login_verify_sent, state.verifyTarget)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant,
            )
        }

        // Right: the fields for the current step.
        Column(
            Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            when (state.step) {
                LoginStep.Credentials -> CredentialsFields(
                    state, focus, username, { username = it }, password, { password = it }, onRegion, onSignIn,
                )
                LoginStep.Captcha -> CodeField(
                    state = state,
                    label = stringResource(R.string.login_captcha_field),
                    focus = focus,
                    onSubmit = onCaptcha,
                    picture = true,
                )
                LoginStep.Verify -> CodeField(
                    state = state,
                    label = stringResource(R.string.login_verify_field),
                    focus = focus,
                    onSubmit = onVerify,
                    picture = false,
                )
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Error)
            }
        }
    }
}

@Composable
private fun CredentialsFields(
    state: LoginUiState,
    focus: androidx.compose.ui.focus.FocusRequester,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    onRegion: (Region) -> Unit,
    onSignIn: (String, String) -> Unit,
) {
    AppTextField(username, onUsername, stringResource(R.string.login_field_user), focusRequester = focus, enabled = !state.working)
    AppTextField(
        password, onPassword, stringResource(R.string.login_field_password),
        password = true,
        enabled = !state.working,
        imeAction = ImeAction.Done,
        onImeAction = { onSignIn(username, password) },
    )
    var choosingRegion by remember { mutableStateOf(false) }
    SelectField(
        label = stringResource(R.string.login_region_label),
        value = stringResource(state.region.label),
        enabled = !state.working,
        onClick = { choosingRegion = true },
    )
    if (choosingRegion) {
        RegionDialog(
            current = state.region,
            onSelect = {
                onRegion(it)
                choosingRegion = false
            },
            onDismiss = { choosingRegion = false },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = { onSignIn(username, password) }, enabled = !state.working) {
            WorkingLabel(state.working, if (state.working) R.string.login_working else R.string.login_action)
        }
    }
    Text(
        stringResource(R.string.login_hint_challenge),
        style = MaterialTheme.typography.bodyMedium,
        color = OnSurfaceVariant,
    )
}

@Composable
private fun CodeField(
    state: LoginUiState,
    label: String,
    focus: androidx.compose.ui.focus.FocusRequester,
    onSubmit: (String) -> Unit,
    picture: Boolean,
) {
    var code by remember { mutableStateOf("") }

    val bitmap = state.captcha
    if (picture && bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)).background(OnSurface),
        )
    }
    AppTextField(
        code, { code = it }, label,
        focusRequester = focus,
        enabled = !state.working,
        imeAction = ImeAction.Done,
        onImeAction = { onSubmit(code) },
    )
    Button(onClick = { onSubmit(code) }, enabled = !state.working) {
        WorkingLabel(state.working, if (state.working) R.string.login_working else R.string.action_continue)
    }
}

// A button's text, with a spinner before it while the action runs.
@Composable
private fun WorkingLabel(working: Boolean, text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (working) Spinner(diameter = 20.dp, color = LocalContentColor.current)
        Text(stringResource(text))
    }
}

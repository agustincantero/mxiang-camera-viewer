package com.agustincantero.mxiangcameraviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurface
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurfaceVariant
import com.agustincantero.mxiangcameraviewer.ui.theme.Outline
import com.agustincantero.mxiangcameraviewer.ui.theme.Primary
import com.agustincantero.mxiangcameraviewer.ui.theme.SurfaceContainer

// Asks for focus as soon as the screen is up, so a remote has something selected from the first frame.
@Composable
fun rememberInitialFocus(key: Any? = Unit): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) { requester.requestFocus() }
    return requester
}

// A single-line field for a TV. The box is what the D-pad lands on, and the keyboard opens only when OK is pressed, because a keyboard that pops up on every focus would cover the screen while the user is just moving through it.
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    password: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    var boxFocused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val box = focusRequester ?: remember { FocusRequester() }
    val input = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)
    val ringed = boxFocused || editing

    // Editing hands the focus to the text field and opens the keyboard; finishing hands it back to the box.
    LaunchedEffect(editing) {
        if (editing) {
            input.requestFocus()
            keyboard?.show()
        }
    }
    BackHandler(enabled = editing) {
        editing = false
        box.requestFocus()
    }

    FieldFrame(
        label = label,
        highlighted = ringed,
        modifier = modifier,
        fieldModifier = Modifier
            .focusRequester(box)
            .onFocusChanged { boxFocused = it.isFocused }
            .clickable(enabled = enabled) { editing = true },
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            readOnly = !editing,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurface),
            cursorBrush = SolidColor(Primary),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    editing = false
                    box.requestFocus()
                    focusManager.moveFocus(FocusDirection.Down)
                },
                onDone = {
                    editing = false
                    box.requestFocus()
                    onImeAction()
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = editing }
                .focusRequester(input)
                .onFocusChanged { if (!it.isFocused && editing) editing = false },
        )
    }
}

// A turning arc for waits, since the TV library ships no progress indicator.
@Composable
fun Spinner(modifier: Modifier = Modifier, diameter: Dp = 40.dp, color: Color = Primary) {
    val angle by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Canvas(modifier.size(diameter)) {
        val stroke = this.size.minDimension / 8f
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

package com.agustincantero.mxiangcameraviewer.ui

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.agustincantero.mxiangcameraviewer.R
import com.agustincantero.mxiangcameraviewer.data.Account
import com.agustincantero.mxiangcameraviewer.data.Camera
import com.agustincantero.mxiangcameraviewer.data.LightControl
import com.agustincantero.mxiangcameraviewer.data.Quality
import com.agustincantero.mxiangcameraviewer.data.SecureStore
import com.agustincantero.mxiangcameraviewer.data.VideoFit
import com.agustincantero.mxiangcameraviewer.media.LiveStream
import com.agustincantero.mxiangcameraviewer.media.LocalNetworks
import com.agustincantero.mxiangcameraviewer.media.StreamState
import com.agustincantero.mxiangcameraviewer.ui.theme.Error
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurface
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurfaceVariant
import com.agustincantero.mxiangcameraviewer.ui.theme.Primary
import com.agustincantero.mxiangcameraviewer.ui.theme.SurfaceContainer
import com.agustincantero.mxiangcameraviewer.ui.theme.VideoBackground
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LightState { Loading, Unavailable, Off, On, Busy }

// Everything a live view is made of, held apart from the composables so a recomposition never touches the connection.
class LiveController(
    private val camera: Camera,
    private val account: Account,
    private val store: SecureStore,
    private val context: Context,
) {
    var state by mutableStateOf<StreamState>(StreamState.Connecting)
    var videoSize by mutableStateOf<Pair<Int, Int>?>(null)
    var bitrate by mutableDoubleStateOf(0.0)
    var listening by mutableStateOf(false)
    var audioUnsupported by mutableStateOf(false)
    var quality by mutableStateOf(store.quality(camera.id))
    var light by mutableStateOf(LightState.Loading)

    private var stream: LiveStream? = null
    private var surface: Surface? = null
    private val lightControl = LightControl(account, camera)

    // Whether a session is wanted, so that leaving the screen and coming back never opens two.
    private var active = false

    fun start() {
        if (active) return
        active = true
        connect()
    }

    // The app in the background has nobody to show the picture to, so the session is closed, and opened again when it comes back.
    fun pause() {
        if (!active) return
        active = false
        stop()
    }

    private fun connect() {
        state = StreamState.Connecting
        videoSize = null
        bitrate = 0.0
        stream = LiveStream(
            camera = camera,
            account = account,
            quality = quality,
            onState = { state = it },
            onVideoSize = { w, h -> videoSize = w to h },
            onBitrate = { bitrate = it },
            onAudioUnsupported = {
                listening = false
                audioUnsupported = true
            },
            beforeOpen = { LocalNetworks.publish(context) },
        ).also {
            it.setSurface(surface)
            it.setListening(listening)
            it.start()
        }
    }

    // Stopping joins the worker, which can take a moment, so it is never done on the main thread.
    fun stop() {
        val old = stream
        stream = null
        thread(name = "live-stop", isDaemon = true) { old?.stop() }
    }

    fun setSurface(value: Surface?) {
        surface = value
        stream?.setSurface(value)
    }

    fun step(direction: String) {
        stream?.step(direction)
    }

    fun toggleListening() {
        listening = !listening
        audioUnsupported = false
        stream?.setListening(listening)
    }

    fun retry() {
        stop()
        connect()
    }

    // The quality is asked for when the session opens, so a change means opening it again.
    fun cycleQuality() {
        quality = quality.next()
        store.setQuality(camera.id, quality)
        stop()
        connect()
    }

    suspend fun loadLight() {
        light = withContext(Dispatchers.IO) {
            when (lightControl.read()) {
                null -> LightState.Unavailable
                true -> LightState.On
                false -> LightState.Off
            }
        }
    }

    suspend fun toggleLight() {
        val target = light == LightState.Off
        if (light != LightState.On && light != LightState.Off) return
        val before = light
        light = LightState.Busy
        val accepted = withContext(Dispatchers.IO) { lightControl.write(target) }
        light = if (accepted) (if (target) LightState.On else LightState.Off) else before
    }
}

@Composable
fun LiveScreen(
    camera: Camera,
    account: Account,
    store: SecureStore,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val controller = remember(camera.id) { LiveController(camera, account, store, context) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.start()
                Lifecycle.Event.ON_STOP -> controller.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        view.keepScreenOn = true
        onDispose {
            lifecycle.removeObserver(observer)
            view.keepScreenOn = false
            controller.pause()
        }
    }
    LaunchedEffect(controller) { controller.loadLight() }

    var overlay by remember { mutableStateOf(false) }
    // Not state, on purpose: bumping a state on every key would recompose the controls in the middle of a press.
    val lastKey = remember { longArrayOf(0L) }
    val selectPressed = remember { booleanArrayOf(false) }
    var chip by remember { mutableStateOf<PtzHint?>(null) }
    var lastStep by remember { mutableStateOf(0L) }
    val root = remember { FocusRequester() }
    val firstControl = remember { FocusRequester() }
    var fit by remember { mutableStateOf(store.videoFit()) }
    val retryFocus = remember { FocusRequester() }
    val failed = controller.state is StreamState.Failed

    BackHandler { if (overlay) overlay = false else onBack() }

    // The controls go away on their own, so a remote left alone does not leave them over the picture.
    LaunchedEffect(overlay) {
        if (overlay) {
            lastKey[0] = System.currentTimeMillis()
            while (System.currentTimeMillis() - lastKey[0] < 8000) delay(500)
            overlay = false
        }
    }
    LaunchedEffect(chip) {
        if (chip != null) {
            delay(1200)
            chip = null
        }
    }
    LaunchedEffect(overlay) {
        if (overlay) firstControl.requestFocus() else root.requestFocus()
    }
    // A failed connection offers its retry button, which takes the focus and gives it back once it is gone.
    LaunchedEffect(failed) {
        if (failed) retryFocus.requestFocus() else if (!overlay) root.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(VideoBackground)
            .onPreviewKeyEvent { event ->
                lastKey[0] = System.currentTimeMillis()
                // The focused button would use the first Back to drop its own highlight, so with the controls up Back is answered here, before it reaches the button: one press closes them.
                if (overlay && event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) overlay = false
                    true
                } else {
                    false
                }
            }
            .onKeyEvent { event ->
                if (overlay) return@onKeyEvent false
                val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (select) {
                    // The controls open when OK is released, not when it goes down: opened on the press, the first button takes the focus in time to receive the release and acts on it. A release whose press happened elsewhere, such as the tap on the camera that opened this screen, opens nothing.
                    if (event.type == KeyEventType.KeyDown) {
                        selectPressed[0] = true
                    } else if (event.type == KeyEventType.KeyUp && selectPressed[0]) {
                        selectPressed[0] = false
                        overlay = true
                    }
                    return@onKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionLeft -> "left" to R.string.ptz_left
                    Key.DirectionRight -> "right" to R.string.ptz_right
                    Key.DirectionUp -> "up" to R.string.ptz_up
                    Key.DirectionDown -> "down" to R.string.ptz_down
                    else -> return@onKeyEvent false
                }
                // Holding an arrow repeats the key, and each step is a round trip to the camera, so they are spaced out.
                val now = System.currentTimeMillis()
                if (camera.motorised && controller.state == StreamState.Streaming && now - lastStep >= 300) {
                    lastStep = now
                    controller.step(direction.first)
                    chip = PtzHint(direction.first, direction.second, (chip?.count ?: 0) + 1)
                }
                true
            }
            .focusRequester(root)
            .focusable(),
    ) {
        VideoSurface(controller, fit)
        StatusMessage(controller, retryFocus, onRetry = controller::retry)

        chip?.let { PtzChip(it) }

        Controls(
            visible = overlay,
            camera = camera,
            controller = controller,
            firstControl = firstControl,
            fit = fit,
            onFit = {
                fit = fit.next()
                store.setVideoFit(fit)
            },
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onLight = { scope.launch { controller.toggleLight() } },
        )
    }
}

@Composable
private fun VideoSurface(controller: LiveController, fit: VideoFit) {
    val size = controller.videoSize
    val ratio = if (size != null && size.second > 0) size.first.toFloat() / size.second else 16f / 9f

    // The picture runs edge to edge unless the viewer shrinks it, for a TV that crops the edges; the text and the controls stay inside the overscan-safe margins (5% on each side) either way.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize(fit.fraction), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = controller.setSurface(holder.surface)
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                            override fun surfaceDestroyed(holder: SurfaceHolder) = controller.setSurface(null)
                        })
                    }
                },
                modifier = Modifier.aspectRatio(ratio),
            )
        }
    }
}

@Composable
private fun StatusMessage(controller: LiveController, retryFocus: FocusRequester, onRetry: () -> Unit) {
    val state = controller.state
    val (text, isError) = when (state) {
        StreamState.Connecting -> stringResource(R.string.live_connecting) to false
        StreamState.WaitingPicture -> stringResource(R.string.live_waiting_picture) to false
        StreamState.Streaming -> return
        is StreamState.Retrying -> buildString {
            append(stringResource(R.string.live_reconnecting, state.seconds))
            if (state.reason.isNotEmpty()) append('\n').append(state.reason)
        } to false
        is StreamState.Failed -> stringResource(R.string.live_failed, state.message) to true
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 58.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.background(Color(0xCC101317), RoundedCornerShape(12.dp)).padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isError) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = Error, textAlign = TextAlign.Center)
                Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
                    Text(stringResource(R.string.action_retry))
                }
            } else {
                Spinner()
                Text(text, style = MaterialTheme.typography.bodyLarge, color = OnSurface, textAlign = TextAlign.Center)
            }
        }
    }
}

// What the last pan or tilt step was, with a count that changes on every step so the hint's timer starts over.
private data class PtzHint(val direction: String, val text: Int, val count: Int)

// The hint sits on the side the camera is turning towards, with an arrow, so it reads at a glance from the couch; it keeps inside the overscan-safe margins.
@Composable
private fun PtzChip(hint: PtzHint) {
    val (alignment, arrow) = when (hint.direction) {
        "left" -> Alignment.CenterStart to R.drawable.ic_arrow_back
        "right" -> Alignment.CenterEnd to R.drawable.ic_arrow_forward
        "up" -> Alignment.TopCenter to R.drawable.ic_arrow_upward
        else -> Alignment.BottomCenter to R.drawable.ic_arrow_downward
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 32.dp), contentAlignment = alignment) {
        Row(
            Modifier.background(Color(0xCC101317), RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(arrow), contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
            Text(stringResource(hint.text), style = MaterialTheme.typography.titleMedium, color = Primary)
        }
    }
}

@Composable
private fun Controls(
    visible: Boolean,
    camera: Camera,
    controller: LiveController,
    firstControl: FocusRequester,
    fit: VideoFit,
    onFit: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onLight: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // The bars slide in from their own edge while fading, and leave a little faster than they came, as a player's controls do.
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it / 3 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 3 },
        ) {
            // The scrims keep the text readable over any picture.
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent)))
                    .padding(bottom = 48.dp),
            ) { Header(camera, controller) }
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 3 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 3 },
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB000000))))
                    .padding(start = 58.dp, end = 58.dp, top = 56.dp, bottom = 32.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
                    LabelledButton(
                        label = stringResource(if (controller.listening) R.string.live_mute else R.string.live_listen),
                        modifier = Modifier.focusRequester(firstControl),
                        onClick = controller::toggleListening,
                    ) {
                        Icon(
                            painterResource(if (controller.listening) R.drawable.ic_volume_up else R.drawable.ic_volume_off),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    if (controller.light != LightState.Unavailable && controller.light != LightState.Loading) {
                        LabelledButton(
                            label = stringResource(R.string.live_light),
                            subtitle = stringResource(
                                when (controller.light) {
                                    LightState.On -> R.string.live_light_on
                                    LightState.Off -> R.string.live_light_off
                                    else -> R.string.live_light_busy
                                },
                            ),
                            enabled = controller.light != LightState.Busy,
                            onClick = onLight,
                        ) {
                            Icon(painterResource(R.drawable.ic_lightbulb), contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                    LabelledButton(
                        label = stringResource(R.string.live_quality),
                        subtitle = stringResource(controller.quality.label),
                        onClick = controller::cycleQuality,
                    ) {
                        // Smaller than the rest of the text on purpose: "AUTO" has to fit inside a round 40 dp button.
                        Text(
                            stringResource(controller.quality.badge),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                        )
                    }
                    LabelledButton(
                        label = stringResource(R.string.live_fit),
                        subtitle = stringResource(fit.label),
                        onClick = onFit,
                    ) {
                        Icon(painterResource(R.drawable.ic_settings_overscan), contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                    LabelledButton(
                        label = stringResource(R.string.live_favorite),
                        onClick = onToggleFavorite,
                    ) {
                        Icon(painterResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline), contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(camera: Camera, controller: LiveController) {
    Column(Modifier.padding(start = 58.dp, end = 58.dp, top = 28.dp)) {
        Text(cameraLabel(camera), style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        if (controller.state == StreamState.Streaming) {
            val size = controller.videoSize
            val sizeText = if (size != null) stringResource(R.string.live_status_size, size.first, size.second) else ""
            Text(
                stringResource(R.string.live_status, sizeText, bitrateText(controller.bitrate)),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
            )
        }
        if (controller.audioUnsupported) {
            Text(
                stringResource(R.string.live_audio_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = Error,
            )
        }
    }
}

@Composable
private fun bitrateText(bitsPerSecond: Double): String =
    if (bitsPerSecond >= 1_000_000.0) {
        stringResource(R.string.unit_mbps, String.format(Locale.getDefault(), "%.1f", bitsPerSecond / 1_000_000.0))
    } else {
        stringResource(R.string.unit_kbps, (bitsPerSecond / 1000.0).roundToInt())
    }

// An icon button with its name under it, and optionally its current value, since an icon alone is not enough at ten feet.
@Composable
private fun LabelledButton(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
        Text(label, style = MaterialTheme.typography.labelLarge, color = OnSurface, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

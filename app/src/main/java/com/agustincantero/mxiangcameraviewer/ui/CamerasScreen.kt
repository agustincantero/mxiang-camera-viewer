package com.agustincantero.mxiangcameraviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import com.agustincantero.mxiangcameraviewer.R
import com.agustincantero.mxiangcameraviewer.data.Camera
import com.agustincantero.mxiangcameraviewer.ui.theme.Error
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurface
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurfaceVariant
import com.agustincantero.mxiangcameraviewer.ui.theme.Primary
import com.agustincantero.mxiangcameraviewer.ui.theme.SurfaceContainer

@Composable
fun cameraLabel(camera: Camera): String {
    val base = camera.name.ifEmpty { camera.model }
    return if (camera.isSecondaryLens) stringResource(R.string.cameras_lens_2, base) else base
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CamerasScreen(
    cameras: List<Camera>,
    state: CamerasState,
    focusId: String?,
    favoriteId: String?,
    onOpen: (Camera) -> Unit,
    onRefresh: () -> Unit,
    onAccount: () -> Unit,
) {
    val initialCard = remember { FocusRequester() }
    val refresh = remember { FocusRequester() }
    val retry = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    // Coming back from a live view, the focus returns to the camera that was open.
    val initialIndex = cameras.indexOfFirst { it.id == focusId }.coerceAtLeast(0)

    // Focus lands on that camera; while loading, on Refresh; and on the button of the message when there is nothing else to press.
    LaunchedEffect(state, cameras.isEmpty()) {
        when {
            state == CamerasState.Ready && cameras.isNotEmpty() -> {
                grid.scrollToItem(initialIndex)
                withFrameNanos { }
                runCatching { initialCard.requestFocus() }
            }
            state is CamerasState.Loading -> refresh.requestFocus()
            else -> retry.requestFocus()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(start = 58.dp, end = 58.dp, top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.cameras_title),
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, modifier = Modifier.focusRequester(refresh)) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.cd_refresh), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onAccount) {
                Icon(painterResource(R.drawable.ic_person), contentDescription = stringResource(R.string.cd_account), modifier = Modifier.size(20.dp))
            }
        }

        when {
            state is CamerasState.Loading -> Row(
                Modifier.padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spinner()
                Text(stringResource(R.string.cameras_loading), style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariant)
            }
            state is CamerasState.Error ->
                Message(stringResource(R.string.cameras_error, state.message), retry, R.string.action_retry, onRefresh, error = true)
            cameras.isEmpty() -> Message(stringResource(R.string.cameras_empty), retry, R.string.action_refresh, onRefresh)
            else -> LazyVerticalGrid(
                state = grid,
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 28.dp, top = 8.dp),
            ) {
                itemsIndexed(cameras, key = { _, camera -> camera.id }) { index, camera ->
                    CameraCard(
                        camera = camera,
                        isFavorite = camera.id == favoriteId,
                        onClick = { onOpen(camera) },
                        modifier = if (index == initialIndex) Modifier.focusRequester(initialCard) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, focus: FocusRequester, action: Int, onAction: () -> Unit, error: Boolean = false) {
    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (error) Error else OnSurfaceVariant)
        Button(onClick = onAction, modifier = Modifier.focusRequester(focus)) { Text(stringResource(action)) }
    }
}

// The standard card of the guide: a 16:9 picture, with the title and subtitle under it and as wide as the picture.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CameraCard(camera: Camera, isFavorite: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val address = if (camera.hasAddress) camera.ip else stringResource(R.string.cameras_address_unknown)

    StandardCardContainer(
        modifier = modifier,
        imageCard = { interaction ->
            Card(
                onClick = onClick,
                interactionSource = interaction,
                colors = CardDefaults.colors(containerColor = SurfaceContainer),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_videocam),
                        contentDescription = stringResource(R.string.cd_camera),
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    if (isFavorite) {
                        Icon(
                            painterResource(R.drawable.ic_star),
                            contentDescription = stringResource(R.string.cd_favorite_camera),
                            tint = Primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(22.dp),
                        )
                    }
                }
            }
        },
        title = {
            Text(
                cameraLabel(camera),
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        },
        subtitle = {
            Text(
                stringResource(R.string.cameras_detail, camera.model, address),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

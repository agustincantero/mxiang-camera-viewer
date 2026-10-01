package com.agustincantero.mxiangcameraviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.agustincantero.mxiangcameraviewer.ui.AccountPanel
import com.agustincantero.mxiangcameraviewer.ui.AppViewModel
import com.agustincantero.mxiangcameraviewer.ui.CamerasScreen
import com.agustincantero.mxiangcameraviewer.ui.LiveScreen
import com.agustincantero.mxiangcameraviewer.ui.LoginScreen
import com.agustincantero.mxiangcameraviewer.ui.Screen
import com.agustincantero.mxiangcameraviewer.ui.SignOutDialog
import com.agustincantero.mxiangcameraviewer.ui.theme.OnSurfaceVariant
import com.agustincantero.mxiangcameraviewer.ui.theme.Surface
import com.agustincantero.mxiangcameraviewer.ui.theme.MxiangCameraViewerTheme

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MxiangCameraViewerTheme {
                App(model)
            }
        }
    }
}

@Composable
private fun App(model: AppViewModel) {
    Box(Modifier.fillMaxSize().background(Surface)) {
        when (val screen = model.screen) {
            Screen.Starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.login_working),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceVariant,
                )
            }

            Screen.Login -> LoginScreen(
                state = model.login,
                onRegion = model::setRegion,
                onSignIn = model::signIn,
                onCaptcha = model::submitCaptcha,
                onVerify = model::submitVerify,
                onBack = model::backToCredentials,
            )

            Screen.Cameras -> {
                CamerasScreen(
                    cameras = model.cameras,
                    state = model.camerasState,
                    focusId = model.lastOpenedId,
                    favoriteId = model.favoriteCameraId,
                    onOpen = model::open,
                    onRefresh = model::loadCameras,
                    onAccount = { model.showAccount = true },
                )
                val account = model.account
                if (model.showAccount && account != null) {
                    AccountPanel(
                        account = account,
                        onSignOut = { model.showSignOut = true },
                        onDismiss = { model.showAccount = false },
                    )
                }
                if (model.showSignOut) {
                    SignOutDialog(onConfirm = model::signOut, onDismiss = { model.showSignOut = false })
                }
            }

            is Screen.Live -> {
                val account = model.account
                if (account != null) {
                    LiveScreen(
                        camera = screen.camera,
                        account = account,
                        store = model.store,
                        isFavorite = model.favoriteCameraId == screen.camera.id,
                        onToggleFavorite = { model.toggleFavorite(screen.camera) },
                        onBack = model::closeLive,
                    )
                }
            }
        }
    }
}

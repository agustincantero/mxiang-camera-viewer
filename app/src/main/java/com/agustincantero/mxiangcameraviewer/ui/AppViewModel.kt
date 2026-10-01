package com.agustincantero.mxiangcameraviewer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agustincantero.mxiangcameraviewer.R
import com.agustincantero.mxiangcameraviewer.data.Account
import com.agustincantero.mxiangcameraviewer.data.Bridge
import com.agustincantero.mxiangcameraviewer.data.Camera
import com.agustincantero.mxiangcameraviewer.data.CloudRepository
import com.agustincantero.mxiangcameraviewer.data.LoginResult
import com.agustincantero.mxiangcameraviewer.data.Region
import com.agustincantero.mxiangcameraviewer.data.SecureStore
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Starting : Screen
    data object Login : Screen
    data object Cameras : Screen
    data class Live(val camera: Camera) : Screen
}

enum class LoginStep { Credentials, Captcha, Verify }

data class LoginUiState(
    val step: LoginStep = LoginStep.Credentials,
    val working: Boolean = false,
    val error: String? = null,
    val region: Region = Region.Default,
    val captcha: Bitmap? = null,
    val verifyTarget: String = "",
)

sealed interface CamerasState {
    data object Loading : CamerasState
    data object Ready : CamerasState
    data class Error(val message: String) : CamerasState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val store = SecureStore(application)
    private val repository = CloudRepository()
    // Only the first list of a launch jumps to the favorite camera, so coming back to the list, or refreshing it, stays put.
    private var openDefaultAtStart = true

    var screen by mutableStateOf<Screen>(Screen.Starting)
        private set
    var account by mutableStateOf<Account?>(null)
        private set
    var login by mutableStateOf(LoginUiState())
        private set
    var cameras by mutableStateOf<List<Camera>>(emptyList())
        private set
    var camerasState by mutableStateOf<CamerasState>(CamerasState.Loading)
        private set
    var favoriteCameraId by mutableStateOf(store.favoriteCameraId())
        private set
    var lastOpenedId by mutableStateOf<String?>(null)
        private set
    var showAccount by mutableStateOf(false)
    var showSignOut by mutableStateOf(false)

    init {
        // An unknown method is answered offline, so this shows in the log whether the Go library loaded on this device.
        Log.i("MxiangViewer", "bridge answered: ${Bridge.call("ping")}")
        restoreSession()
    }

    private fun text(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    // A saved session is checked with Xiaomi before it is trusted, since the token may have been revoked.
    private fun restoreSession() {
        val saved = store.loadAccount()
        if (saved == null) {
            screen = Screen.Login
            return
        }
        viewModelScope.launch {
            when (val result = repository.restore(saved)) {
                is LoginResult.Success -> finishLogin(result.account)
                is LoginResult.Failure -> {
                    login = login.copy(error = text(R.string.login_error, result.message))
                    screen = Screen.Login
                }
                else -> {
                    login = login.copy(error = text(R.string.login_session_expired))
                    screen = Screen.Login
                }
            }
        }
    }

    fun setRegion(region: Region) {
        login = login.copy(region = region)
    }

    fun signIn(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) {
            login = login.copy(error = text(R.string.login_missing_fields))
            return
        }
        login = login.copy(working = true, error = null)
        viewModelScope.launch { handle(repository.beginLogin(username.trim(), password, login.region)) }
    }

    // Leaves a captcha or verification step, for instance to fix the region, which cannot be changed once Xiaomi has answered.
    fun backToCredentials() {
        login = login.copy(step = LoginStep.Credentials, working = false, error = null, captcha = null)
    }

    fun submitCaptcha(code: String) {
        if (code.isBlank()) return
        login = login.copy(working = true, error = null)
        viewModelScope.launch { handle(repository.submitCaptcha(code.trim())) }
    }

    fun submitVerify(code: String) {
        if (code.isBlank()) return
        login = login.copy(working = true, error = null)
        viewModelScope.launch { handle(repository.submitVerify(code.trim())) }
    }

    private fun handle(result: LoginResult) {
        when (result) {
            is LoginResult.Success -> finishLogin(result.account)
            is LoginResult.Captcha -> login = login.copy(
                step = LoginStep.Captcha,
                working = false,
                captcha = BitmapFactory.decodeByteArray(result.png, 0, result.png.size),
            )
            is LoginResult.Verify -> login = login.copy(
                step = LoginStep.Verify,
                working = false,
                verifyTarget = result.phone.ifEmpty { result.email },
            )
            is LoginResult.Failure -> login = login.copy(
                step = LoginStep.Credentials,
                working = false,
                error = text(R.string.login_error, result.message),
            )
        }
    }

    private fun finishLogin(signedIn: Account) {
        store.saveAccount(signedIn)
        account = signedIn
        login = LoginUiState(region = Region.fromCode(signedIn.region))
        screen = Screen.Cameras
        loadCameras()
    }

    fun loadCameras() {
        val current = account ?: return
        camerasState = CamerasState.Loading
        viewModelScope.launch {
            repository.cameras(current).fold(
                onSuccess = { list ->
                    cameras = list
                    camerasState = CamerasState.Ready
                    if (openDefaultAtStart) {
                        openDefaultAtStart = false
                        list.firstOrNull { it.id == favoriteCameraId }?.let(::open)
                    }
                },
                onFailure = { camerasState = CamerasState.Error(it.message ?: "") },
            )
        }
    }

    fun open(camera: Camera) {
        lastOpenedId = camera.id
        screen = Screen.Live(camera)
    }

    // One favorite at a time, which the app opens on its own at start.
    fun toggleFavorite(camera: Camera) {
        favoriteCameraId = if (favoriteCameraId == camera.id) null else camera.id
        store.setFavoriteCameraId(favoriteCameraId)
    }

    fun closeLive() {
        screen = Screen.Cameras
    }

    fun signOut() {
        val current = account
        showAccount = false
        showSignOut = false
        viewModelScope.launch {
            if (current != null) repository.forget(current)
            store.clearAccount()
            store.setFavoriteCameraId(null)
            favoriteCameraId = null
            account = null
            cameras = emptyList()
            login = LoginUiState()
            screen = Screen.Login
        }
    }
}

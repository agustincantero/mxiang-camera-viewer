package com.agustincantero.mxiangcameraviewer.data

import android.util.Base64
import com.agustincantero.mxiangcameraviewer.data.Bridge.errorText
import com.agustincantero.mxiangcameraviewer.data.Bridge.isOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Everything the app asks of Xiaomi's cloud, over the bridge's control plane. Every call blocks, so they all run off the main thread.
class CloudRepository {

    suspend fun beginLogin(username: String, password: String, region: Region): LoginResult = io {
        loginResult(
            Bridge.call(
                "login.begin",
                JSONObject().put("username", username).put("password", password).put("region", region.code),
            ),
        )
    }

    suspend fun submitCaptcha(code: String): LoginResult = io {
        loginResult(Bridge.call("login.captcha", JSONObject().put("code", code)))
    }

    suspend fun submitVerify(ticket: String): LoginResult = io {
        loginResult(Bridge.call("login.verify", JSONObject().put("ticket", ticket)))
    }

    suspend fun restore(account: Account): LoginResult = io {
        loginResult(
            Bridge.call(
                "login.token",
                JSONObject().put("user_id", account.userId).put("region", account.region).put("token", account.token),
            ),
        )
    }

    suspend fun forget(account: Account) = io {
        Bridge.call("account.forget", JSONObject().put("user_id", account.userId))
        Unit
    }

    // Every camera of the account, with a dual-lens one listed once per lens.
    suspend fun cameras(account: Account): Result<List<Camera>> = io {
        val response = Bridge.call("device.list", JSONObject().put("user_id", account.userId))
        if (!response.isOk()) {
            return@io Result.failure(IllegalStateException(response.errorText()))
        }

        val devices = response.optJSONArray("devices")
        val cameras = mutableListOf<Camera>()
        for (i in 0 until (devices?.length() ?: 0)) {
            val device = devices!!.getJSONObject(i)
            val model = device.optString("model")
            if (!model.contains(".camera.") && !model.contains(".cateye.")) {
                continue
            }
            val base = Camera(
                did = device.optString("did"),
                name = device.optString("name"),
                model = model,
                ip = device.optString("ip"),
                channel = "",
            )
            cameras += base
            if (base.isDualLens) {
                cameras += base.copy(channel = "1")
            }
        }
        Result.success(cameras)
    }

    private fun loginResult(response: JSONObject): LoginResult {
        if (!response.isOk()) {
            return LoginResult.Failure(response.errorText())
        }
        return when (response.optString("status")) {
            "success" -> {
                val account = response.getJSONObject("account")
                LoginResult.Success(
                    Account(
                        userId = account.optString("user_id"),
                        region = account.optString("region"),
                        token = account.optString("token"),
                    ),
                )
            }
            "captcha" -> LoginResult.Captcha(Base64.decode(response.optString("captcha_png"), Base64.DEFAULT))
            "verify" -> LoginResult.Verify(response.optString("verify_phone"), response.optString("verify_email"))
            else -> LoginResult.Failure("unexpected login status")
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}

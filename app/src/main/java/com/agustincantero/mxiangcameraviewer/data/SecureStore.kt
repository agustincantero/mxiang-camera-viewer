package com.agustincantero.mxiangcameraviewer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Keeps the session token encrypted with a key that never leaves the Android Keystore, since the token is a bearer credential for the whole Xiaomi account.
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("mxiang_camera_viewer", Context.MODE_PRIVATE)

    fun loadAccount(): Account? {
        val userId = prefs.getString(KEY_USER, null) ?: return null
        val encrypted = prefs.getString(KEY_TOKEN, null) ?: return null
        val token = decrypt(encrypted) ?: return null
        return Account(userId, prefs.getString(KEY_REGION, "") ?: "", token)
    }

    fun saveAccount(account: Account) {
        prefs.edit()
            .putString(KEY_USER, account.userId)
            .putString(KEY_REGION, account.region)
            .putString(KEY_TOKEN, encrypt(account.token))
            .apply()
    }

    fun clearAccount() {
        prefs.edit().remove(KEY_USER).remove(KEY_REGION).remove(KEY_TOKEN).apply()
    }

    fun quality(cameraId: String): Quality = Quality.fromCode(prefs.getString("quality_$cameraId", null))

    fun setQuality(cameraId: String, quality: Quality) {
        prefs.edit().putString("quality_$cameraId", quality.code).apply()
    }

    // One setting for the whole device, since overscan belongs to the TV and not to the camera.
    fun videoFit(): VideoFit = VideoFit.fromCode(prefs.getString(KEY_VIDEO_FIT, null))

    fun setVideoFit(fit: VideoFit) {
        prefs.edit().putString(KEY_VIDEO_FIT, fit.code).apply()
    }

    // The favorite camera, which the app opens on its own at start, if any.
    fun favoriteCameraId(): String? = prefs.getString(KEY_FAVORITE_CAMERA, null)

    fun setFavoriteCameraId(cameraId: String?) {
        prefs.edit().apply { if (cameraId == null) remove(KEY_FAVORITE_CAMERA) else putString(KEY_FAVORITE_CAMERA, cameraId) }.apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }

    // A token that cannot be decrypted, after a backup restore or a wiped keystore, is the same as none.
    private fun decrypt(encoded: String): String? = try {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_SIZE))
        String(cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val KEY_ALIAS = "mxiang_camera_viewer_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val KEY_USER = "user_id"
        const val KEY_REGION = "region"
        const val KEY_TOKEN = "token"
        const val KEY_VIDEO_FIT = "video_fit"
        const val KEY_FAVORITE_CAMERA = "favorite_camera"
    }
}

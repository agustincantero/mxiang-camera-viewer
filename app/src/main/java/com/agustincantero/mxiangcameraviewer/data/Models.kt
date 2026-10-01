package com.agustincantero.mxiangcameraviewer.data

import androidx.annotation.StringRes
import com.agustincantero.mxiangcameraviewer.R

data class Account(val userId: String, val region: String, val token: String)

// The codes are the ones the bridge sends to Xiaomi; an empty one is the default cloud.
enum class Region(val code: String, @param:StringRes val label: Int) {
    Default("", R.string.region_default),
    Germany("de", R.string.region_de),
    India("i2", R.string.region_i2),
    Russia("ru", R.string.region_ru),
    Singapore("sg", R.string.region_sg),
    UnitedStates("us", R.string.region_us);

    fun next(): Region = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromCode(code: String): Region = entries.firstOrNull { it.code == code } ?: Default
    }
}

// What the camera is asked for when the session opens, so changing it means reconnecting.
enum class Quality(val code: String, @param:StringRes val label: Int, @param:StringRes val badge: Int) {
    Auto("auto", R.string.quality_auto, R.string.quality_badge_auto),
    Standard("sd", R.string.quality_sd, R.string.quality_badge_sd),
    High("hd", R.string.quality_hd, R.string.quality_badge_hd);

    fun next(): Quality = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromCode(code: String?): Quality = entries.firstOrNull { it.code == code } ?: High
    }
}

// How much of the screen the picture takes. A TV that draws past the edge of its panel (overscan) crops a full-screen picture, so the viewer can shrink it until the edges show.
enum class VideoFit(val code: String, val fraction: Float, @param:StringRes val label: Int) {
    Full("full", 1f, R.string.fit_full),
    Margin5("95", 0.95f, R.string.fit_95),
    Margin10("90", 0.9f, R.string.fit_90);

    fun next(): VideoFit = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromCode(code: String?): VideoFit = entries.firstOrNull { it.code == code } ?: Full
    }
}

// One tile: a dual-lens camera contributes two of these, sharing an address but differing in channel.
data class Camera(
    val did: String,
    val name: String,
    val model: String,
    val ip: String,
    val channel: String,
) {
    val id: String get() = "$did/$channel"

    val isDualLens: Boolean
        get() = model.contains(".hlmax") || model.contains("cw500") || model.contains("500dh")

    val isSecondaryLens: Boolean get() = channel.isNotEmpty() && channel != "0"

    val hasAddress: Boolean get() = ip.isNotEmpty() && ip != "0.0.0.0"

    private val hasMotor: Boolean
        get() = model.contains("hlc8") || model.contains("500dh") || model.contains("cw500") ||
            model.contains(".hlmax") || isCw300 || model == "isa.camera.700sa"

    val isCw300: Boolean get() = model == "mxiang.camera.moc001" || model == "mxiang.camera.moc006"

    // On a dual-lens model the motor turns the dome, and the second lens is fixed.
    val motorised: Boolean get() = hasMotor && (!isDualLens || !isSecondaryLens)
}

sealed interface LoginResult {
    data class Success(val account: Account) : LoginResult
    data class Captcha(val png: ByteArray) : LoginResult
    data class Verify(val phone: String, val email: String) : LoginResult
    data class Failure(val message: String) : LoginResult
}

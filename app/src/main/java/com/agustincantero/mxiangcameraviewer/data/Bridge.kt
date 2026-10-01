package com.agustincantero.mxiangcameraviewer.data

import org.json.JSONObject
import xmbridge.Xmbridge

// The control plane of the Go bridge: JSON in, JSON out, and errors travel inside the envelope.
object Bridge {
    fun call(method: String, request: JSONObject = JSONObject()): JSONObject = try {
        JSONObject(Xmbridge.call(method, request.toString()))
    } catch (e: Throwable) {
        // Also catches a native library that will not load on this device's processor, which is an Error rather than an Exception.
        JSONObject().put("ok", false).put("error", e.message ?: e.javaClass.simpleName)
    }

    fun JSONObject.isOk(): Boolean = optBoolean("ok", false)

    fun JSONObject.errorText(): String = optString("error", "unknown error")
}

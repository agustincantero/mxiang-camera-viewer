package com.agustincantero.mxiangcameraviewer.data

import com.agustincantero.mxiangcameraviewer.data.Bridge.isOk
import org.json.JSONArray
import org.json.JSONObject

// The fill light, a MIoT property the camera keeps. Only some cameras have one, so a read that comes back empty means there is nothing to show.
class LightControl(private val account: Account, private val camera: Camera) {
    // Property ids from each model's MIoT specification, the same table the Windows app uses.
    private val siid = if (camera.isCw300) 16 else 6
    private val piid = if (camera.isCw300) 1 else 16

    // Blocking. Null when the camera does not report the light.
    fun read(): Boolean? {
        val response = Bridge.call(
            "miot.get",
            JSONObject()
                .put("user_id", account.userId)
                .put("did", camera.did)
                .put("props", JSONArray().put(JSONObject().put("siid", siid).put("piid", piid))),
        )
        if (!response.isOk()) {
            return null
        }
        val entry = response.optJSONArray("result")?.optJSONObject(0) ?: return null
        if (entry.optInt("code", -1) != 0 || !entry.has("value")) {
            return null
        }
        return when (val value = entry.get("value")) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> null
        }
    }

    // Blocking. Returns whether the camera accepted the change.
    fun write(on: Boolean): Boolean {
        val response = Bridge.call(
            "miot.set",
            JSONObject()
                .put("user_id", account.userId)
                .put("did", camera.did)
                .put(
                    "props",
                    JSONArray().put(JSONObject().put("siid", siid).put("piid", piid).put("value", on)),
                ),
        )
        if (!response.isOk()) {
            return false
        }
        val entry = response.optJSONArray("result")?.optJSONObject(0) ?: return false
        return entry.optInt("code", -1) == 0
    }
}

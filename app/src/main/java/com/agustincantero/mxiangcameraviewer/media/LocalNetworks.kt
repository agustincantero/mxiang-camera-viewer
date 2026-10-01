package com.agustincantero.mxiangcameraviewer.media

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import xmbridge.Xmbridge

// Android 11 and later stop an app from listing its own network interfaces, which the bridge needs to broadcast a search for a camera, so the networks are read here from the OS and handed over.
object LocalNetworks {
    fun publish(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val network = manager.activeNetwork ?: return
        val properties = manager.getLinkProperties(network) ?: return

        val blocks = properties.linkAddresses
            .filter { it.address is Inet4Address && !it.address.isLoopbackAddress && it.prefixLength in 8..30 }
            .joinToString(",") { "${it.address.hostAddress}/${it.prefixLength}" }

        try {
            Xmbridge.setLocalNetworks(blocks)
        } catch (e: Throwable) {
            // Without the networks the bridge only searches by unicast, which is what it does on a platform that never needed this.
        }
    }
}

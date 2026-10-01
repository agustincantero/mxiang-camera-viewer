package com.agustincantero.mxiangcameraviewer.media

import android.view.Surface
import com.agustincantero.mxiangcameraviewer.data.Account
import com.agustincantero.mxiangcameraviewer.data.Camera
import com.agustincantero.mxiangcameraviewer.data.Quality
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import org.json.JSONObject
import xmbridge.Stream
import xmbridge.Xmbridge

sealed interface StreamState {
    data object Connecting : StreamState
    data object WaitingPicture : StreamState
    data object Streaming : StreamState
    data class Retrying(val seconds: Int, val reason: String) : StreamState
    data class Failed(val message: String) : StreamState
}

// One live session with a camera: it connects, reads access units, and reconnects with backoff when the camera drops it. Callbacks arrive on its own thread.
class LiveStream(
    private val camera: Camera,
    private val account: Account,
    private val quality: Quality,
    private val onState: (StreamState) -> Unit,
    private val onVideoSize: (width: Int, height: Int) -> Unit,
    private val onBitrate: (bitsPerSecond: Double) -> Unit,
    private val onAudioUnsupported: () -> Unit,
    private val beforeOpen: () -> Unit = {},
) {
    private val lock = Any()
    private val commands = Executors.newSingleThreadExecutor()
    private var worker: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var current: Stream? = null

    @Volatile
    private var surface: Surface? = null

    @Volatile
    private var listening = false

    private var decoder: VideoDecoder? = null
    private var decoderCodec = 0
    private var audio: AudioOut? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "live-stream", isDaemon = true) { run() }
    }

    fun stop() {
        running = false
        current?.close()
        worker?.join(1500)
        commands.shutdownNow()
        synchronized(lock) {
            releaseDecoder()
            releaseAudio()
        }
    }

    // The picture can only be drawn while there is a surface, so a lost one drops the decoder and a new one waits for the next keyframe.
    fun setSurface(value: Surface?) {
        surface = value
        if (value == null) {
            synchronized(lock) { releaseDecoder() }
        }
    }

    fun setListening(on: Boolean) {
        listening = on
        if (!on) {
            synchronized(lock) { releaseAudio() }
        }
    }

    // One step per call: the camera moves a fixed amount and stops by itself.
    fun step(direction: String) {
        val stream = current ?: return
        commands.execute {
            stream.command(JSONObject().put("method", "ptz.step").put("direction", direction).toString())
        }
    }

    private fun run() {
        var failures = 0
        while (running) {
            onState(StreamState.Connecting)
            beforeOpen()
            val stream = Xmbridge.openStream(openRequest())

            if (!stream.opened()) {
                val info = parse(stream.response())
                val reason = info.optString("error", "unknown error")
                if (info.optBoolean("permanent")) {
                    onState(StreamState.Failed(reason))
                    return
                }
                failures++
                if (!wait(minOf(2 + failures * 2, 15), reason)) return
                continue
            }

            failures = 0
            current = stream
            onState(StreamState.WaitingPicture)
            var reason = ""
            try {
                readLoop(stream)
            } catch (e: Exception) {
                reason = e.message ?: ""
            } finally {
                current = null
                stream.close()
                synchronized(lock) { releaseDecoder() }
            }
            if (running && !wait(2, reason)) return
        }
    }

    // Counts down in view of the user, one second at a time so a stop is noticed at once.
    private fun wait(seconds: Int, reason: String): Boolean {
        for (left in seconds downTo 1) {
            if (!running) return false
            onState(StreamState.Retrying(left, reason))
            Thread.sleep(1000)
        }
        return running
    }

    private fun readLoop(stream: Stream) {
        var bytes = 0L
        var windowStart = System.nanoTime()

        while (running) {
            val frame = stream.read()
            val data = frame.data()
            bytes += data.size

            when (frame.kind().toInt()) {
                KIND_VIDEO -> handleVideo(frame.codec().toInt(), frame.keyframe(), frame.ptsMs(), data)
                KIND_AUDIO -> handleAudio(frame.codec().toInt(), frame.sampleRate().toInt(), data)
            }

            val now = System.nanoTime()
            if (now - windowStart >= 1_000_000_000L) {
                onBitrate(bytes * 8.0 * 1e9 / (now - windowStart))
                bytes = 0
                windowStart = now
            }
        }
    }

    private fun handleVideo(codec: Int, keyframe: Boolean, ptsMs: Long, data: ByteArray) {
        synchronized(lock) {
            if (decoder != null && decoderCodec != codec) {
                releaseDecoder()
            }
            if (decoder == null) {
                // A decoder can only start on a keyframe, and only once there is somewhere to draw.
                val target = surface
                if (!keyframe || target == null) return
                decoder = try {
                    VideoDecoder(target, codec == CODEC_H265) { width, height ->
                        onVideoSize(width, height)
                        onState(StreamState.Streaming)
                    }
                } catch (e: Exception) {
                    running = false
                    onState(StreamState.Failed(e.message ?: e.javaClass.simpleName))
                    return
                }
                decoderCodec = codec
            }
            decoder?.queue(data, ptsMs, keyframe)
        }
    }

    private fun handleAudio(codec: Int, sampleRate: Int, data: ByteArray) {
        if (!listening) return
        synchronized(lock) {
            if (audio == null) {
                audio = AudioOut.create(codec, sampleRate)
                if (audio == null) {
                    listening = false
                    onAudioUnsupported()
                    return
                }
            }
            audio?.push(data)
        }
    }

    private fun releaseDecoder() {
        decoder?.release()
        decoder = null
        decoderCodec = 0
    }

    private fun releaseAudio() {
        audio?.release()
        audio = null
    }

    private fun openRequest(): String = JSONObject()
        .put("user_id", account.userId)
        .put("did", camera.did)
        .put("model", camera.model)
        .put("ip", camera.ip)
        .put("channel", camera.channel)
        .put("quality", quality.code)
        .put("transport", "")
        .put("audio", true)
        .toString()

    private fun parse(json: String): JSONObject = try {
        JSONObject(json)
    } catch (e: Exception) {
        JSONObject()
    }

    private companion object {
        const val KIND_VIDEO = 1
        const val KIND_AUDIO = 2
        const val CODEC_H265 = 2
    }
}

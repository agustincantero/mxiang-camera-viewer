package com.agustincantero.mxiangcameraviewer.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import kotlin.concurrent.thread

// Hardware decoder for the camera's Annex-B stream, rendering straight into a surface. Frames are shown the moment they are decoded, because a live view has no use for a jitter buffer.
class VideoDecoder(
    surface: Surface,
    h265: Boolean,
    private val onSize: (width: Int, height: Int) -> Unit,
) {
    private val codec: MediaCodec
    private val drain: Thread

    @Volatile
    private var running = true

    init {
        val mime = if (h265) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
        // The real size comes from the stream's own parameter sets; the maximums let the decoder cope with whichever profile the camera picks.
        val format = MediaFormat.createVideoFormat(mime, 1920, 1080).apply {
            setInteger(MediaFormat.KEY_MAX_WIDTH, 3840)
            setInteger(MediaFormat.KEY_MAX_HEIGHT, 2160)
            setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
        }
        codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, surface, null, 0)
        codec.start()
        drain = thread(name = "video-output", isDaemon = true) { drainOutput() }
    }

    // Returns false when the decoder had no room, in which case the frame is dropped.
    fun queue(data: ByteArray, ptsMs: Long, keyframe: Boolean): Boolean {
        if (!running) return false
        return try {
            val index = codec.dequeueInputBuffer(20_000)
            if (index < 0) return false
            val buffer = codec.getInputBuffer(index) ?: return false
            if (data.size > buffer.capacity()) {
                codec.queueInputBuffer(index, 0, 0, ptsMs * 1000, 0)
                return false
            }
            buffer.clear()
            buffer.put(data)
            val flags = if (keyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            codec.queueInputBuffer(index, 0, data.size, ptsMs * 1000, flags)
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    private fun drainOutput() {
        val info = MediaCodec.BufferInfo()
        while (running) {
            try {
                val index = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    index >= 0 -> codec.releaseOutputBuffer(index, true)
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> reportSize(codec.outputFormat)
                }
            } catch (e: IllegalStateException) {
                return
            }
        }
    }

    // The visible picture, which is smaller than the coded one when the stream is cropped.
    private fun reportSize(format: MediaFormat) {
        var width = format.getInteger(MediaFormat.KEY_WIDTH)
        var height = format.getInteger(MediaFormat.KEY_HEIGHT)
        if (format.containsKey("crop-left") && format.containsKey("crop-right")) {
            width = format.getInteger("crop-right") - format.getInteger("crop-left") + 1
        }
        if (format.containsKey("crop-top") && format.containsKey("crop-bottom")) {
            height = format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1
        }
        onSize(width, height)
    }

    fun release() {
        running = false
        try {
            drain.join(500)
            codec.stop()
        } catch (e: Exception) {
            // Already stopped or never started: nothing left to release cleanly.
        }
        try {
            codec.release()
        } catch (e: Exception) {
            // Same.
        }
    }
}

package com.agustincantero.mxiangcameraviewer.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// Plays what the camera's microphone sends. The camera speaks G.711, raw PCM or Opus, mono, and the first three need no decoder at all.
class AudioOut private constructor(private val codec: Int, private val sampleRate: Int) {
    private val track: AudioTrack
    private val queue = LinkedBlockingQueue<ShortArray>(MAX_QUEUED)
    private var opus: MediaCodec? = null
    private val writer: Thread

    @Volatile
    private var running = true

    init {
        val outputRate = if (codec == CODEC_OPUS) OPUS_RATE else sampleRate
        val minimum = AudioTrack.getMinBufferSize(outputRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(outputRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(minimum * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()

        if (codec == CODEC_OPUS) {
            startOpus()
        }
        writer = thread(name = "audio-output", isDaemon = true) { writeLoop() }
    }

    fun push(data: ByteArray) {
        if (!running) return
        if (codec == CODEC_OPUS) {
            pushOpus(data)
            return
        }
        val samples = when (codec) {
            CODEC_PCMA -> ShortArray(data.size) { ALAW[data[it].toInt() and 0xFF] }
            CODEC_PCMU -> ShortArray(data.size) { ULAW[data[it].toInt() and 0xFF] }
            else -> ShortArray(data.size / 2) {
                ((data[it * 2].toInt() and 0xFF) or (data[it * 2 + 1].toInt() shl 8)).toShort()
            }
        }
        // Falling behind means a listener hears the past, so the oldest audio goes first.
        while (!queue.offer(samples)) {
            queue.poll()
        }
    }

    private fun writeLoop() {
        while (running) {
            val samples = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
            track.write(samples, 0, samples.size)
        }
    }

    private fun startOpus() {
        val head = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusHead".toByteArray())
            put(1)
            put(1) // mono
            putShort(0) // pre-skip
            putInt(sampleRate)
            putShort(0) // output gain
            put(0) // channel mapping family
        }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, OPUS_RATE, 1).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(head.array()))
            setByteBuffer("csd-1", nanos(0))
            setByteBuffer("csd-2", nanos(80_000_000L))
        }
        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        decoder.configure(format, null, null, 0)
        decoder.start()
        opus = decoder
        thread(name = "opus-output", isDaemon = true) {
            val info = MediaCodec.BufferInfo()
            while (running) {
                try {
                    val index = decoder.dequeueOutputBuffer(info, 10_000)
                    if (index >= 0) {
                        val buffer = decoder.getOutputBuffer(index)
                        if (buffer != null && info.size > 0) {
                            val pcm = ShortArray(info.size / 2)
                            buffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(pcm)
                            while (!queue.offer(pcm)) queue.poll()
                        }
                        decoder.releaseOutputBuffer(index, false)
                    }
                } catch (e: IllegalStateException) {
                    return@thread
                }
            }
        }
    }

    // The Opus decoder's codec-specific data, which wants the pre-skip and the seek pre-roll as nanosecond counts.
    private fun nanos(value: Long): ByteBuffer {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        buffer.putLong(value)
        buffer.flip()
        return buffer
    }

    private fun pushOpus(data: ByteArray) {
        val decoder = opus ?: return
        try {
            val index = decoder.dequeueInputBuffer(5_000)
            if (index < 0) return
            val buffer = decoder.getInputBuffer(index) ?: return
            buffer.clear()
            buffer.put(data)
            decoder.queueInputBuffer(index, 0, data.size, 0, 0)
        } catch (e: IllegalStateException) {
            // The decoder is being released.
        }
    }

    fun release() {
        running = false
        try {
            writer.join(300)
            track.stop()
        } catch (e: Exception) {
            // Nothing playing.
        }
        track.release()
        opus?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                // Already stopped.
            }
            it.release()
        }
    }

    companion object {
        const val CODEC_PCMA = 3
        const val CODEC_OPUS = 4
        const val CODEC_PCM = 5
        const val CODEC_PCMU = 6

        private const val OPUS_RATE = 48_000
        private const val MAX_QUEUED = 16

        // Null when this device cannot play the format, so the caller can say so.
        fun create(codec: Int, sampleRate: Int): AudioOut? {
            if (codec != CODEC_PCMA && codec != CODEC_PCMU && codec != CODEC_PCM && codec != CODEC_OPUS) {
                return null
            }
            // A camera that declares no rate is taken to mean the narrowband one G.711 is defined at.
            return try {
                AudioOut(codec, if (sampleRate > 0) sampleRate else 8000)
            } catch (e: Exception) {
                null
            }
        }

        // G.711 to 16-bit linear, from the ITU tables.
        private val ALAW = ShortArray(256) { value ->
            val a = value xor 0x55
            var t = (a and 0x0F) shl 4
            val segment = (a and 0x70) shr 4
            when (segment) {
                0 -> t += 8
                1 -> t += 0x108
                else -> {
                    t += 0x108
                    t = t shl (segment - 1)
                }
            }
            (if (a and 0x80 != 0) t else -t).toShort()
        }

        private val ULAW = ShortArray(256) { value ->
            val u = value.inv() and 0xFF
            var t = ((u and 0x0F) shl 3) + 0x84
            t = t shl ((u and 0x70) shr 4)
            (if (u and 0x80 != 0) 0x84 - t else t - 0x84).toShort()
        }
    }
}

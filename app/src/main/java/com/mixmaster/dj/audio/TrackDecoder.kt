package com.mixmaster.dj.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.nio.ByteOrder
import kotlin.math.abs

/** Fully decoded track: interleaved stereo 16-bit PCM. */
class DecodedTrack(val pcm: ShortArray, val sampleRate: Int, val title: String, val artist: String) {
    val frames: Int get() = pcm.size / 2
}

object TrackDecoder {
    const val MAX_SECONDS = 480

    fun decode(ctx: Context, uri: Uri): DecodedTrack {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(ctx, uri, null)
            var format: MediaFormat? = null
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    format = f; trackIndex = i; break
                }
            }
            if (format == null) throw IllegalArgumentException("This file has no audio track")
            extractor.selectTrack(trackIndex)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            var out = ShortArray(sampleRate * 2 * 20)
            var n = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val ii = c.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = c.getInputBuffer(ii)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(ii, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 10_000)
                if (oi >= 0) {
                    if (info.size > 0) {
                        val bb = c.getOutputBuffer(oi)!!
                        bb.position(info.offset)
                        bb.limit(info.offset + info.size)
                        val sb = bb.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val frames = sb.remaining() / channels
                        if (n + frames * 2 > out.size) out = out.copyOf(maxOf(out.size + out.size / 2, n + frames * 2))
                        for (f in 0 until frames) {
                            val l = sb.get()
                            val r = if (channels > 1) sb.get() else l
                            for (k in 2 until channels) sb.get()
                            out[n++] = l
                            out[n++] = r
                        }
                        if (n / 2 > sampleRate * MAX_SECONDS) {
                            throw IllegalStateException("Tracks longer than ${MAX_SECONDS / 60} minutes aren’t supported yet")
                        }
                    }
                    c.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = c.outputFormat
                    sampleRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (nf.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        nf.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT
                    ) throw IllegalStateException("This file’s audio format isn’t supported yet")
                }
            }
            if (n == 0) throw IllegalStateException("No audio could be decoded from this file")
            val (title, artist) = meta(ctx, uri)
            return DecodedTrack(out.copyOf(n), sampleRate, title, artist)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun meta(ctx: Context, uri: Uri): Pair<String, String> {
        var title: String? = null
        var artist: String? = null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        } catch (_: Exception) {
        } finally {
            r.release()
        }
        if (title.isNullOrBlank()) title = displayName(ctx, uri)
        return Pair(title ?: "Unknown track", artist?.takeIf { it.isNotBlank() } ?: "Unknown artist")
    }

    private fun displayName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.substringBeforeLast('.') else null
        }
    } catch (_: Exception) {
        null
    }
}

object Waveform {
    /** Peak overview with [bins] columns, values 0..1. */
    fun peaks(pcm: ShortArray, bins: Int): FloatArray {
        val frames = pcm.size / 2
        val out = FloatArray(bins)
        if (frames == 0) return out
        for (b in 0 until bins) {
            val s = (b.toLong() * frames / bins).toInt()
            val e = ((b + 1).toLong() * frames / bins).toInt().coerceAtLeast(s + 1).coerceAtMost(frames)
            val step = maxOf(1, (e - s) / 64)
            var m = 0
            var i = s
            while (i < e) {
                val v = abs(pcm[i * 2].toInt())
                if (v > m) m = v
                i += step
            }
            out[b] = m / 32768f
        }
        return out
    }
}

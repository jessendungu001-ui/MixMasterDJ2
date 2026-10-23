package com.mixmaster.dj.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

/** Mixes the decks on a dedicated high-priority thread and writes float PCM to one AudioTrack. */
class AudioEngine(ctx: Context) {
    val decks = arrayOf(Deck(), Deck())
    @Volatile var masterGain: Float = 0.8f
    @Volatile var level: Float = 0f
        private set
    @Volatile var lastClipMs: Long = 0L
        private set

    @Volatile private var running = false
    @Volatile private var duck = 1f
    private var thread: Thread? = null
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val outRate: Int = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000

    init { applyCrossfader(0.5f) }

    /** 0 = deck A only, 1 = deck B only, equal-power curve. */
    fun applyCrossfader(x: Float) {
        val g = Crossfader.gains(x)
        decks[0].xfade = g.first
        decks[1].xfade = g.second
    }

    private var focusReq: AudioFocusRequest? = null
    private val resumeAfter = BooleanArray(decks.size)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { decks.forEach { it.pause() }; abandonFocus() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                decks.forEachIndexed { i, d -> resumeAfter[i] = d.playing; d.pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> duck = 0.3f
            AudioManager.AUDIOFOCUS_GAIN -> {
                duck = 1f
                decks.forEachIndexed { i, d -> if (resumeAfter[i]) d.resume(); resumeAfter[i] = false }
            }
        }
    }

    fun togglePlay(i: Int) {
        val d = decks[i]
        if (!d.playing) { requestFocus(); start() }
        d.togglePlay()
    }

    fun start() {
        if (thread?.isAlive == true) return
        running = true
        thread = Thread({ loop() }, "mixmaster-audio").also { it.start() }
    }

    fun release() {
        running = false
        thread?.join(500)
        thread = null
        abandonFocus()
    }

    private fun requestFocus() {
        if (focusReq != null) return
        val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        if (am.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusReq = r
    }

    private fun abandonFocus() {
        focusReq?.let { am.abandonAudioFocusRequest(it) }
        focusReq = null
    }

    private fun buildTrack(chunk: Int): AudioTrack? = try {
        val min = AudioTrack.getMinBufferSize(outRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(outRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(max(min, chunk * 2 * 4 * 4))
            .build()
    } catch (e: Exception) {
        null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val chunk = 256
        val track = buildTrack(chunk)
        if (track == null) { running = false; return }
        val buf = FloatArray(chunk * 2)
        var idle = 0
        var started = false
        try {
            while (running) {
                if (decks.any { it.playing }) idle = 0 else idle++
                if (idle > outRate / chunk) {          // about 1 s of silence: stop the output to save battery
                    if (started) { track.pause(); track.flush(); started = false }
                    Thread.sleep(15)
                    continue
                }
                if (!started) { track.play(); started = true }
                java.util.Arrays.fill(buf, 0f)
                for (d in decks) d.renderInto(buf, chunk, outRate)
                var peak = 0f
                val mg = masterGain * duck
                for (i in buf.indices) {
                    var x = buf[i] * mg
                    val a = abs(x)
                    if (a > peak) peak = a
                    if (a > 0.8f) x = sign(x) * (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f))   // soft limiter, max 1.0
                    buf[i] = x
                }
                level = max(peak, level * 0.92f)
                if (peak > 1f) lastClipMs = SystemClock.elapsedRealtime()
                if (track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING) < 0) break
            }
        } catch (e: Exception) {
            // output failed: the next play press restarts the engine
        } finally {
            running = false
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }
}

object Crossfader {
    fun gains(x: Float): Pair<Float, Float> {
        val p = x.coerceIn(0f, 1f) * (PI / 2).toFloat()
        return Pair(cos(p).coerceAtLeast(0f), sin(p).coerceAtLeast(0f))
    }
}

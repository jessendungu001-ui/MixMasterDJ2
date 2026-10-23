package com.mixmaster.dj.audio

import java.util.concurrent.atomic.AtomicLong

/**
 * One deck. UI thread sets parameters; the audio thread calls [renderInto].
 * No allocation or locking happens in [renderInto].
 */
class Deck {
    private class Track(val pcm: ShortArray, val frames: Int, val rate: Int)

    @Volatile private var track: Track? = null
    @Volatile var playing: Boolean = false
        private set
    @Volatile var gain: Float = 1f
    @Volatile var volume: Float = 1f
    @Volatile var tempo: Float = 1f
    @Volatile var positionFrames: Double = 0.0
        private set
    @Volatile var cueFrame: Double = 0.0
        private set

    private val none = Double.NaN.toRawBits()
    private val seekReq = AtomicLong(none)
    private var level = 0f        // play/pause fade (audio thread only)
    private var smoothGain = 0f   // gain smoothing (audio thread only)

    val loaded: Boolean get() = track != null
    val positionSec: Double get() = track?.let { positionFrames / it.rate } ?: 0.0
    val durationSec: Double get() = track?.let { it.frames.toDouble() / it.rate } ?: 0.0

    fun load(pcm: ShortArray, rate: Int) {
        playing = false
        cueFrame = 0.0
        positionFrames = 0.0
        seekReq.set(0.0.toRawBits())
        track = Track(pcm, pcm.size / 2, rate)
    }

    fun unload() {
        playing = false
        track = null
        positionFrames = 0.0
        cueFrame = 0.0
    }

    fun togglePlay() {
        val t = track ?: return
        if (playing) {
            playing = false
        } else {
            if (positionFrames >= t.frames - 2) seekFrames(0.0)
            playing = true
        }
    }

    fun pause() { playing = false }

    fun resume() { if (track != null) playing = true }

    fun seekSec(sec: Double) {
        val t = track ?: return
        seekFrames(sec * t.rate)
    }

    private fun seekFrames(f: Double) {
        val t = track ?: return
        val v = f.coerceIn(0.0, (t.frames - 2).toDouble())
        seekReq.set(v.toRawBits())
        positionFrames = v
    }

    /** CDJ-style cue: while playing, return to the cue point and pause; while paused, set the cue point here. */
    fun cue() {
        if (track == null) return
        if (playing) {
            playing = false
            seekFrames(cueFrame)
        } else {
            cueFrame = positionFrames
        }
    }

    /** Adds this deck's audio into [out] (interleaved stereo float). Audio thread only. */
    fun renderInto(out: FloatArray, frames: Int, outRate: Int) {
        val t = track ?: return
        var running = playing
        if (!running && level < 0.0005f) { level = 0f; return }
        val bits = seekReq.getAndSet(none)
        var pos = if (bits != none) Double.fromBits(bits) else positionFrames
        val step = t.rate.toDouble() / outRate * tempo.coerceIn(0.5f, 1.5f)
        val target = gain * volume
        val last = t.frames - 2
        val pcm = t.pcm
        var lv = level
        var sg = smoothGain
        for (i in 0 until frames) {
            if (running && pos >= last) { pos = last.toDouble(); running = false; playing = false }
            lv += ((if (running) 1f else 0f) - lv) * 0.01f
            sg += (target - sg) * 0.002f
            val i0 = pos.toInt()
            val f = (pos - i0).toFloat()
            val a = i0 * 2
            val l = (pcm[a] + (pcm[a + 2] - pcm[a]) * f) * (1f / 32768f)
            val r = (pcm[a + 1] + (pcm[a + 3] - pcm[a + 1]) * f) * (1f / 32768f)
            val g = lv * sg
            out[i * 2] += l * g
            out[i * 2 + 1] += r * g
            if (running) pos += step
        }
        positionFrames = pos
        level = lv
        smoothGain = sg
    }
}

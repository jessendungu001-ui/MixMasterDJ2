package com.mixmaster.dj.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckTest {
    private fun constant(frames: Int, v: Short) = ShortArray(frames * 2) { v }

    @Test fun positionAdvancesOneFramePerOutputFrameAtUnityTempo() {
        val d = Deck(); d.load(constant(48000, 1000), 48000); d.togglePlay()
        val buf = FloatArray(480 * 2)
        repeat(10) { d.renderInto(buf, 480, 48000) }
        assertEquals(4800.0, d.positionFrames, 0.001)
    }

    @Test fun outputReachesGainTimesSampleValue() {
        val d = Deck(); d.load(constant(48000, 1000), 48000); d.togglePlay()
        val buf = FloatArray(256 * 2)
        repeat(40) { java.util.Arrays.fill(buf, 0f); d.renderInto(buf, 256, 48000) }
        assertEquals(1000f / 32768f, buf[buf.size - 1], 0.002f)
    }

    @Test fun volumeZeroIsSilent() {
        val d = Deck(); d.load(constant(48000, 1000), 48000); d.volume = 0f; d.togglePlay()
        val buf = FloatArray(256 * 2)
        repeat(40) { java.util.Arrays.fill(buf, 0f); d.renderInto(buf, 256, 48000) }
        assertEquals(0f, buf[buf.size - 1], 0.0001f)
    }

    @Test fun playbackStopsAtEndOfTrack() {
        val d = Deck(); d.load(constant(100, 1000), 48000); d.togglePlay()
        val buf = FloatArray(400 * 2)
        d.renderInto(buf, 400, 48000)
        assertFalse(d.playing)
    }

    @Test fun cueSetsPointWhilePausedAndReturnsWhilePlaying() {
        val d = Deck(); d.load(constant(48000, 1000), 48000)
        d.seekSec(0.5); d.cue()
        assertEquals(24000.0, d.cueFrame, 0.001)
        d.togglePlay(); d.seekSec(0.9); d.cue()
        assertFalse(d.playing)
        assertEquals(24000.0, d.positionFrames, 0.001)
    }

    @Test fun waveformPeaksOfConstantSignal() {
        val p = Waveform.peaks(constant(48000, 16384), 100)
        assertTrue(p.all { kotlin.math.abs(it - 0.5f) < 0.001f })
    }

    private fun sine(freq: Double, frames: Int): ShortArray {
        val p = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val v = (kotlin.math.sin(2 * Math.PI * freq * i / 48000.0) * 0.5 * 32767).toInt().toShort()
            p[i * 2] = v; p[i * 2 + 1] = v
        }
        return p
    }

    /** RMS of the settled second half of 150 blocks. */
    private fun rms(d: Deck): Double {
        val buf = FloatArray(256 * 2)
        var sum = 0.0
        var n = 0
        repeat(150) { b ->
            java.util.Arrays.fill(buf, 0f)
            d.renderInto(buf, 256, 48000)
            if (b >= 75) for (k in buf.indices) { sum += buf[k] * buf[k]; n++ }
        }
        return Math.sqrt(sum / n)
    }

    private fun played(freq: Double, setup: (Deck) -> Unit = {}): Double {
        val d = Deck(); d.load(sine(freq, 48000), 48000); setup(d); d.togglePlay()
        return rms(d)
    }

    @Test fun flatEqPassesSignalUnchanged() {
        assertEquals(0.5 / Math.sqrt(2.0), played(1000.0), 0.01)
    }

    @Test fun lowKillRemovesBass() {
        assertTrue(played(60.0) { it.lowDb = -40f } < played(60.0) * 0.1)
    }

    @Test fun midKillRemoves1kHz() {
        assertTrue(played(1000.0) { it.midDb = -40f } < played(1000.0) * 0.1)
    }

    @Test fun highKillRemoves10kHz() {
        assertTrue(played(10000.0) { it.highDb = -40f } < played(10000.0) * 0.1)
    }

    @Test fun lowKillLeavesHighsAlone() {
        assertEquals(played(10000.0), played(10000.0) { it.lowDb = -40f }, 0.02)
    }

    @Test fun crossfaderIsEqualPower() {
        val a = Crossfader.gains(0f); val b = Crossfader.gains(1f); val m = Crossfader.gains(0.5f)
        assertEquals(1f, a.first, 0.0001f); assertEquals(0f, a.second, 0.0001f)
        assertEquals(0f, b.first, 0.0001f); assertEquals(1f, b.second, 0.0001f)
        assertEquals(1.0, (m.first * m.first + m.second * m.second).toDouble(), 0.001)
    }

    @Test fun crossfaderZeroSilencesDeckB() {
        val d = Deck(); d.load(constant(48000, 1000), 48000); d.xfade = 0f; d.togglePlay()
        val buf = FloatArray(256 * 2)
        repeat(40) { java.util.Arrays.fill(buf, 0f); d.renderInto(buf, 256, 48000) }
        assertEquals(0f, buf[buf.size - 1], 0.0001f)
    }
}

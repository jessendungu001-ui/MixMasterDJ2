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
}

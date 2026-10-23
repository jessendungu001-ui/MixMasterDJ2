package com.mixmaster.dj.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One stereo biquad filter (RBJ cookbook coefficients, transposed direct form II). */
class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1l = 0.0
    private var z2l = 0.0
    private var z1r = 0.0
    private var z2r = 0.0

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
    }

    fun lowShelf(fs: Double, f0: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w = 2 * PI * f0 / fs
        val c = cos(w)
        val k = 2 * sqrt(a) * (sin(w) / sqrt(2.0))
        set(
            a * ((a + 1) - (a - 1) * c + k), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - k),
            (a + 1) + (a - 1) * c + k, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - k,
        )
    }

    fun highShelf(fs: Double, f0: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w = 2 * PI * f0 / fs
        val c = cos(w)
        val k = 2 * sqrt(a) * (sin(w) / sqrt(2.0))
        set(
            a * ((a + 1) + (a - 1) * c + k), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - k),
            (a + 1) - (a - 1) * c + k, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - k,
        )
    }

    fun peak(fs: Double, f0: Double, db: Double, q: Double) {
        val a = 10.0.pow(db / 40.0)
        val w = 2 * PI * f0 / fs
        val c = cos(w)
        val al = sin(w) / (2 * q)
        set(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
    }

    fun l(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + z1l
        z1l = b1 * xd - a1 * y + z2l
        z2l = b2 * xd - a2 * y
        return y.toFloat()
    }

    fun r(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + z1r
        z1r = b1 * xd - a1 * y + z2r
        z2r = b2 * xd - a2 * y
        return y.toFloat()
    }

    fun reset() { z1l = 0.0; z2l = 0.0; z1r = 0.0; z2r = 0.0 }
}

/** Low shelf 250 Hz, mid peak 1 kHz, high shelf 3.5 kHz. Gains in dB; -40 dB acts as a kill. */
class ThreeBandEq {
    private val low = Biquad()
    private val mid = Biquad()
    private val high = Biquad()
    var outL: Float = 0f
        private set
    var outR: Float = 0f
        private set

    fun update(fs: Double, lowDb: Float, midDb: Float, highDb: Float) {
        low.lowShelf(fs, 250.0, lowDb.toDouble())
        mid.peak(fs, 1000.0, midDb.toDouble(), 0.7)
        high.highShelf(fs, 3500.0, highDb.toDouble())
    }

    fun tick(l: Float, r: Float) {
        outL = high.l(mid.l(low.l(l)))
        outR = high.r(mid.r(low.r(r)))
    }

    fun reset() { low.reset(); mid.reset(); high.reset() }
}

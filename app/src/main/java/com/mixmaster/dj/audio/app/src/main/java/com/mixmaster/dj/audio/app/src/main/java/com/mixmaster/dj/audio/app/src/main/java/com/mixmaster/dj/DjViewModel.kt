package com.mixmaster.dj

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mixmaster.dj.audio.AudioEngine
import com.mixmaster.dj.audio.Deck
import com.mixmaster.dj.audio.TrackDecoder
import com.mixmaster.dj.audio.Waveform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class DeckUi(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val title: String = "No track loaded",
    val artist: String = "",
    val durationSec: Double = 0.0,
    val peaks: FloatArray = FloatArray(0),
    val error: String? = null,
)

/** EQ knob -1..1 to dB: -1 is a -40 dB kill, +1 is +6 dB. */
fun eqDb(v: Float): Float = if (v < 0f) v * 40f else v * 6f

private fun snap(v: Float): Float = if (abs(v) < 0.04f) 0f else v

class DeckState(val deck: Deck) {
    var ui by mutableStateOf(DeckUi())
    var gain by mutableFloatStateOf(1f)
        private set
    var volume by mutableFloatStateOf(1f)
        private set
    var tempo by mutableFloatStateOf(1f)
        private set
    var low by mutableFloatStateOf(0f)
        private set
    var mid by mutableFloatStateOf(0f)
        private set
    var high by mutableFloatStateOf(0f)
        private set

    fun onGain(v: Float) { gain = v; deck.gain = v }
    fun onVolume(v: Float) { volume = v; deck.volume = v }
    fun onTempo(v: Float) { tempo = v; deck.tempo = v }
    fun onLow(v: Float) { low = snap(v); deck.lowDb = eqDb(low) }
    fun onMid(v: Float) { mid = snap(v); deck.midDb = eqDb(mid) }
    fun onHigh(v: Float) { high = snap(v); deck.highDb = eqDb(high) }
}

class DjViewModel(app: Application) : AndroidViewModel(app) {
    val engine = AudioEngine(app)
    val decks = listOf(DeckState(engine.decks[0]), DeckState(engine.decks[1]))

    var master by mutableFloatStateOf(0.8f)
        private set
    var crossfader by mutableFloatStateOf(0.5f)
        private set

    private val jobs = arrayOfNulls<Job>(2)

    fun load(i: Int, uri: Uri) {
        val s = decks[i]
        jobs[i]?.cancel()
        s.deck.unload()
        s.ui = DeckUi(loading = true, title = "Loading…")
        jobs[i] = viewModelScope.launch {
            try {
                val t = withContext(Dispatchers.Default) { TrackDecoder.decode(getApplication<Application>(), uri) }
                val peaks = withContext(Dispatchers.Default) { Waveform.peaks(t.pcm, 1200) }
                s.deck.load(t.pcm, t.sampleRate)
                s.ui = DeckUi(
                    loaded = true, title = t.title, artist = t.artist,
                    durationSec = t.frames.toDouble() / t.sampleRate, peaks = peaks,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                s.ui = DeckUi(error = "Not enough memory for this track. Try a shorter one.")
            } catch (e: Exception) {
                s.ui = DeckUi(error = e.message ?: "Couldn’t open this file")
            }
        }
    }

    fun togglePlay(i: Int) = engine.togglePlay(i)
    fun cue(i: Int) = decks[i].deck.cue()
    fun seek(i: Int, fraction: Float) = decks[i].deck.seekSec(fraction * decks[i].ui.durationSec)
    fun pauseAll() = engine.decks.forEach { it.pause() }

    fun onMaster(v: Float) { master = v; engine.masterGain = v }
    fun onCrossfader(v: Float) {
        crossfader = if (abs(v - 0.5f) < 0.02f) 0.5f else v
        engine.applyCrossfader(crossfader)
    }

    override fun onCleared() {
        jobs.forEach { it?.cancel() }
        engine.release()
    }
}

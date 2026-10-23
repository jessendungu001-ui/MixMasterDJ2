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
import com.mixmaster.dj.audio.TrackDecoder
import com.mixmaster.dj.audio.Waveform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeckUi(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val title: String = "No track loaded",
    val artist: String = "",
    val durationSec: Double = 0.0,
    val peaks: FloatArray = FloatArray(0),
    val error: String? = null,
)

class DjViewModel(app: Application) : AndroidViewModel(app) {
    val engine = AudioEngine(app)

    var deck by mutableStateOf(DeckUi())
        private set
    var gain by mutableFloatStateOf(1f)
        private set
    var volume by mutableFloatStateOf(1f)
        private set
    var tempo by mutableFloatStateOf(1f)
        private set
    var master by mutableFloatStateOf(0.8f)
        private set

    private var loadJob: Job? = null

    fun load(uri: Uri) {
        loadJob?.cancel()
        engine.decks[0].unload()
        deck = DeckUi(loading = true, title = "Loading…")
        loadJob = viewModelScope.launch {
            try {
                val t = withContext(Dispatchers.Default) { TrackDecoder.decode(getApplication<Application>(), uri) }
                val peaks = withContext(Dispatchers.Default) { Waveform.peaks(t.pcm, 1200) }
                engine.decks[0].load(t.pcm, t.sampleRate)
                deck = DeckUi(
                    loaded = true, title = t.title, artist = t.artist,
                    durationSec = t.frames.toDouble() / t.sampleRate, peaks = peaks,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                deck = DeckUi(error = "Not enough memory for this track. Try a shorter one.")
            } catch (e: Exception) {
                deck = DeckUi(error = e.message ?: "Couldn’t open this file")
            }
        }
    }

    fun togglePlay() = engine.togglePlay(0)
    fun cue() = engine.decks[0].cue()
    fun seek(fraction: Float) = engine.decks[0].seekSec(fraction * deck.durationSec)
    fun pauseAll() = engine.decks.forEach { it.pause() }

    fun setGain(v: Float) { gain = v; engine.decks[0].gain = v }
    fun setVolume(v: Float) { volume = v; engine.decks[0].volume = v }
    fun setTempo(v: Float) { tempo = v; engine.decks[0].tempo = v }
    fun setMaster(v: Float) { master = v; engine.masterGain = v }

    override fun onCleared() {
        loadJob?.cancel()
        engine.release()
    }
}

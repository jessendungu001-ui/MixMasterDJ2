package com.mixmaster.dj

import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val Bg = Color(0xFF0E1218)
private val Panel = Color(0xFF171D26)
private val Well = Color(0xFF0B0F14)
private val Mint = Color(0xFF35E0B5)
private val Blue = Color(0xFF7C9CFF)
private val Amber = Color(0xFFFFC857)
private val Muted = Color(0xFF8D9BAD)

@Composable
fun MixMasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint, onPrimary = Color(0xFF04201A),
            background = Bg, onBackground = Color(0xFFE8EEF5),
            surface = Panel, onSurface = Color(0xFFE8EEF5),
            secondaryContainer = Color(0xFF263241), onSecondaryContainer = Color(0xFFE8EEF5),
        ),
        content = content,
    )
}

private fun fmt(sec: Double): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

class DeckLive {
    var pos by mutableDoubleStateOf(0.0)
    var playing by mutableStateOf(false)
    var level by mutableFloatStateOf(0f)
}

@Composable
fun DjScreen(vm: DjViewModel) {
    val ctx = LocalContext.current
    var target by rememberSaveable { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            vm.load(target, uri)
        }
    }
    val live = remember { listOf(DeckLive(), DeckLive()) }
    var level by remember { mutableFloatStateOf(0f) }
    var clip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            for (i in 0..1) {
                val d = vm.engine.decks[i]
                live[i].pos = d.positionSec
                live[i].playing = d.playing
                live[i].level = d.peak
            }
            level = vm.engine.level
            clip = SystemClock.elapsedRealtime() - vm.engine.lastClipMs < 600
        }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val onLoad: (Int) -> Unit = { target = it; picker.launch(arrayOf("audio/*")) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header(level, clip, vm.master) { vm.onMaster(it) }
            if (landscape) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeckStrip(vm, 0, live[0], onLoad, Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
                    Mixer(vm, Modifier.weight(1.3f).fillMaxHeight().verticalScroll(rememberScrollState()))
                    DeckStrip(vm, 1, live[1], onLoad, Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DeckStrip(vm, 0, live[0], onLoad, Modifier.fillMaxWidth())
                    Mixer(vm, Modifier.fillMaxWidth())
                    DeckStrip(vm, 1, live[1], onLoad, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun Header(level: Float, clip: Boolean, master: Float, onMaster: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.app_name),
                fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(12.dp).background(if (clip) Color(0xFFFF4D4D) else Color(0xFF3A2020), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text("Clip", color = Muted, fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { level.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp),
            color = if (level > 0.8f) Amber else Mint,
            trackColor = Well,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Master", color = Muted)
            Slider(value = master, onValueChange = onMaster, valueRange = 0f..1f, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun DeckStrip(vm: DjViewModel, i: Int, live: DeckLive, onLoad: (Int) -> Unit, modifier: Modifier) {
    val ui = vm.decks[i].ui
    val color = if (i == 0) Mint else Blue
    Column(
        modifier.background(Panel, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (i == 0) "A" else "B", color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (ui.artist.isNotEmpty()) Text(ui.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = { onLoad(i) }, enabled = !ui.loading) { Text("Load") }
        }
        ui.error?.let { Text(it, color = Color(0xFFFF7A7A)) }
        val progress = if (ui.durationSec > 0) (live.pos / ui.durationSec).toFloat().coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(84.dp).background(Well, RoundedCornerShape(8.dp))) {
            if (ui.loaded) {
                WaveformView(ui.peaks, progress, color, { vm.seek(i, it) }, Modifier.fillMaxSize())
            } else {
                Text(
                    if (ui.loading) "Decoding…" else "Load a track from your device",
                    color = Muted, modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(live.pos), color = Muted)
            Text("-" + fmt(ui.durationSec - live.pos), color = Muted)
        }
        LinearProgressIndicator(
            progress = { live.level.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color, trackColor = Well,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = { vm.cue(i) }, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(60.dp),
            ) { Text("CUE", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = { vm.togglePlay(i) }, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF06131F)),
                modifier = Modifier.weight(1f).height(60.dp),
            ) { Text(if (live.playing) "PAUSE" else "PLAY", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun WaveformView(peaks: FloatArray, progress: Float, color: Color, onSeek: (Float) -> Unit, modifier: Modifier) {
    Canvas(
        modifier
            .pointerInput(peaks) { detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(peaks) {
                detectHorizontalDragGestures { change, _ -> onSeek((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        val n = peaks.size
        if (n == 0) return@Canvas
        val w = size.width
        val h = size.height
        val bw = w / n
        for (k in 0 until n) {
            val ph = max(2f, peaks[k] * h * 0.95f)
            val x = k * bw + bw / 2
            drawLine(
                if (k.toFloat() / n < progress) color else Color(0xFF3A4654),
                Offset(x, (h - ph) / 2), Offset(x, (h + ph) / 2), strokeWidth = max(1f, bw),
            )
        }
        drawLine(Color.White, Offset(w * progress, 0f), Offset(w * progress, h), 2f)
    }
}

@Composable
fun Mixer(vm: DjViewModel, modifier: Modifier) {
    Column(
        modifier.background(Panel, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("A", color = Mint, fontWeight = FontWeight.Bold)
            Text("Crossfader", color = Muted)
            Text("B", color = Blue, fontWeight = FontWeight.Bold)
        }
        Slider(value = vm.crossfader, onValueChange = { vm.onCrossfader(it) }, valueRange = 0f..1f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Channel(vm.decks[0], Mint, "A", Modifier.weight(1f))
            Channel(vm.decks[1], Blue, "B", Modifier.weight(1f))
        }
    }
}

private fun dbText(v: Float): String =
    if (v <= -0.98f) "Kill" else String.format(Locale.US, "%+.0f dB", eqDb(v))

@Composable
fun Channel(s: DeckState, color: Color, name: String, modifier: Modifier) {
    Column(modifier) {
        Text("Channel $name", color = color, fontWeight = FontWeight.SemiBold)
        MiniSlider("Gain", s.gain, 0f..2f, "${(s.gain * 100).roundToInt()}%", color) { s.onGain(it) }
        MiniSlider("High", s.high, -1f..1f, dbText(s.high), color) { s.onHigh(it) }
        MiniSlider("Mid", s.mid, -1f..1f, dbText(s.mid), color) { s.onMid(it) }
        MiniSlider("Low", s.low, -1f..1f, dbText(s.low), color) { s.onLow(it) }
        MiniSlider("Volume", s.volume, 0f..1f, "${(s.volume * 100).roundToInt()}%", color) { s.onVolume(it) }
        MiniSlider(
            "Tempo", s.tempo, 0.92f..1.08f,
            String.format(Locale.US, "%+.1f%%", (s.tempo - 1f) * 100f), color,
        ) { s.onTempo(it) }
        TextButton(onClick = { s.onTempo(1f) }) { Text("Reset tempo") }
    }
}

@Composable
fun MiniSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    text: String, color: Color, onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Muted, fontSize = 13.sp)
            Text(text, fontSize = 13.sp)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
        )
    }
}

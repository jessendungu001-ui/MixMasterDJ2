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

@Composable
fun DjScreen(vm: DjViewModel) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            vm.load(uri)
        }
    }
    var pos by remember { mutableDoubleStateOf(0.0) }
    var level by remember { mutableFloatStateOf(0f) }
    var clip by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val d = vm.engine.decks[0]
            pos = d.positionSec
            playing = d.playing
            level = vm.engine.level
            clip = SystemClock.elapsedRealtime() - vm.engine.lastClipMs < 600
        }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val load = { picker.launch(arrayOf("audio/*")) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header(level, clip, vm.master) { vm.setMaster(it) }
            if (landscape) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeckPanel(vm.deck, pos, { vm.seek(it) }, load, Modifier.weight(1f).fillMaxHeight())
                    Controls(vm, playing, Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
                }
            } else {
                DeckPanel(vm.deck, pos, { vm.seek(it) }, load, Modifier.fillMaxWidth())
                Controls(vm, playing, Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()))
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
fun DeckPanel(ui: DeckUi, pos: Double, onSeek: (Float) -> Unit, onLoad: () -> Unit, modifier: Modifier) {
    Column(
        modifier.background(Panel, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", color = Mint, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                if (ui.artist.isNotEmpty()) Text(ui.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onLoad, enabled = !ui.loading) { Text("Load") }
        }
        ui.error?.let { Text(it, color = Color(0xFFFF7A7A)) }
        val progress = if (ui.durationSec > 0) (pos / ui.durationSec).toFloat().coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(110.dp).background(Well, RoundedCornerShape(8.dp))) {
            if (ui.loaded) {
                WaveformView(ui.peaks, progress, onSeek, Modifier.fillMaxSize())
            } else {
                Text(
                    if (ui.loading) "Decoding…" else "Load a track from your device",
                    color = Muted, modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), color = Muted)
            Text("-" + fmt(ui.durationSec - pos), color = Muted)
        }
    }
}

@Composable
fun WaveformView(peaks: FloatArray, progress: Float, onSeek: (Float) -> Unit, modifier: Modifier) {
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
        for (i in 0 until n) {
            val ph = max(2f, peaks[i] * h * 0.95f)
            val x = i * bw + bw / 2
            drawLine(
                if (i.toFloat() / n < progress) Mint else Color(0xFF3A4654),
                Offset(x, (h - ph) / 2), Offset(x, (h + ph) / 2), strokeWidth = max(1f, bw),
            )
        }
        drawLine(Color.White, Offset(w * progress, 0f), Offset(w * progress, h), 2f)
    }
}

@Composable
fun Controls(vm: DjViewModel, playing: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = { vm.cue() }, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(76.dp),
            ) { Text("CUE", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = { vm.togglePlay() }, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(76.dp),
            ) { Text(if (playing) "PAUSE" else "PLAY", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
        LabeledSlider("Gain", vm.gain, 0f..2f, "${(vm.gain * 100).roundToInt()}%") { vm.setGain(it) }
        LabeledSlider("Volume", vm.volume, 0f..1f, "${(vm.volume * 100).roundToInt()}%") { vm.setVolume(it) }
        LabeledSlider(
            "Tempo", vm.tempo, 0.92f..1.08f,
            String.format(Locale.US, "%+.1f%%", (vm.tempo - 1f) * 100f),
        ) { vm.setTempo(it) }
        OutlinedButton(onClick = { vm.setTempo(1f) }) { Text("Reset tempo") }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, text: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Muted)
            Text(text)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

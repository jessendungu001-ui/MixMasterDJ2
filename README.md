# MixMaster DJ (Milestones 1 and 2)

Rename: change `app_name` in `app/src/main/res/values/strings.xml`. To change the package, edit `namespace` and `applicationId` in `app/build.gradle.kts` and move the `com/mixmaster/dj` folders.

## Architecture (Milestone 1)
- Kotlin + Jetpack Compose UI, one `ViewModel`, no extra architecture layers.
- Decoding: Android `MediaExtractor` + `MediaCodec` (mp3, aac/m4a, flac, ogg, wav). Each track is decoded once into RAM as stereo 16-bit PCM, which gives instant seek, loops, waveforms and tempo changes later. Trade-off: about 10 MB per minute of audio per deck, so tracks over 8 minutes are refused for now. `largeHeap` is on. A later milestone can move to streamed decoding.
- Output: one `AudioTrack` (float PCM, low-latency mode) fed by a single high-priority mixer thread that sums all decks, applies master gain and a soft limiter. Render code does no allocation, locking or I/O. Oboe/C++ is not used: it adds NDK complexity and should only be added if measured latency needs it.
- Resampling: decks read with linear interpolation at `trackRate / outputRate * tempo`, so different sample rates mix correctly. Tempo in M2 is varispeed (pitch changes with tempo). Key-locked time-stretching comes in Milestone 5.
- Audio focus: pauses on focus loss, ducks on "can duck", resumes after a transient loss.
- Permissions: none. Files come from the system picker. The app has no internet permission.

## Licences
All dependencies (Kotlin, AndroidX, Jetpack Compose, kotlinx.coroutines) are Apache 2.0. Decoding uses the Android platform codecs. JUnit (tests only) is EPL 1.0. No audio is bundled.

## Files
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`: project root
- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/{strings,themes}.xml`
- `app/src/main/java/com/mixmaster/dj/`: `MainActivity.kt`, `DjScreen.kt`, `DjViewModel.kt`
- `app/src/main/java/com/mixmaster/dj/audio/`: `TrackDecoder.kt`, `Deck.kt`, `AudioEngine.kt`
- `app/src/test/java/com/mixmaster/dj/audio/DeckTest.kt`
- `.github/workflows/build.yml`: builds the APK on GitHub for free

## Build from an Android phone (no PC)
1. Install Termux from F-Droid. Download `MixMasterDJ.zip` to Downloads.
2. In Termux:
   `pkg install git unzip`, then `termux-setup-storage`
   `cd ~ && unzip ~/storage/downloads/MixMasterDJ.zip && cd MixMasterDJ`
   `git init -b main && git add . && git commit -m "M2"`
   `git remote add origin https://github.com/YOURNAME/MixMasterDJ.git`
   `git push -u origin main` (use a GitHub personal access token as the password)
3. On github.com open the repo, Actions, "Build debug APK". When it finishes, open the run and download the artifact. Unzip it and install `app-debug.apk` (allow "install unknown apps").
4. If the build fails, open the failed step and send me the error text.

## Manual test checklist (Milestone 2)
1. Load an mp3, an m4a, a flac and a wav. Each shows title, artist and a waveform.
2. Play and pause: no clicks. Elapsed and remaining time match the track.
3. Tap and drag the waveform: playback jumps to that point.
4. Cue while paused sets the cue point. Cue while playing returns to it and pauses.
5. Gain and Volume change loudness smoothly. Tempo changes speed and pitch, Reset tempo restores it.
6. With a loud track, Gain 200% and Master 100%: the Clip light blinks and the sound does not hard-distort.
7. Rotate the phone while playing: audio continues, UI keeps state.
8. Press Home: playback pauses. Reopen: the track is still loaded.
9. Start a call or another app's audio: playback pauses, and resumes after a short interruption.
10. Load a second track while playing, then repeat 10 times: no crash, memory stays stable.
11. Load a non-audio file or a track over 8 minutes: a clear message appears.
12. Unit tests: the GitHub run's "Unit tests" step passes (6 tests).

## Known limitations
Single deck in the UI (the engine already mixes two). No effects, loops, hot cues, BPM, library or recording yet. Playback stops when the app is in the background.

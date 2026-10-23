package com.mixmaster.dj

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: DjViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MixMasterTheme { DjScreen(vm) } }
    }

    // Milestone 2 has no background service yet, so playback pauses when the app leaves the screen.
    override fun onStop() {
        super.onStop()
        vm.pauseAll()
    }
}

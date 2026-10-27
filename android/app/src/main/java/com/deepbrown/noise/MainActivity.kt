package com.deepbrown.noise

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private var service: NoiseService? = null
    private val ui = mutableStateOf(UiState())
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as? NoiseService.LocalBinder)?.service ?: return
            service = s
            s.listener = { ui.value = it }
            ui.value = s.snapshot()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.listener = null
            service = null
            ui.value = ui.value.copy(playing = false)
        }

        override fun onBindingDied(name: ComponentName?) {
            onServiceDisconnected(name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            DeepBrownTheme {
                RequestNotificationPermission()
                val state = ui.value
                DeepBrownScreen(
                    state = state,
                    onToggle = { if (state.playing) pausePlayback() else startPlayback() },
                    onDeep = { value -> update(state.params.copy(deep = value)) },
                    onPreset = { preset -> update(preset.applyTo(state.params)) },
                    onPrevPreset = { stepPreset(-1) },
                    onNextPreset = { stepPreset(1) },
                    onParam = { key, value -> update(state.params.with(key, value)) },
                    onTimer = { minutes ->
                        ui.value = ui.value.copy(
                            timerMin = minutes,
                            timerEndWall = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L,
                        )
                        service?.setTimer(minutes)
                    },
                    scope = { service?.scope() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!bound) {
            bindService(Intent(this, NoiseService::class.java), connection, Context.BIND_AUTO_CREATE)
            bound = true
        }
    }

    override fun onStop() {
        if (bound) {
            service?.listener = null
            try { unbindService(connection) } catch (_: Throwable) {}
            bound = false
        }
        service = null
        super.onStop()
    }

    private fun startPlayback() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, NoiseService::class.java).setAction(NoiseService.ACTION_PLAY),
        )
    }

    private fun pausePlayback() {
        val s = service
        if (s != null) {
            s.pause()
        } else {
            startService(Intent(this, NoiseService::class.java).setAction(NoiseService.ACTION_PAUSE))
        }
    }

    private fun update(params: Params) {
        val id = Presets.match(params)?.id
        ui.value = ui.value.copy(params = params, presetId = id)
        service?.updateParams(params, id)
    }

    private fun stepPreset(direction: Int) {
        val presets = Presets.all
        val current = presets.indexOfFirst { it.id == ui.value.presetId }
        val next = when {
            current < 0 -> if (direction > 0) 0 else presets.size - 1
            else -> (current + direction + presets.size) % presets.size
        }
        update(presets[next].applyTo(ui.value.params))
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

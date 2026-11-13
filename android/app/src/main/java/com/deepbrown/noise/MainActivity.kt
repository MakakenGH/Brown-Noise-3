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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var service: NoiseService? = null
    private val ui = mutableStateOf(UiState())
    private val sounds = mutableStateListOf<SavedSound>()
    private val showReorderHint = mutableStateOf(false)
    private var bound = false
    private lateinit var store: SoundStore

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as? NoiseService.LocalBinder)?.service ?: return
            service = s
            s.listener = { ui.value = it }
            val snapshot = s.snapshot()
            ui.value = snapshot
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
        store = SoundStore(this)
        sounds.addAll(store.loadSounds())
        showReorderHint.value = !store.reorderHintSeen
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            CompositionLocalProvider(LocalIndication provides NoIndication) {
                ColorsOfNoiseTheme {
                    RequestNotificationPermission()
                    val error = ui.value.error
                    LaunchedEffect(error) {
                        if (error != null) {
                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                        }
                    }
                    ColorsOfNoiseApp(
                        ui = ui.value,
                        sounds = sounds.toList(),
                        onQuick = ::toggleQuick,
                        onPlaySound = ::toggleSound,
                        onToggleNowPlaying = ::toggleNowPlaying,
                        onSaveDraft = ::saveDraft,
                        onDeleteSound = ::deleteSound,
                        onMoveSound = ::moveSound,
                        onReorderFinished = ::persistOrder,
                        onReorderHintSeen = ::markReorderHintSeen,
                        showReorderHint = showReorderHint.value,
                        onRenameSound = ::renameSound,
                        onVolume = ::setVolume,
                        onTimer = ::setTimer,
                        onFadeIn = {
                            ui.value = ui.value.copy(fadeInSec = it)
                            service?.setFadeIn(it)
                        },
                        onFadeOut = {
                            ui.value = ui.value.copy(fadeOutSec = it)
                            service?.setFadeOut(it)
                        },
                        onPreviewStart = ::startPreview,
                        onPreviewStop = ::pausePlayback,
                        onDiscardPreview = ::discardPreview,
                        onPreviewParams = ::updatePreview,
                        previewHintSeen = store.previewHintSeen,
                        onPreviewHintSeen = { store.previewHintSeen = true },
                        scope = { service?.scope() },
                    )
                }
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

    /* ------------------------------------------------------------------ */
    /* Playback                                                            */
    /* ------------------------------------------------------------------ */

    private fun startPlayback(params: Params, name: String, id: String?) {
        val intent = Intent(this, NoiseService::class.java)
            .setAction(NoiseService.ACTION_PLAY)
            .putExtra(NoiseService.EXTRA_DEEP, params.deep)
            .putExtra(NoiseService.EXTRA_RUMBLE, params.rumble)
            .putExtra(NoiseService.EXTRA_WIDTH, params.width)
            .putExtra(NoiseService.EXTRA_SWELL, params.swell)
            .putExtra(NoiseService.EXTRA_VOLUME, params.volume)
            .putExtra(NoiseService.EXTRA_COLOR, params.color.name)
            .putExtra(NoiseService.EXTRA_RAIN, params.rain)
            .putExtra(NoiseService.EXTRA_DROPS, params.drops)
            .putExtra(NoiseService.EXTRA_PLINK, params.plink)
            .putExtra(NoiseService.EXTRA_WIND, params.wind)
            .putExtra(NoiseService.EXTRA_FAN, params.fan)
            .putExtra(NoiseService.EXTRA_AIR, params.air)
            .putExtra(NoiseService.EXTRA_DRIFT, params.drift)
            .putExtra(NoiseService.EXTRA_NAME, name)
            .putExtra(NoiseService.EXTRA_ID, id)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun pausePlayback() {
        val s = service
        if (s != null) {
            s.pause()
        } else {
            startService(Intent(this, NoiseService::class.java).setAction(NoiseService.ACTION_PAUSE))
        }
    }

    private fun toggleQuick(color: NoiseColor) {
        val state = ui.value
        if (state.playing && state.soundId == null && state.params.color == color) {
            pausePlayback()
        } else {
            startPlayback(quickParams(color, store.volume), "${color.label} Noise", null)
        }
    }

    private fun toggleSound(sound: SavedSound) {
        val state = ui.value
        if (state.playing && state.soundId == sound.id) {
            pausePlayback()
        } else {
            startPlayback(sound.params(store.volume), sound.name, sound.id)
        }
    }

    private fun toggleNowPlaying() {
        val state = ui.value
        if (!state.loaded) return
        if (state.playing) {
            pausePlayback()
        } else {
            startPlayback(state.params, state.soundName, state.soundId)
        }
    }

    private fun discardPreview() {
        if (ui.value.soundId != null) return
        val s = service
        if (s != null) {
            s.stop()
        } else {
            startService(Intent(this, NoiseService::class.java).setAction(NoiseService.ACTION_STOP))
        }
    }

    private fun startPreview(draft: Draft) {
        startPlayback(
            draft.params(store.volume),
            draft.name.ifBlank { "Custom sound" },
            null,
        )
    }

    private fun updatePreview(draft: Draft) {
        val name = draft.name.ifBlank { "Custom sound" }
        if (ui.value.playing) {
            service?.updateParams(draft.params(store.volume), name, null)
        } else {
            startPlayback(draft.params(store.volume), name, null)
        }
    }

    /* ------------------------------------------------------------------ */
    /* Collection                                                          */
    /* ------------------------------------------------------------------ */

    private fun saveDraft(draft: Draft, editingId: String?, adoptPlayback: Boolean): SavedSound {
        val id = editingId ?: UUID.randomUUID().toString()
        val sound = SavedSound(
            id = id,
            name = draft.name,
            color = draft.color,
            deep = draft.deep,
            rumble = draft.rumble,
            width = draft.width,
            swell = draft.swell,
            rain = draft.rain,
            drops = draft.drops,
            plink = draft.plink,
            wind = draft.wind,
            fan = draft.fan,
            air = draft.air,
            drift = draft.drift,
        )
        val index = sounds.indexOfFirst { it.id == id }
        if (index >= 0) sounds[index] = sound else sounds.add(0, sound)
        store.saveSounds(sounds.toList())

        val state = ui.value
        if (adoptPlayback) {
            service?.updateParams(sound.params(store.volume), sound.name, sound.id)
        } else if (state.soundId == sound.id) {
            service?.updateParams(sound.params(store.volume), sound.name, sound.id)
        }
        return sound
    }

    private fun deleteSound(sound: SavedSound) {
        sounds.removeAll { it.id == sound.id }
        store.saveSounds(sounds.toList())
        if (ui.value.soundId == sound.id) {
            val s = service
            if (s != null) {
                s.stop()
            } else {
                startService(Intent(this, NoiseService::class.java).setAction(NoiseService.ACTION_STOP))
            }
        }
    }

    private fun moveSound(from: Int, to: Int) {
        if (from == to || from !in sounds.indices || to !in sounds.indices) return
        val sound = sounds.removeAt(from)
        sounds.add(to, sound)
    }

    private fun persistOrder() {
        store.saveSounds(sounds.toList())
    }

    private fun markReorderHintSeen() {
        if (showReorderHint.value) {
            showReorderHint.value = false
            store.reorderHintSeen = true
        }
    }

    private fun renameSound(sound: SavedSound, name: String) {
        val index = sounds.indexOfFirst { it.id == sound.id }
        if (index < 0) return
        sounds[index] = sound.copy(name = name)
        store.saveSounds(sounds.toList())
        if (ui.value.soundId == sound.id) service?.setIdentity(sound.id, name)
    }

    /* ------------------------------------------------------------------ */
    /* Settings                                                            */
    /* ------------------------------------------------------------------ */

    private fun setVolume(value: Float) {
        store.volume = value
        ui.value = ui.value.copy(params = ui.value.params.copy(volume = value))
        service?.setVolume(value)
    }

    private fun setTimer(minutes: Int) {
        val s = service
        if (s != null) {
            s.setTimer(minutes)
        } else {
            startService(
                Intent(this, NoiseService::class.java)
                    .setAction(NoiseService.ACTION_TIMER)
                    .putExtra(NoiseService.EXTRA_MINUTES, minutes),
            )
        }
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

package com.deepbrown.noise

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat

data class UiState(
    val playing: Boolean = false,
    val params: Params = Params(),
    val presetId: String? = "velvet",
    val timerEndWall: Long = 0L,
    val timerMin: Int = 0,
    val error: String? = null,
)

/**
 * Foreground media service. It owns the AudioTrack, the media session and the
 * notification. As long as it plays, Android keeps it alive with the screen off,
 * when the app is minimized and when the activity is gone.
 */
class NoiseService : Service() {

    inner class LocalBinder : Binder() {
        val service: NoiseService get() = this@NoiseService
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var prefs: SharedPreferences
    private lateinit var engine: NoiseEngine
    private lateinit var audioManager: AudioManager
    private lateinit var session: MediaSession
    private lateinit var focusRequest: AudioFocusRequest
    private lateinit var wakeLock: PowerManager.WakeLock

    private var params = Params()
    private var presetId: String? = "velvet"
    private var playing = false
    private var resumeOnFocusGain = false
    private var foregroundStarted = false
    private var timerEndWall = 0L
    private var timerMin = 0
    private var errorText: String? = null
    @Volatile private var destroyed = false

    private var state = UiState()
    var listener: ((UiState) -> Unit)? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                if (playing) pauseInternal(abandonFocus = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = playing
                if (playing) pauseInternal(abandonFocus = false, clearSleepTimer = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Brown noise is already quiet; do not duck.
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    play()
                }
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && playing) {
                resumeOnFocusGain = false
                pauseInternal(abandonFocus = true)
            }
        }
    }

    private val timerRunnable = Runnable {
        timerEndWall = 0L
        timerMin = 0
        engine.setSleepEnd(0L)
        resumeOnFocusGain = false
        if (playing) pauseInternal(abandonFocus = true)
        publish()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        loadState()
        audioManager = getSystemService(AudioManager::class.java)
        engine = NoiseEngine(this)
        engine.setParams(params)
        engine.onUnexpectedStop = {
            handler.post { onAudioTrackStopped() }
        }

        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DeepBrown:playback").apply {
            setReferenceCounted(false)
        }

        createChannel()
        createSession()
        createFocusRequest()
        registerNoisyReceiver()
        publish()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> {
                resumeOnFocusGain = false
                pauseInternal(abandonFocus = true)
            }
            ACTION_STOP -> stopPlayback()
            ACTION_TIMER -> setTimer(intent.getIntExtra(EXTRA_MINUTES, 0))
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // stopWithTask is false: the started foreground service keeps running
        // when the activity is swiped away. Nothing to do here.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(noisyReceiver) } catch (_: Throwable) {}
        engine.stop()
        if (wakeLock.isHeld) wakeLock.release()
        abandonFocus()
        session.isActive = false
        session.release()
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        super.onDestroy()
    }

    /* ------------------------------------------------------------------ */
    /* Playback                                                            */
    /* ------------------------------------------------------------------ */

    @SuppressLint("WakelockTimeout")
    fun play() {
        if (playing) return
        resumeOnFocusGain = false

        // Satisfy the startForegroundService() deadline before doing anything slow.
        startAsForeground()

        if (!requestFocus()) {
            errorText = "Another app is using the audio output. Try again."
            publish()
            updateNotification()
            return
        }
        errorText = null

        engine.setParams(params)
        engine.setSleepEnd(0L)
        engine.setMasterTarget(1.0)
        if (!engine.isRunning) engine.start()
        playing = true

        if (!wakeLock.isHeld) wakeLock.acquire()
        applyTimerOnPlay()
        updateSession()
        updateNotification()
        publish()
    }

    fun pause() {
        resumeOnFocusGain = false
        pauseInternal(abandonFocus = true)
    }

    private fun pauseInternal(abandonFocus: Boolean, clearSleepTimer: Boolean = true) {
        if (!playing) {
            if (abandonFocus) abandonFocus()
            return
        }
        playing = false
        if (clearSleepTimer) {
            clearTimer()
        } else {
            handler.removeCallbacks(timerRunnable)
        }
        engine.setSleepEnd(0L)
        engine.setMasterTarget(0.0)
        handler.postDelayed({
            if (!playing) engine.stop()
        }, FADE_OUT_MS + 80L)

        if (abandonFocus) abandonFocus()
        if (wakeLock.isHeld) wakeLock.release()
        updateSession()
        updateNotification()
        publish()
    }

    private fun stopPlayback() {
        playing = false
        resumeOnFocusGain = false
        clearTimer()
        engine.setSleepEnd(0L)
        engine.setMasterTarget(0.0)
        publish()
        handler.postDelayed({
            if (playing) return@postDelayed          // restarted before the fade ended
            engine.stop()
            if (wakeLock.isHeld) wakeLock.release()
            abandonFocus()
            session.isActive = false
            if (foregroundStarted) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
            }
            publish()
            stopSelf()
        }, FADE_OUT_MS + 80L)
    }

    /* Called by the UI (through the binder). */
    fun updateParams(newParams: Params, newPresetId: String?) {
        params = newParams.clamped()
        presetId = newPresetId
        engine.setParams(params)
        persist()
        updateSession()
        if (playing) updateNotification()
        publish()
    }

    fun setTimer(minutes: Int) {
        handler.removeCallbacks(timerRunnable)
        if (minutes <= 0) {
            timerEndWall = 0L
            timerMin = 0
            engine.setSleepEnd(0L)
            publish()
            return
        }
        timerMin = minutes
        timerEndWall = System.currentTimeMillis() + minutes * 60_000L
        handler.removeCallbacks(timerRunnable)
        handler.postDelayed(timerRunnable, minutes * 60_000L)
        if (playing) engine.setSleepEnd(SystemClock.elapsedRealtime() + minutes * 60_000L)
        publish()
    }

    fun snapshot(): UiState = state

    fun scope(): FloatArray? = engine.scope()

    /* ------------------------------------------------------------------ */
    /* State                                                               */
    /* ------------------------------------------------------------------ */

    /* The audio device can disappear (a Bluetooth route ends, audio focus is
     * lost in a way the system does not report, ...). Reflect that instead of
     * showing "Playing" while nothing comes out. */
    private fun onAudioTrackStopped() {
        if (destroyed || !playing) return
        playing = false
        resumeOnFocusGain = false
        clearTimer()
        if (wakeLock.isHeld) wakeLock.release()
        abandonFocus()
        errorText = "Playback stopped. Tap play to restart."
        updateSession()
        updateNotification()
        publish()
    }

    private fun publish() {
        state = UiState(
            playing = playing,
            params = params,
            presetId = presetId,
            timerEndWall = timerEndWall,
            timerMin = timerMin,
            error = errorText,
        )
        listener?.invoke(state)
    }

    private fun applyTimerOnPlay() {
        val remaining = timerEndWall - System.currentTimeMillis()
        if (remaining <= 0L) {
            timerEndWall = 0L
            timerMin = 0
            engine.setSleepEnd(0L)
            return
        }
        handler.removeCallbacks(timerRunnable)
        handler.postDelayed(timerRunnable, remaining)
        engine.setSleepEnd(SystemClock.elapsedRealtime() + remaining)
    }

    private fun clearTimer() {
        handler.removeCallbacks(timerRunnable)
        timerEndWall = 0L
        timerMin = 0
    }

    private fun loadState() {
        params = Params(
            deep = prefs.getFloat(KEY_DEEP, 0.50f),
            rumble = prefs.getFloat(KEY_RUMBLE, 0.35f),
            width = prefs.getFloat(KEY_WIDTH, 0.60f),
            swell = prefs.getFloat(KEY_SWELL, 0.10f),
            volume = prefs.getFloat(KEY_VOLUME, 0.55f),
        ).clamped()
        presetId = prefs.getString(KEY_PRESET, "velvet")
    }

    private fun persist() {
        prefs.edit()
            .putFloat(KEY_DEEP, params.deep)
            .putFloat(KEY_RUMBLE, params.rumble)
            .putFloat(KEY_WIDTH, params.width)
            .putFloat(KEY_SWELL, params.swell)
            .putFloat(KEY_VOLUME, params.volume)
            .putString(KEY_PRESET, presetId)
            .apply()
    }

    /* ------------------------------------------------------------------ */
    /* Audio focus                                                         */
    /* ------------------------------------------------------------------ */

    private fun createFocusRequest() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    }

    private fun requestFocus(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun abandonFocus() {
        try { audioManager.abandonAudioFocusRequest(focusRequest) } catch (_: Throwable) {}
    }

    /* ------------------------------------------------------------------ */
    /* Media session + notification                                        */
    /* ------------------------------------------------------------------ */

    private fun createSession() {
        session = MediaSession(this, "DeepBrown").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = this@NoiseService.play()
                override fun onPause() {
                    resumeOnFocusGain = false
                    pauseInternal(abandonFocus = true)
                }
                override fun onStop() = stopPlayback()
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setSessionActivity(contentIntent())
            isActive = true
        }
        updateSession()
    }

    private fun updateSession() {
        val name = presetName()
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Deep brown noise")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, name)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Deep Brown")
                .build(),
        )
        val pb = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP,
            )
            .setState(
                if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                if (playing) 1f else 0f,
            )
            .build()
        session.setPlaybackState(pb)
        session.isActive = true
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Background brown-noise playback"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        foregroundStarted = true
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val playPause = if (playing) pauseIntent() else playIntent()
        val stop = stopIntent()
        val icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_noise)
            .setContentTitle("Deep brown noise")
            .setContentText(if (playing) "${presetName()} · Playing" else "Paused")
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, icon),
                    if (playing) "Pause" else "Play",
                    playPause,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stop),
                    "Stop",
                    stop,
                ).build(),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun playIntent(): PendingIntent = PendingIntent.getForegroundService(
        this,
        1,
        Intent(this, NoiseService::class.java).setAction(ACTION_PLAY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun pauseIntent(): PendingIntent = PendingIntent.getService(
        this,
        2,
        Intent(this, NoiseService::class.java).setAction(ACTION_PAUSE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        3,
        Intent(this, NoiseService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun presetName(): String = Presets.byId(presetId)?.name ?: "Custom"

    private fun registerNoisyReceiver() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, filter)
        }
    }

    companion object {
        const val ACTION_PLAY = "com.deepbrown.noise.PLAY"
        const val ACTION_PAUSE = "com.deepbrown.noise.PAUSE"
        const val ACTION_STOP = "com.deepbrown.noise.STOP"
        const val ACTION_TIMER = "com.deepbrown.noise.TIMER"
        const val EXTRA_MINUTES = "minutes"

        private const val CHANNEL_ID = "playback"
        private const val NOTIF_ID = 42
        private const val FADE_OUT_MS = 300L
        private const val PREFS = "deepbrown"
        private const val KEY_DEEP = "deep"
        private const val KEY_RUMBLE = "rumble"
        private const val KEY_WIDTH = "width"
        private const val KEY_SWELL = "swell"
        private const val KEY_VOLUME = "volume"
        private const val KEY_PRESET = "preset"
    }
}

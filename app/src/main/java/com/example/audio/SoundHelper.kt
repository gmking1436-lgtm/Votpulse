package com.example.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.IOException

/**
 * Ultra-low battery footprint audio player for VoltPulse.
 *
 * Architecture:
 * - Uses [SoundPool] for immediate, low-latency playback of short plug-in and unplug effects in background.
 * - Uses a unified [previewMediaPlayer] singleton for all UI audio previews with zero overlap.
 * - Uses [MediaPlayer] for the continuous, looped 100% full-charge alert.
 * - Handles audio focus acquisition and instant memory release upon alarm dismissal or screen exit.
 * - Resilient URI resolution with cascading fallbacks against missing or revoked custom audio URIs.
 */
class SoundHelper(context: Context) {

    private val applicationContext: Context = context.applicationContext
    private val audioManager: AudioManager =
        applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val alarmLock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Unified preview player for Sound Settings UI
    private val previewLock = Any()
    private var previewMediaPlayer: MediaPlayer? = null

    // Lightweight SoundPool: 2 max concurrent streams, low battery footprint
    private val soundPool: SoundPool
    private val soundCache = mutableMapOf<String, Int>()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Permanent audio focus loss, stopping alarm and preview")
                stopFullChargeAlarm()
                stopPreview()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                synchronized(alarmLock) {
                    try {
                        if (mediaPlayer?.isPlaying == true) {
                            mediaPlayer?.pause()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed pausing media player on transient focus loss", e)
                    }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                synchronized(alarmLock) {
                    try {
                        mediaPlayer?.start()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed resuming media player on focus gain", e)
                    }
                }
            }
        }
    }

    init {
        val soundPoolAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(soundPoolAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { pool, sampleId, status ->
            if (status == 0) {
                pool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f)
            } else {
                Log.w(TAG, "SoundPool sample load failed with status: $status (id=$sampleId)")
            }
        }
    }

    // ========================================================================
    // Low-Latency Short Sound Effects (SoundPool - Background Triggers)
    // ========================================================================

    /**
     * Plays the short sound effect for charger connection using [SoundPool].
     * Ultra-fast response with zero warm-up latency.
     */
    fun playPluggedSound(customUriString: String? = null) {
        playShortTone(
            customUriString = customUriString,
            fallbackType = RingtoneManager.TYPE_NOTIFICATION
        )
    }

    /**
     * Plays the short sound effect for charger disconnection using [SoundPool].
     */
    fun playUnpluggedSound(customUriString: String? = null) {
        playShortTone(
            customUriString = customUriString,
            fallbackType = RingtoneManager.TYPE_NOTIFICATION
        )
    }

    /**
     * Plays a gentle recurring chime for prolonged charging beyond user target.
     */
    fun playGentleReminderChime() {
        playShortTone(
            customUriString = null,
            fallbackType = RingtoneManager.TYPE_NOTIFICATION
        )
    }

    /**
     * Plays an audible alert chime when battery drops below low-battery threshold.
     */
    fun playLowBatteryAlert() {
        playShortTone(
            customUriString = null,
            fallbackType = RingtoneManager.TYPE_NOTIFICATION
        )
    }

    /**
     * Attempts loading and playing the given tone through [SoundPool].
     * Falls back safely to default system tones if the custom URI is missing or inaccessible.
     */
    fun playShortTone(customUriString: String?, fallbackType: Int = RingtoneManager.TYPE_NOTIFICATION) {
        val targetUri = resolvePlayableUri(
            primaryUriString = customUriString,
            defaultType = fallbackType
        ) ?: run {
            Log.w(TAG, "Unable to resolve any audio URI for short tone")
            return
        }

        val uriKey = targetUri.toString()
        val cachedSoundId = soundCache[uriKey]

        if (cachedSoundId != null) {
            val playResult = soundPool.play(cachedSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            if (playResult != 0) return
            soundCache.remove(uriKey)
        }

        loadAndPlayViaSoundPool(targetUri, uriKey, fallbackType)
    }

    private fun loadAndPlayViaSoundPool(uri: Uri, cacheKey: String, fallbackType: Int) {
        var afd: AssetFileDescriptor? = null
        try {
            afd = applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")
            if (afd != null) {
                val soundId = soundPool.load(afd.fileDescriptor, afd.startOffset, afd.length, 1)
                soundCache[cacheKey] = soundId
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed opening AssetFileDescriptor for URI: $uri ($e), trying fallback")
        } finally {
            try {
                afd?.close()
            } catch (_: IOException) {}
        }

        // Fallback to default system sound if custom URI failed
        val defaultUri = RingtoneManager.getDefaultUri(fallbackType)
        if (defaultUri != null && defaultUri != uri) {
            var fallbackAfd: AssetFileDescriptor? = null
            try {
                fallbackAfd = applicationContext.contentResolver.openAssetFileDescriptor(defaultUri, "r")
                if (fallbackAfd != null) {
                    val fallbackId = soundPool.load(
                        fallbackAfd.fileDescriptor,
                        fallbackAfd.startOffset,
                        fallbackAfd.length,
                        1
                    )
                    soundCache[defaultUri.toString()] = fallbackId
                }
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Critical failure loading fallback SoundPool tone", fallbackEx)
            } finally {
                try {
                    fallbackAfd?.close()
                } catch (_: IOException) {}
            }
        }
    }

    // ========================================================================
    // Unified Audio Preview Controller (UI Previews - No Overlapping)
    // ========================================================================

    /**
     * Plays a preview of any alert sound using a unified, singleton [MediaPlayer].
     * Guarantees that any currently playing audio is immediately stopped and released
     * before starting a new track, eliminating audio overlaps and background leaks.
     */
    fun playPreview(
        context: Context,
        uri: Uri?,
        defaultType: Int,
        onComplete: () -> Unit
    ) {
        synchronized(previewLock) {
            stopPreview()

            val resolvedUri = resolvePlayableUri(
                primaryUriString = uri?.toString(),
                defaultType = defaultType
            ) ?: run {
                Log.w(TAG, "Unable to resolve playable URI for preview")
                onComplete()
                return
            }

            val usage = if (defaultType == RingtoneManager.TYPE_ALARM) {
                AudioAttributes.USAGE_ALARM
            } else {
                AudioAttributes.USAGE_NOTIFICATION
            }

            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            try {
                val player = MediaPlayer().apply {
                    setDataSource(context.applicationContext, resolvedUri)
                    setAudioAttributes(attributes)
                    isLooping = false
                    setOnCompletionListener { mp ->
                        synchronized(previewLock) {
                            try {
                                mp.reset()
                                mp.release()
                            } catch (e: Exception) {
                                Log.w(TAG, "Error releasing preview MediaPlayer on completion", e)
                            } finally {
                                if (previewMediaPlayer == mp) {
                                    previewMediaPlayer = null
                                }
                            }
                        }
                        onComplete()
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "Preview MediaPlayer error: what=$what extra=$extra")
                        synchronized(previewLock) {
                            try {
                                mp.reset()
                                mp.release()
                            } catch (e: Exception) {
                                Log.w(TAG, "Error releasing preview MediaPlayer on error", e)
                            } finally {
                                if (previewMediaPlayer == mp) {
                                    previewMediaPlayer = null
                                }
                            }
                        }
                        onComplete()
                        true
                    }
                    prepare()
                    start()
                }
                previewMediaPlayer = player
                Log.d(TAG, "Audio preview started for URI: $resolvedUri")
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing preview MediaPlayer for URI: $resolvedUri", e)
                stopPreview()
                onComplete()
            }
        }
    }

    /**
     * Safely stops and releases the preview MediaPlayer, resetting all playback states.
     */
    fun stopPreview() {
        synchronized(previewLock) {
            previewMediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.reset()
                    player.release()
                    Log.d(TAG, "Preview MediaPlayer stopped and released")
                } catch (e: Exception) {
                    Log.w(TAG, "Exception while releasing preview MediaPlayer", e)
                } finally {
                    previewMediaPlayer = null
                }
            }
        }
    }

    // ========================================================================
    // Continuous 100% Full-Charge Looping Alarm (MediaPlayer)
    // ========================================================================

    /**
     * Starts continuous looping playback for 100% full battery charge.
     * Safely acquires transient exclusive audio focus and configures loop properties.
     */
    fun startFullChargeAlarm(customUriString: String? = null) {
        synchronized(alarmLock) {
            stopFullChargeAlarm()
            stopPreview()

            val alarmUri = resolvePlayableUri(
                primaryUriString = customUriString,
                defaultType = RingtoneManager.TYPE_ALARM
            ) ?: run {
                Log.e(TAG, "No valid alarm audio URI could be resolved")
                return
            }

            val alarmAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // Request Audio Focus
            val focusAcquired = requestAlarmAudioFocus(alarmAttributes)
            if (!focusAcquired) {
                Log.w(TAG, "Audio focus not granted; attempting playback regardless for critical full charge")
            }

            try {
                val player = MediaPlayer().apply {
                    setDataSource(applicationContext, alarmUri)
                    setAudioAttributes(alarmAttributes)
                    isLooping = true
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "MediaPlayer error occurred: what=$what extra=$extra")
                        stopFullChargeAlarm()
                        true
                    }
                    prepare()
                    start()
                }
                mediaPlayer = player
                Log.i(TAG, "Full-charge continuous alarm started successfully with URI: $alarmUri")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start full-charge alarm with URI: $alarmUri, retrying default fallback", e)
                retryDefaultAlarmFallback(alarmAttributes)
            }
        }
    }

    private fun retryDefaultAlarmFallback(attributes: AudioAttributes) {
        val fallbackTypes = listOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_RINGTONE,
            RingtoneManager.TYPE_NOTIFICATION
        )

        for (type in fallbackTypes) {
            val fallbackUri = RingtoneManager.getDefaultUri(type) ?: continue
            try {
                val player = MediaPlayer().apply {
                    setDataSource(applicationContext, fallbackUri)
                    setAudioAttributes(attributes)
                    isLooping = true
                    prepare()
                    start()
                }
                mediaPlayer = player
                Log.i(TAG, "Full-charge alarm recovered using fallback tone: $fallbackUri")
                return
            } catch (fallbackError: Exception) {
                Log.w(TAG, "Fallback attempt for type $type failed: $fallbackError")
            }
        }
        Log.e(TAG, "All alarm audio fallbacks failed")
    }

    /**
     * Safely stops the 100% full-charge continuous alarm.
     * Immediately releases audio focus and frees memory without leaking resources.
     */
    fun stopFullChargeAlarm() {
        synchronized(alarmLock) {
            abandonAlarmAudioFocus()

            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.reset()
                    player.release()
                    Log.d(TAG, "Full-charge MediaPlayer stopped and released successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Exception encountered while releasing MediaPlayer", e)
                } finally {
                    mediaPlayer = null
                }
            }
        }
    }

    // ========================================================================
    // High Temperature / Overheat Warning Looping Alarm (MediaPlayer)
    // ========================================================================

    private val overheatLock = Any()
    private var overheatMediaPlayer: MediaPlayer? = null

    /**
     * Starts audio warning for battery overheating condition.
     */
    fun startOverheatAlarm(customUriString: String? = null) {
        synchronized(overheatLock) {
            stopOverheatAlarm()
            stopPreview()

            val alarmUri = resolvePlayableUri(
                primaryUriString = customUriString,
                defaultType = RingtoneManager.TYPE_ALARM
            ) ?: run {
                Log.e(TAG, "No valid overheat alarm audio URI could be resolved")
                return
            }

            val alarmAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            requestAlarmAudioFocus(alarmAttributes)

            try {
                val player = MediaPlayer().apply {
                    setDataSource(applicationContext, alarmUri)
                    setAudioAttributes(alarmAttributes)
                    isLooping = true
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "Overheat MediaPlayer error: what=$what extra=$extra")
                        stopOverheatAlarm()
                        true
                    }
                    prepare()
                    start()
                }
                overheatMediaPlayer = player
                Log.i(TAG, "Overheat warning audio started successfully with URI: $alarmUri")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start overheat alarm with URI: $alarmUri", e)
            }
        }
    }

    /**
     * Safely stops the high-temperature overheat alarm.
     */
    fun stopOverheatAlarm() {
        synchronized(overheatLock) {
            overheatMediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.reset()
                    player.release()
                    Log.d(TAG, "Overheat MediaPlayer stopped and released successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Exception releasing Overheat MediaPlayer", e)
                } finally {
                    overheatMediaPlayer = null
                }
            }
        }
    }

    /**
     * Releases all allocated resources, including SoundPool and MediaPlayers.
     * Call when destroying the host lifecycle or terminating audio helper.
     */
    fun release() {
        stopPreview()
        stopFullChargeAlarm()
        stopOverheatAlarm()
        try {
            soundCache.clear()
            soundPool.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing SoundPool", e)
        }
    }

    // ========================================================================
    // Audio Focus & URI Utilities
    // ========================================================================

    private fun requestAlarmAudioFocus(attributes: AudioAttributes): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            this.audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAlarmAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { request ->
                    audioManager.abandonAudioFocusRequest(request)
                    audioFocusRequest = null
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error abandoning audio focus", e)
        }
    }

    private fun resolvePlayableUri(primaryUriString: String?, defaultType: Int): Uri? {
        if (!primaryUriString.isNullOrBlank()) {
            try {
                val customUri = Uri.parse(primaryUriString)
                applicationContext.contentResolver.openAssetFileDescriptor(customUri, "r")?.use {
                    return customUri
                }
            } catch (e: Exception) {
                Log.w(TAG, "Custom URI inaccessible: $primaryUriString, falling back to default", e)
            }
        }

        return RingtoneManager.getDefaultUri(defaultType)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    companion object {
        private const val TAG = "SoundHelper"

        @Volatile
        private var instance: SoundHelper? = null

        fun getInstance(context: Context): SoundHelper {
            return instance ?: synchronized(this) {
                instance ?: SoundHelper(context).also { instance = it }
            }
        }
    }
}

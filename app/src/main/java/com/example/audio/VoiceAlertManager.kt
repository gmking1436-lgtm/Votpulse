package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.preferences.BatteryPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Production-grade Text-to-Speech Voice Announcement Manager for VoltPulse.
 *
 * Capabilities:
 * - Dynamically announces real-time battery status on hardware events (plugged in, unplugged,
 *   longevity target reached, 100% full charge, and critical overheat).
 * - Reads current charge percentage and wattage dynamically (e.g. "Charging connected at 65 percent").
 * - Lazy lifecycle initialization: initializes TTS on demand to minimize startup overhead.
 * - Adheres to user's preference toggle [BatteryPreferences.isVoiceAnnouncementEnabled].
 * - Configures speech audio attributes for USAGE_ASSISTANCE_SONIFICATION to avoid disrupting media playback.
 * - Releases TTS engine cleanly on shutdown.
 */
class VoiceAlertManager private constructor(context: Context) {

    private val applicationContext: Context = context.applicationContext
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = BatteryPreferences(applicationContext)

    private val isInitialized = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)
    private var textToSpeech: TextToSpeech? = null
    private val pendingUtterances = mutableListOf<String>()
    private val initLock = Any()

    init {
        // Pre-initialize TTS in background if voice announcements are currently active
        managerScope.launch {
            if (preferences.isVoiceAnnouncementEnabled.first()) {
                ensureInitialized()
            }
        }
    }

    private fun ensureInitialized(onReady: (() -> Unit)? = null) {
        synchronized(initLock) {
            if (isInitialized.get() && textToSpeech != null) {
                onReady?.invoke()
                return
            }

            if (isInitializing.get()) {
                if (onReady != null) {
                    synchronized(pendingUtterances) {
                        // Will be dispatched once init finishes
                    }
                }
                return
            }

            isInitializing.set(true)
            try {
                textToSpeech = TextToSpeech(applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        configureTts()
                        isInitialized.set(true)
                        isInitializing.set(false)
                        Log.d(TAG, "TextToSpeech engine successfully initialized")

                        // Flush any pending announcements
                        synchronized(pendingUtterances) {
                            val queued = ArrayList(pendingUtterances)
                            pendingUtterances.clear()
                            queued.forEach { text -> speakInternal(text) }
                        }
                        onReady?.invoke()
                    } else {
                        isInitializing.set(false)
                        Log.w(TAG, "TextToSpeech engine initialization failed with status $status")
                    }
                }
            } catch (e: Exception) {
                isInitializing.set(false)
                Log.e(TAG, "Failed instantiating TextToSpeech", e)
            }
        }
    }

    private fun configureTts() {
        val tts = textToSpeech ?: return
        try {
            // Set language to system locale or fallback to English
            val locale = Locale.getDefault()
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "System locale $locale not supported by TTS, falling back to US English")
                tts.setLanguage(Locale.US)
            }

            // Natural cadence and pitch
            tts.setSpeechRate(1.05f)
            tts.setPitch(1.0f)

            // Audio attributes for non-intrusive alert sonification
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts.setAudioAttributes(audioAttributes)

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.v(TAG, "Voice announcement started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.v(TAG, "Voice announcement completed: $utteranceId")
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.w(TAG, "Voice announcement error for: $utteranceId")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed configuring TextToSpeech attributes", e)
        }
    }

    /**
     * Speaks the given announcement if voice announcements are enabled in preferences.
     */
    fun speak(text: String, force: Boolean = false) {
        managerScope.launch {
            try {
                val isEnabled = preferences.isVoiceAnnouncementEnabled.first()
                if (!isEnabled && !force) {
                    Log.v(TAG, "Voice announcement skipped: user disabled in settings")
                    return@launch
                }

                if (!isInitialized.get()) {
                    synchronized(pendingUtterances) {
                        pendingUtterances.add(text)
                    }
                    ensureInitialized()
                } else {
                    speakInternal(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating voice announcement dispatch", e)
            }
        }
    }

    private fun speakInternal(text: String) {
        val tts = textToSpeech ?: return
        try {
            val utteranceId = "voltpulse_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            Log.i(TAG, "Voice announcement queued: \"$text\"")
        } catch (e: Exception) {
            Log.w(TAG, "Failed calling speak on TextToSpeech", e)
        }
    }

    // ========================================================================
    // Dedicated Announcement Recipes
    // ========================================================================

    /**
     * Announces power cable connected with live battery level and optional wattage.
     * Example: "Charging connected at 65 percent. Rapid charging at 18 watts."
     */
    fun announcePluggedIn(percentage: Int, watts: Float = 0f) {
        val message = StringBuilder("Charging connected at $percentage percent.")
        if (watts >= 10f) {
            message.append(" Fast charging at ${watts.roundToInt()} watts.")
        }
        speak(message.toString())
    }

    /**
     * Announces charger disconnected with current charge percentage.
     * Example: "Charger disconnected at 82 percent."
     */
    fun announceUnplugged(percentage: Int) {
        val message = "Charger disconnected at $percentage percent."
        speak(message)
    }

    /**
     * Announces longevity target limit reached (e.g. 80%).
     * Example: "Battery longevity target reached at 80 percent. Please disconnect charger."
     */
    fun announceTargetReached(targetPercentage: Int) {
        val message = "Battery longevity target reached at $targetPercentage percent. Please disconnect charger to preserve battery health."
        speak(message)
    }

    /**
     * Announces full charge completion (100%).
     * Example: "Battery fully charged at 100 percent. Unplug device to extend battery longevity."
     */
    fun announceFullCharge() {
        val message = "Battery fully charged at 100 percent. Unplug device to extend battery longevity."
        speak(message)
    }

    /**
     * Announces prolonged charging reminder (e.g. 30 minutes past target).
     */
    fun announceProlongedCharging(targetPercentage: Int) {
        val message = "Prolonged charging reminder: Battery has been connected for over 30 minutes past your $targetPercentage percent target. Unplug device to prevent battery degradation."
        speak(message)
    }

    /**
     * Announces low battery warning.
     * Example: "Warning: Battery low at 18 percent. Please connect charger."
     */
    fun announceLowBattery(percentage: Int) {
        val message = "Warning: Battery low at $percentage percent. Connect charger to protect battery health."
        speak(message)
    }

    /**
     * Announces battery thermal alert.
     * Example: "Warning: High battery temperature at 43 degrees Celsius."
     */
    fun announceOverheat(temperatureCelsius: Float) {
        val formatted = String.format(Locale.US, "%.1f", temperatureCelsius)
        val message = "Warning: Battery temperature high at $formatted degrees Celsius. Disconnect charger immediately."
        speak(message)
    }

    /**
     * Stops any currently playing speech.
     */
    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
    }

    /**
     * Releases TTS resources on application process termination.
     */
    fun shutdown() {
        synchronized(initLock) {
            try {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
                textToSpeech = null
                isInitialized.set(false)
                isInitializing.set(false)
                Log.d(TAG, "TextToSpeech engine released cleanly")
            } catch (e: Exception) {
                Log.w(TAG, "Error shutting down TTS engine", e)
            }
        }
    }

    companion object {
        private const val TAG = "VoiceAlertManager"

        @Volatile
        private var instance: VoiceAlertManager? = null

        fun getInstance(context: Context): VoiceAlertManager {
            return instance ?: synchronized(this) {
                instance ?: VoiceAlertManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

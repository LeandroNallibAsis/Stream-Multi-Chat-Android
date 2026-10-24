package com.streammultichat.app.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.streammultichat.app.data.preferences.TtsMode
import com.streammultichat.app.data.preferences.UserPreferences
import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.NotificationType
import com.streammultichat.core.model.StreamNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "TtsManager"

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _engineName = MutableStateFlow<String?>(null)
    val engineName: StateFlow<String?> = _engineName.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val pendingQueue = AtomicInteger(0)
    private val utteranceIdCounter = AtomicInteger(0)

    // Deduplication cache to guarantee no message is ever read twice
    private val spokenMessageIds = object : LinkedHashMap<String, Boolean>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 300
        }
    }
    private val spokenLock = Any()

    private val urlRegex = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val repeatedCharsRegex = Regex("(.)\\1{2,}")
    private val emoteCodeRegex = Regex(":[a-zA-Z0-9_]+:")

    private var textToSpeech: TextToSpeech? = null
    private var engineAttemptIndex = 0

    // Priority ordered fallback list: system default, Google TTS, Samsung SMT
    private val candidateEngines = listOf(
        null,
        "com.google.android.tts",
        "com.samsung.SMT"
    )

    init {
        tryNextEngine()
    }

    private fun tryNextEngine() {
        if (engineAttemptIndex >= candidateEngines.size) {
            Log.e(TAG, "All TTS candidate engines failed to initialize")
            _isInitialized.value = false
            _errorMessage.value = "No se detectó un motor de síntesis de voz (TTS) activo en el teléfono."
            return
        }

        val targetEngine = candidateEngines[engineAttemptIndex]
        Log.d(TAG, "Attempting TTS initialization with candidate: $targetEngine (index $engineAttemptIndex)")

        try {
            textToSpeech?.shutdown()
            textToSpeech = if (targetEngine != null) {
                TextToSpeech(context.applicationContext, this, targetEngine)
            } else {
                TextToSpeech(context.applicationContext, this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating TextToSpeech for candidate $targetEngine", e)
            engineAttemptIndex++
            tryNextEngine()
        }
    }

    override fun onInit(status: Int) {
        val currentCandidate = candidateEngines.getOrNull(engineAttemptIndex)
        Log.d(TAG, "onInit callback received: status=$status for engine candidate=$currentCandidate")

        if (status == TextToSpeech.SUCCESS) {
            val tts = textToSpeech
            if (tts != null) {
                _isInitialized.value = true
                _errorMessage.value = null
                val activeEngine = tts.defaultEngine ?: currentCandidate ?: "Sistema"
                _engineName.value = activeEngine
                Log.i(TAG, "TTS successfully initialized using engine: $activeEngine")
                configureTts(tts)
            } else {
                _isInitialized.value = false
                Log.w(TAG, "status is SUCCESS but textToSpeech reference is null")
            }
        } else {
            Log.w(TAG, "Engine candidate $currentCandidate failed with status=$status. Trying next...")
            engineAttemptIndex++
            tryNextEngine()
        }
    }

    private fun configureTts(tts: TextToSpeech) {
        try {
            // Audio routing: standard Media stream with Speech content type
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts.setAudioAttributes(audioAttributes)

            // Select best available Spanish locale (ES, MX, generic) or device locale
            val spanishCandidates = listOf(
                Locale.Builder().setLanguage("es").setRegion("ES").build(),
                Locale.Builder().setLanguage("es").setRegion("MX").build(),
                Locale.Builder().setLanguage("es").build()
            )

            var selectedLocale = Locale.getDefault()
            for (loc in spanishCandidates) {
                val availability = tts.isLanguageAvailable(loc)
                if (availability >= TextToSpeech.LANG_AVAILABLE) {
                    selectedLocale = loc
                    break
                }
            }

            val langResult = tts.setLanguage(selectedLocale)
            Log.d(TAG, "TTS language set to $selectedLocale (result=$langResult)")

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Utterance started: $utteranceId")
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Utterance completed: $utteranceId")
                    val remaining = pendingQueue.decrementAndGet().coerceAtLeast(0)
                    if (remaining == 0) {
                        _isSpeaking.value = false
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "Utterance error: $utteranceId")
                    val remaining = pendingQueue.decrementAndGet().coerceAtLeast(0)
                    if (remaining == 0) {
                        _isSpeaking.value = false
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "Utterance error: $utteranceId (errorCode=$errorCode)")
                    val remaining = pendingQueue.decrementAndGet().coerceAtLeast(0)
                    if (remaining == 0) {
                        _isSpeaking.value = false
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring TextToSpeech engine parameters", e)
        }
    }

    fun speakMessage(message: ChatMessage, preferences: UserPreferences) {
        val shouldSpeak = when (preferences.ttsMode) {
            TtsMode.OFF -> false
            TtsMode.DONATIONS_ONLY -> message.isHighlight
            TtsMode.ALL_MESSAGES -> true
        }
        if (!_isInitialized.value || !shouldSpeak) return

        // Resynchronize pending counter if TTS is currently idle
        val tts = textToSpeech
        if (tts == null || !tts.isSpeaking) {
            pendingQueue.set(0)
            _isSpeaking.value = false
        }

        // Prevent duplicate speech of the exact same message
        synchronized(spokenLock) {
            if (spokenMessageIds.containsKey(message.id)) {
                Log.d(TAG, "Ignoring already-spoken message id: ${message.id}")
                return
            }
            spokenMessageIds[message.id] = true
            // Also register sanitized prefixes to prevent double reading notifications mirrored as chat
            val cleanId = message.id.removePrefix("chat-").removePrefix("bits-")
            spokenMessageIds[cleanId] = true
        }

        // Anti-backlog protection for high-frequency chat:
        // Only drop normal chat messages if the speech queue has a heavy backlog (> 6)
        if (pendingQueue.get() > 6 && !message.isHighlight) {
            Log.d(TAG, "Dropping message '${message.authorName}' due to heavy queue backlog (${pendingQueue.get()})")
            return
        }

        val cleanAuthor = sanitizeForSpeech(message.authorName, maxChars = 25)
        val cleanText = sanitizeForSpeech(message.messageText, maxChars = preferences.ttsMaxChars)

        if (cleanText.isBlank()) return

        val textToSpeak = if (preferences.ttsReadAuthorName) {
            "$cleanAuthor dice: $cleanText"
        } else {
            cleanText
        }

        speakInternal(textToSpeak, preferences.ttsSpeechRate, preferences.ttsPitch, isPriority = message.isHighlight, flush = false)
    }

    fun speakNotification(notification: StreamNotification, preferences: UserPreferences) {
        if (!_isInitialized.value || preferences.ttsMode == TtsMode.OFF) return

        // Deduplication against double notification reads
        synchronized(spokenLock) {
            if (spokenMessageIds.containsKey(notification.id)) {
                Log.d(TAG, "Ignoring already-spoken notification id: ${notification.id}")
                return
            }
            spokenMessageIds[notification.id] = true
            spokenMessageIds["chat-${notification.id}"] = true
            spokenMessageIds["bits-${notification.id}"] = true
        }

        val cleanUser = sanitizeForSpeech(notification.user, maxChars = 25)
        var cleanDesc = sanitizeForSpeech(notification.description, maxChars = preferences.ttsMaxChars)

        // Avoid repeated usernames like "Juan: Juan comenzó a seguirte"
        if (cleanDesc.startsWith(cleanUser, ignoreCase = true)) {
            cleanDesc = cleanDesc.removePrefix(cleanUser).trimStart()
        }

        val textToSpeak = when (notification.type) {
            NotificationType.FOLLOW -> "¡Nuevo seguidor en ${notification.platform.displayName}! $cleanUser comenzó a seguirte."
            NotificationType.SUBSCRIPTION -> "¡Nueva suscripción en ${notification.platform.displayName}! $cleanUser se ha suscrito. $cleanDesc"
            NotificationType.DONATION -> {
                val amt = notification.amount
                if (amt != null) {
                    val curr = notification.currency ?: "dólares"
                    "¡Alerta de donación en ${notification.platform.displayName}! $cleanUser donó ${amt.toLong()} $curr. $cleanDesc"
                } else {
                    "¡Alerta de donación en ${notification.platform.displayName}! $cleanUser: $cleanDesc"
                }
            }
            NotificationType.RAID -> "¡Alerta de raid en ${notification.platform.displayName}! $cleanUser inició una raid. $cleanDesc"
            NotificationType.CHANNEL_POINTS -> "¡Canje de puntos en ${notification.platform.displayName}! $cleanUser: $cleanDesc"
        }

        // Voice alert queues smoothly without interrupting current speech
        speakInternal(textToSpeak, preferences.ttsSpeechRate, preferences.ttsPitch, isPriority = true, flush = false)
    }

    fun speakTest(speechRate: Float, pitch: Float) {
        Log.d(TAG, "speakTest called. isInitialized=${_isInitialized.value}")
        if (!_isInitialized.value) {
            // Re-attempt init if user pressed test while engine was still pending or failed
            engineAttemptIndex = 0
            tryNextEngine()
            return
        }
        val testPhrase = "Prueba de voz en Stream Multi Chat. El audio se reproduce correctamente."
        stop()
        speakInternal(testPhrase, speechRate, pitch, isPriority = true, flush = true)
    }

    fun speakAlertTest(speechRate: Float, pitch: Float) {
        Log.d(TAG, "speakAlertTest called. isInitialized=${_isInitialized.value}")
        if (!_isInitialized.value) {
            engineAttemptIndex = 0
            tryNextEngine()
            return
        }
        stop()
        val testAlertPhrase = "¡Alerta de prueba en Twitch! Usuario Estrella donó 500 bits. ¡Muchas gracias por apoyar el stream!"
        speakInternal(testAlertPhrase, speechRate, pitch, isPriority = true, flush = true)
    }

    fun stop() {
        Log.d(TAG, "stop() called on TTS")
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
        pendingQueue.set(0)
        _isSpeaking.value = false
    }

    fun shutdown() {
        Log.d(TAG, "shutdown() called on TTS")
        stop()
        try {
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
        textToSpeech = null
        _isInitialized.value = false
    }

    fun openTtsSettings(ctx: Context) {
        try {
            val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "TTS_SETTINGS action not found, falling back to general settings", e)
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Could not open system settings", ex)
            }
        }
    }

    private fun speakInternal(
        text: String,
        speechRate: Float,
        pitch: Float,
        isPriority: Boolean,
        flush: Boolean = false
    ) {
        val tts = textToSpeech
        if (tts == null || !_isInitialized.value) {
            Log.w(TAG, "speakInternal aborted: tts=$tts, isInitialized=${_isInitialized.value}")
            return
        }

        try {
            tts.setSpeechRate(speechRate.coerceIn(0.5f, 2.5f))
            tts.setPitch(pitch.coerceIn(0.5f, 2.0f))

            val queueMode = if (flush) {
                pendingQueue.set(1)
                TextToSpeech.QUEUE_FLUSH
            } else {
                pendingQueue.incrementAndGet()
                TextToSpeech.QUEUE_ADD
            }

            val utteranceId = "utt_${utteranceIdCounter.incrementAndGet()}"
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }

            Log.i(TAG, "tts.speak -> '$text' [queueMode=$queueMode, id=$utteranceId, rate=$speechRate, pitch=$pitch]")
            val result = tts.speak(text, queueMode, params, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "tts.speak failed with code=$result")
                pendingQueue.decrementAndGet().coerceAtLeast(0)
                _isSpeaking.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in speakInternal", e)
        }
    }

    private fun sanitizeForSpeech(input: String, maxChars: Int): String {
        var text = input.trim()

        // 1. Replace URLs with "enlace"
        text = urlRegex.replace(text, " enlace ")

        // 2. Remove emote codes (:pog:, :kappa:)
        text = emoteCodeRegex.replace(text, " ")

        // 3. Collapse 3+ repeated characters (e.g. "holaaaaa" -> "holaa")
        text = repeatedCharsRegex.replace(text) { matchResult ->
            val char = matchResult.value[0]
            "$char$char"
        }

        // 4. Truncate long texts
        if (text.length > maxChars) {
            text = text.take(maxChars).trimEnd() + " etcétera."
        }

        return text
    }
}

package com.g150446.voiceclasp

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Cloud ASR through OpenRouter's /audio/transcriptions, as an alternative to Groq Whisper. */
internal class OpenRouterSttBackend(
    private val appContext: Context,
    private val baseUrl: String = OpenRouterLlmBackend.DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient = defaultClient(),
) {
    suspend fun ensureReady(): Result<Unit> = runCatching {
        if (!OpenRouterPrefs.hasApiKey(appContext)) {
            error("OpenRouter API キーが未設定です。モデル設定で入力してください。")
        }
    }

    suspend fun transcribe(
        audioFile: File,
        vocabulary: List<AsrVocabularyTerm>,
    ): Result<TranscriptionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = OpenRouterPrefs.getApiKey(appContext)
            if (apiKey.isBlank()) error("OpenRouter API キーが未設定です。")
            val model = OpenRouterPrefs.getSttModelId(appContext)
            val started = System.currentTimeMillis()
            val body = buildRequestBody(
                model = model,
                audioBase64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP),
                format = audioFile.extension.lowercase().ifBlank { "wav" },
                language = ModelManager.currentSpeechBaseLanguage(appContext),
                vocabularyPrompt = AsrVocabularyCatalog.whisperPrompt(vocabulary),
            )
            val response = httpClient.newCall(
                Request.Builder()
                    .url("$baseUrl/audio/transcriptions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute()
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("OpenRouter STT error ${response.code}: ${responseText.take(200)}")
            }
            val text = parseText(responseText)
            val latencyMs = System.currentTimeMillis() - started
            ModelManager.recordAsrMs(latencyMs)
            Log.d(TAG, "STT ok model=$model latency=${latencyMs}ms")
            // Whisper-family models echo their prompt on noise: treat that as silence.
            val echo = vocabulary.isNotEmpty() &&
                AsrTextFilter.isVocabularyEchoWithoutTrigger(text, vocabulary)
            val cleaned = when {
                echo -> ""
                vocabulary.isEmpty() -> text
                else -> AsrTextFilter.stripTrailingVocabularyFragment(text, vocabulary)
            }
            TranscriptionResult(text = cleaned, languageCode = null, latencyMs = latencyMs)
        }
    }

    /** STT models OpenRouter currently lists (they are not in the default /models catalog). */
    suspend fun fetchModels(): Result<List<OpenRouterModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = OpenRouterPrefs.getApiKey(appContext)
            if (apiKey.isBlank()) error("OpenRouter API キーが未設定です。")
            httpClient.newCall(
                Request.Builder()
                    .url("$baseUrl/models?output_modalities=transcription")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build(),
            ).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error(OpenRouterChatRequestBuilder.safeHttpError(response.code, text))
                }
                OpenRouterModelCatalog.parseModelsJson(text)
            }
        }
    }

    companion object {
        private const val TAG = "OpenRouterStt"

        /** Fast, cheap, Japanese-capable and the same Whisper family the app was tuned against. */
        const val DEFAULT_MODEL = "openai/whisper-large-v3-turbo"

        /** Shown first in settings; ids from OpenRouter's speech-to-text collection. */
        val RECOMMENDED: List<Pair<String, String>> = listOf(
            "openai/whisper-large-v3-turbo" to "おすすめ: 高速・安価・日本語OK（Groqと同系統）",
            "openai/whisper-large-v3" to "精度重視のWhisper（やや遅い）",
            "microsoft/mai-transcribe-2" to "多言語60・OpenRouterで利用数1位",
            "elevenlabs/scribe-v2" to "90言語以上・高精度（割高）",
            "openai/gpt-4o-transcribe" to "GPT-4o系・高品質（トークン課金）",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()

        /** JSON body: base64 audio, ISO language, and (Whisper) the vocabulary prompt for Groq-served routes. */
        internal fun buildRequestBody(
            model: String,
            audioBase64: String,
            format: String,
            language: SpeechBaseLanguage,
            vocabularyPrompt: String?,
        ): String = JSONObject().apply {
            put("model", model)
            put(
                "input_audio",
                JSONObject().apply {
                    put("data", audioBase64)
                    put("format", format)
                },
            )
            when (language) {
                SpeechBaseLanguage.JAPANESE -> put("language", "ja")
                SpeechBaseLanguage.ENGLISH -> put("language", "en")
                SpeechBaseLanguage.AUTO -> Unit
            }
            put("temperature", 0)
            // The JSON endpoint has no top-level prompt; options go to the serving provider only.
            if (!vocabularyPrompt.isNullOrBlank() && model.startsWith("openai/whisper")) {
                put(
                    "provider",
                    JSONObject().put(
                        "options",
                        JSONObject().put("groq", JSONObject().put("prompt", vocabularyPrompt)),
                    ),
                )
            }
        }.toString()

        internal fun parseText(responseBody: String): String =
            runCatching { JSONObject(responseBody.trim()).optString("text", "") }
                .getOrDefault("")
                .trim()
    }
}

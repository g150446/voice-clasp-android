package com.g150446.voiceharness

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterSttBackendTest {
    @Test
    fun requestBodyCarriesBase64AudioModelAndLanguage() {
        val body = JSONObject(
            OpenRouterSttBackend.buildRequestBody(
                model = "openai/whisper-large-v3-turbo",
                audioBase64 = "AAAA",
                format = "wav",
                language = SpeechBaseLanguage.JAPANESE,
                vocabularyPrompt = "パイロットモード",
            ),
        )
        assertEquals("openai/whisper-large-v3-turbo", body.getString("model"))
        assertEquals("AAAA", body.getJSONObject("input_audio").getString("data"))
        assertEquals("wav", body.getJSONObject("input_audio").getString("format"))
        assertEquals("ja", body.getString("language"))
        assertEquals(
            "パイロットモード",
            body.getJSONObject("provider").getJSONObject("options").getJSONObject("groq").getString("prompt"),
        )
    }

    @Test
    fun autoLanguageAndNonWhisperModelsSendNoHints() {
        val body = JSONObject(
            OpenRouterSttBackend.buildRequestBody(
                model = "microsoft/mai-transcribe-2",
                audioBase64 = "AAAA",
                format = "wav",
                language = SpeechBaseLanguage.AUTO,
                vocabularyPrompt = "x",
            ),
        )
        assertFalse(body.has("language"))
        assertFalse(body.has("provider"))
    }

    @Test
    fun parsesTranscriptText() {
        assertEquals("こんにちは", OpenRouterSttBackend.parseText("""{"text":" こんにちは ","usage":{"seconds":2}}"""))
        assertEquals("", OpenRouterSttBackend.parseText("not json"))
    }

    @Test
    fun openRouterIsACloudSttChoiceAndRecommendedFirst() {
        assertTrue(SttBackendId.OPENROUTER.isCloud)
        assertEquals(OpenRouterSttBackend.DEFAULT_MODEL, OpenRouterSttBackend.RECOMMENDED.first().first)
        assertEquals(SttBackendId.OPENROUTER, SttBackendId.fromStorage("OPENROUTER"))
    }
}

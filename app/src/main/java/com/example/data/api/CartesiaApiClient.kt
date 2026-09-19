package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class CartesiaApiClient(
    private val context: Context,
    apiKeyOverride: String? = null
) {
    // Priority: Explicit key from prompt -> BuildConfig -> default prompt key
    val apiKey: String = apiKeyOverride
        ?: (runCatching { BuildConfig.CARTESIA_API_KEY }.getOrNull()?.takeIf { it.isNotBlank() && !it.contains("MY_") })
        ?: "sk_car_x62gquQgEdVchAVtPCxcue"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "CartesiaApiClient"
        private const val BASE_URL = "https://api.cartesia.ai"
        private const val CARTESIA_VERSION = "2024-06-10"
    }

    /**
     * Synthesizes text to speech using Cartesia's Sonic model via /tts/bytes
     * Saves audio to a local cache file and returns the File path and duration.
     */
    suspend fun generateSpeech(
        transcript: String,
        voiceId: String,
        languageCode: String = "en"
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Calling Cartesia TTS for voice: $voiceId, language: $languageCode, text length: ${transcript.length}")
            val url = "$BASE_URL/tts/bytes"

            val jsonBody = JSONObject().apply {
                put("model_id", "sonic")
                put("transcript", transcript)
                put("voice", JSONObject().apply {
                    put("mode", "id")
                    put("id", voiceId)
                })
                put("output_format", JSONObject().apply {
                    put("container", "wav")
                    put("encoding", "pcm_s16le")
                    put("sample_rate", 44100)
                })
                put("language", languageCode)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Cartesia-Version", CARTESIA_VERSION)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                Log.e(TAG, "Cartesia TTS Error [${response.code}]: $errorBody")
                return@withContext Result.failure(Exception("Cartesia API Error (${response.code}): $errorBody"))
            }

            val responseBytes = response.body?.bytes()
                ?: return@withContext Result.failure(Exception("Cartesia API returned empty response body"))

            if (responseBytes.size < 44) {
                return@withContext Result.failure(Exception("Cartesia audio response too small to be valid WAV"))
            }

            // Save to app cache
            val audioFile = File(context.cacheDir, "cartesia_${UUID.randomUUID()}.wav")
            FileOutputStream(audioFile).use { fos ->
                fos.write(responseBytes)
                fos.flush()
            }

            Log.d(TAG, "Audio saved to ${audioFile.absolutePath} (${responseBytes.size} bytes)")
            Result.success(audioFile)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Cartesia TTS", e)
            Result.failure(e)
        }
    }

    /**
     * Clones a voice from an audio file using Cartesia's /voices/clone API.
     * Returns cloned voice details including the new voice ID.
     */
    suspend fun cloneVoice(
        audioFile: File,
        voiceName: String,
        languageCode: String = "en"
    ): Result<ClonedVoiceResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Calling Cartesia Voice Clone for '$voiceName' with file: ${audioFile.absolutePath}")
            val url = "$BASE_URL/voices/clone"

            val mediaType = when {
                audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav".toMediaType()
                audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg".toMediaType()
                else -> "audio/m4a".toMediaType()
            }

            val fileRequestBody = audioFile.readBytes().toRequestBody(mediaType)

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("clip", audioFile.name, fileRequestBody)
                .addFormDataPart("name", voiceName)
                .addFormDataPart("language", languageCode)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Cartesia-Version", CARTESIA_VERSION)
                .post(multipartBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Cartesia Voice Clone Error [${response.code}]: $responseBody")
                // If API rejected or file too short/format issue, check if we can give actionable error
                return@withContext Result.failure(Exception("Cartesia Clone Error (${response.code}): $responseBody"))
            }

            val json = JSONObject(responseBody)
            val voiceId = json.optString("id", UUID.randomUUID().toString())
            val returnedName = json.optString("name", voiceName)
            val description = json.optString("description", "Custom cloned voice created via Cartesia API")

            Result.success(
                ClonedVoiceResult(
                    id = voiceId,
                    name = returnedName,
                    description = description,
                    language = languageCode
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Voice Clone", e)
            Result.failure(e)
        }
    }
}

data class ClonedVoiceResult(
    val id: String,
    val name: String,
    val description: String,
    val language: String
)

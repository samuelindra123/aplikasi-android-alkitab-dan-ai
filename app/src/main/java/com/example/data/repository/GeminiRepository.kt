package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Verse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class GeminiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val geminiApiKey: String
        get() = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }

    private val openRouterApiKey: String
        get() {
            val key = try { BuildConfig.OPENROUTER_API_KEY } catch (e: Exception) { "" }
            return if (key.isNotEmpty() && key != "MY_OPENROUTER_API_KEY" && !key.contains("PLACEHOLDER")) {
                key
            } else {
                "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"
            }
        }

    private val openRouterModel: String = "openai/gpt-oss-120b:free"

    /**
     * Checks if the API key is available and not a placeholder.
     */
    fun isApiKeyAvailable(): Boolean {
        val hasGemini = geminiApiKey.isNotEmpty() && geminiApiKey != "MY_GEMINI_API_KEY" && !geminiApiKey.contains("PLACEHOLDER")
        val hasOpenRouter = openRouterApiKey.isNotEmpty() && openRouterApiKey != "MY_OPENROUTER_API_KEY" && !openRouterApiKey.contains("PLACEHOLDER")
        return hasGemini || hasOpenRouter
    }

    /**
     * Explains a specific Bible verse.
     */
    suspend fun explainVerse(book: String, chapter: Int, verseNumber: Int, text: String): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext "Kunci API AI belum dikonfigurasi. Harap masukkan kunci API di panel Rahasia/Secrets di AI Studio untuk mengaktifkan fitur penjelasan AI Alkitab."
        }

        val prompt = """
            Jelaskan makna rohani dan konteks teologis dari ayat Alkitab berikut secara ringkas, padat, mendalam, dan hangat dalam bahasa Indonesia.
            Berikan penjelasan singkat (maksimal 2 paragraf pendek) dan 1 refleksi praktis yang aplikatif untuk kehidupan sehari-hari.
            PENTING: Jawablah dengan sangat cepat, padat, dan langsung ke inti penjelasan.

            Ayat: $book $chapter:$verseNumber
            Teks: "$text"
        """.trimIndent()

        callAI(prompt, maxTokens = 400)
    }

    /**
     * Answers general questions about the Bible.
     */
    suspend fun askBibleAssistant(question: String, chatHistory: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext "Kunci API AI belum dikonfigurasi. Silakan tambahkan kunci API Anda di AI Studio Secrets."
        }

        // Format history
        val historyPrompt = StringBuilder()
        historyPrompt.append("Anda adalah 'Asisten Alkitab AI'—seorang pembimbing rohani dan teolog Kristen Protestan yang ramah, bijaksana, dan ringkas. ")
        historyPrompt.append("Bantu pengguna memahami Alkitab Terjemahan Baru (TB). ")
        historyPrompt.append("PENTING: Berikan jawaban yang SANGAT RINGKAS, PADAT, dan LANGSUNG KE INTI PERTANYAAN (maksimal 2-3 paragraf pendek atau poin-poin singkat). Hindari penjelasan yang terlalu panjang agar respon dapat dimuat dengan sangat cepat di layar handphone.\n\n")
        
        chatHistory.forEach { (role, msg) ->
            if (role == "user") {
                historyPrompt.append("Pengguna: $msg\n")
            } else {
                historyPrompt.append("Asisten: $msg\n")
            }
        }
        historyPrompt.append("Pengguna: $question\n")
        historyPrompt.append("Asisten:")

        callAI(historyPrompt.toString(), maxTokens = 450)
    }

    /**
     * Fetches verses for a chapter not stored locally in Terjemahan Baru.
     */
    suspend fun fetchChapterFromAI(book: String, chapter: Int): List<Verse> = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            throw IllegalStateException("API Key not set")
        }

        val prompt = """
            Retrieve all verses for the book "$book" chapter $chapter in the official Indonesian 'Terjemahan Baru' (TB) Protestant Bible translation.
            You MUST return ONLY a raw, pure JSON array of objects. Do not include markdown wraps like ```json or any explanations.
            Format of each object MUST be: {"number": Int, "text": "String"}
            Example output format:
            [
              {"number": 1, "text": "Pada mulanya..."},
              {"number": 2, "text": "Bumi belum..."}
            ]
            Ensure all verses for $book chapter $chapter are retrieved completely and text is strictly matching the Indonesian Terjemahan Baru (TB) version.
        """.trimIndent()

        val rawResponse = callAI(prompt)
        parseVersesFromJson(rawResponse)
    }

    private fun parseVersesFromJson(rawJson: String): List<Verse> {
        // Clean markdown backticks if the model returned them
        var cleaned = rawJson.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```")
        }
        cleaned = cleaned.trim()

        val list = mutableListOf<Verse>()
        try {
            val jsonArray = JSONArray(cleaned)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val number = obj.getInt("number")
                val text = obj.getString("text")
                list.add(Verse(number, text))
            }
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error parsing verses JSON: ${e.message}\nRaw: $rawJson")
            throw e
        }
        return list
    }

    private suspend fun callAI(prompt: String, maxTokens: Int? = null): String {
        val hasOpenRouter = openRouterApiKey.isNotEmpty() && openRouterApiKey != "MY_OPENROUTER_API_KEY" && !openRouterApiKey.contains("PLACEHOLDER")
        return if (hasOpenRouter) {
            callOpenRouter(prompt, maxTokens)
        } else {
            callGemini(prompt, maxTokens)
        }
    }

    private suspend fun callOpenRouter(prompt: String, maxTokens: Int? = null): String {
        val url = "https://openrouter.ai/api/v1/chat/completions"
        val model = openRouterModel

        val jsonBody = JSONObject().apply {
            put("model", model)
            if (maxTokens != null) {
                put("max_tokens", maxTokens)
            }
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $openRouterApiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://ai.studio/build")
            .addHeader("X-Title", "Alkitab TB AI")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e("GeminiRepository", "OpenRouter API Error: $errorBody")
                    return getFriendlyOpenRouterError(response.code, errorBody)
                }

                val responseString = response.body?.string() ?: return "Respons kosong dari server."
                val jsonResponse = JSONObject(responseString)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val message = firstChoice.optJSONObject("message")
                    if (message != null) {
                        return message.optString("content")
                    }
                }
                "Maaf, asisten OpenRouter tidak menghasilkan respons."
            }
        } catch (e: Exception) {
            Log.e("GeminiRepository", "OpenRouter Network exception: ${e.message}", e)
            "Koneksi gagal: ${e.localizedMessage}. Silakan periksa jaringan internet Anda."
        }
    }

    private fun getFriendlyOpenRouterError(statusCode: Int, errorBody: String): String {
        return try {
            val json = JSONObject(errorBody)
            val errorObj = json.optJSONObject("error")
            val rawMessage = errorObj?.optString("message") ?: ""
            val rawCode = errorObj?.optInt("code") ?: statusCode

            when {
                rawMessage.contains("limit", ignoreCase = true) ||
                rawMessage.contains("quota", ignoreCase = true) ||
                rawMessage.contains("credit", ignoreCase = true) ||
                rawMessage.contains("insufficient", ignoreCase = true) ||
                rawCode == 429 || statusCode == 429 -> {
                    "Maaf, kuota harian atau batas penggunaan gratis Asisten AI sedang penuh karena tingginya permintaan. Silakan tunggu beberapa saat atau coba lagi nanti."
                }
                rawMessage.contains("No endpoints found", ignoreCase = true) ||
                rawMessage.contains("model", ignoreCase = true) ||
                rawCode == 404 || statusCode == 404 -> {
                    "Model kecerdasan buatan (AI) gratis ini sedang sibuk atau tidak merespons. Kami otomatis mengoptimalkan koneksi Anda, silakan coba tanyakan kembali."
                }
                rawMessage.contains("API key", ignoreCase = true) ||
                rawMessage.contains("unauthorized", ignoreCase = true) ||
                rawCode == 401 || statusCode == 401 -> {
                    "Kunci API OpenRouter tidak terdeteksi atau tidak valid. Harap periksa kembali konfigurasi kunci API Anda di menu Pengaturan."
                }
                else -> {
                    "Asisten AI Alkitab sedang mengalami kendala jaringan atau server sedang padat. Silakan coba kirim pesan Anda kembali dalam beberapa saat."
                }
            }
        } catch (e: Exception) {
            "Asisten AI sedang sibuk atau mengalami kendala koneksi sementara. Silakan coba tanyakan kembali beberapa saat lagi."
        }
    }

    private suspend fun callGemini(prompt: String, maxTokens: Int? = null): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$geminiApiKey"

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            if (maxTokens != null) {
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", maxTokens)
                })
            }
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e("GeminiRepository", "API Error: $errorBody")
                    // If Gemini key fails, attempt to fall back to OpenRouter if we can
                    val hasOpenRouter = openRouterApiKey.isNotEmpty() && openRouterApiKey != "MY_OPENROUTER_API_KEY" && !openRouterApiKey.contains("PLACEHOLDER")
                    if (hasOpenRouter) {
                        Log.i("GeminiRepository", "Gemini failed, falling back to OpenRouter...")
                        return callOpenRouter(prompt, maxTokens)
                    }
                    return "Maaf, terjadi kesalahan saat menghubungi asisten AI Alkitab: $errorBody"
                }

                val responseString = response.body?.string() ?: return "Respons kosong dari server."
                val jsonResponse = JSONObject(responseString)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return parts.getJSONObject(0).optString("text")
                    }
                }
                "Maaf, asisten AI Alkitab tidak menghasilkan respons."
            }
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Network exception: ${e.message}", e)
            // Attempt fallback to OpenRouter on network exception
            val hasOpenRouter = openRouterApiKey.isNotEmpty() && openRouterApiKey != "MY_OPENROUTER_API_KEY" && !openRouterApiKey.contains("PLACEHOLDER")
            if (hasOpenRouter) {
                Log.i("GeminiRepository", "Gemini threw network exception, falling back to OpenRouter...")
                return callOpenRouter(prompt, maxTokens)
            }
            "Koneksi gagal: ${e.localizedMessage}. Pastikan Anda terhubung ke internet."
        }
    }
}

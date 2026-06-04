package com.caloriescount.app.data.remote

import com.caloriescount.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin client over Anthropic's Messages API used to estimate calories/protein from a food photo.
 * The model is instructed to return strict JSON matching [NutritionAnalysis].
 */
class ClaudeClient(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyzePhoto(
        apiKey: String,
        model: String,
        image: ImageUtils.EncodedImage
    ): AnalysisOutcome = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AnalysisOutcome.Error("No API key set. Add your Claude API key in Settings.")
        }

        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            put("system", SYSTEM_PROMPT)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", image.mediaType)
                                put("data", image.base64)
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", USER_PROMPT)
                        }
                    }
                }
            }
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext AnalysisOutcome.Error(parseApiError(body, response.code))
                }
                parseAnalysis(body)
            }
        } catch (e: IOException) {
            AnalysisOutcome.Error("Network error: ${e.message ?: "could not reach the service"}")
        } catch (e: Exception) {
            AnalysisOutcome.Error("Unexpected error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun parseAnalysis(body: String): AnalysisOutcome {
        val text = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["content"]?.jsonArray
                ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                ?.jsonObject?.get("text")?.jsonPrimitive?.content
        }.getOrNull()
            ?: return AnalysisOutcome.Error("Empty response from the model.")

        val jsonText = extractJsonObject(text)
            ?: return AnalysisOutcome.Error("Could not understand the model's response.")

        return runCatching {
            AnalysisOutcome.Success(json.decodeFromString<NutritionAnalysis>(jsonText))
        }.getOrElse {
            AnalysisOutcome.Error("Could not parse nutrition data: ${it.message}")
        }
    }

    private fun parseApiError(body: String, code: Int): String {
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]
                ?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        return when (code) {
            401 -> "Invalid API key. Check it in Settings."
            429 -> "Rate limited by the API. Try again shortly."
            else -> message ?: "API error (HTTP $code)."
        }
    }

    /** Pull the first balanced {...} block out of arbitrary text. */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        private const val SYSTEM_PROMPT =
            "You are a meticulous nutrition analyst. You estimate the calories and protein of " +
                "food shown in a photo as accurately as possible. Account for typical portion sizes, " +
                "cooking methods, oils, sauces and hidden ingredients. Break the dish into its " +
                "components. Respond with ONLY a JSON object, no markdown, no commentary."

        private const val USER_PROMPT =
            "Analyse the food in this photo. Identify each distinct component, estimate its portion, " +
                "calories (kcal) and protein (grams). Then provide the totals. " +
                "Respond strictly as JSON in this exact shape:\n" +
                "{\n" +
                "  \"meal_name\": \"short name of the dish\",\n" +
                "  \"items\": [\n" +
                "    {\"name\": \"component\", \"quantity\": \"e.g. 150 g / 1 cup\", \"calories\": 0, \"protein_g\": 0}\n" +
                "  ],\n" +
                "  \"total_calories\": 0,\n" +
                "  \"total_protein_g\": 0,\n" +
                "  \"notes\": \"brief assumptions or 'unsure' caveats\"\n" +
                "}\n" +
                "Use numbers (not strings) for calories and protein. If you are unsure, give your best " +
                "single estimate rather than a range."

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

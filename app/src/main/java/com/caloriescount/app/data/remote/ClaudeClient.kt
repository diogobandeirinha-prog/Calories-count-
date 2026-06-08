package com.caloriescount.app.data.remote

import com.caloriescount.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
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
 * Thin client over Anthropic's Messages API. Used to:
 *  - estimate calories + macros (protein/carbs/fats) + per-ingredient grams from a food photo, and
 *  - parse a spoken meal ("150 g grilled chicken and a cup of cooked rice") into the same structure.
 *
 * Both paths use:
 *  - Claude Opus 4.8 (most capable; best accuracy — configurable in Settings),
 *  - adaptive thinking + high effort (the model reasons about portions before answering),
 *  - structured outputs (`output_config.format`) so the response is guaranteed-valid JSON
 *    matching [NutritionAnalysis].
 */
class ClaudeClient(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Vision logging: identify ingredients + macros from a photo. */
    suspend fun analyzePhoto(
        apiKey: String,
        model: String,
        image: ImageUtils.EncodedImage
    ): AnalysisOutcome {
        val userContent = buildJsonObject {
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
                    put("text", PHOTO_PROMPT)
                }
            }
        }
        return send(apiKey, model, userContent)
    }

    /** Voice logging: parse a transcribed spoken meal into structured entries with macros. */
    suspend fun analyzeText(
        apiKey: String,
        model: String,
        transcript: String
    ): AnalysisOutcome {
        if (transcript.isBlank()) {
            return AnalysisOutcome.Error("Didn't catch that — please try speaking again.")
        }
        val userContent = buildJsonObject {
            put("role", "user")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "text")
                    put("text", "$DESCRIPTION_PROMPT\n\nMeal description:\n\"$transcript\"")
                }
            }
        }
        return send(apiKey, model, userContent)
    }

    private suspend fun send(
        apiKey: String,
        model: String,
        userMessage: JsonObject
    ): AnalysisOutcome = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AnalysisOutcome.Error("No API key set. Add your Claude API key in Settings.")
        }

        // Adaptive thinking + the effort parameter only exist on the newer "thinking"
        // models (Opus 4.6+/Sonnet 4.6); they 400 on Haiku 4.5 and older. Structured
        // outputs work everywhere, so they're always on.
        val richReasoning = supportsThinkingAndEffort(model)

        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("system", SYSTEM_PROMPT)
            if (richReasoning) {
                putJsonObject("thinking") { put("type", "adaptive") }
            }
            putJsonObject("output_config") {
                if (richReasoning) put("effort", "high")
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("schema", NUTRITION_SCHEMA)
                }
            }
            putJsonArray("messages") { add(userMessage) }
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("anthropic-beta", "structured-outputs-2025-11-13")
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
        // With structured outputs the first text block is guaranteed-valid JSON; we still
        // pull the first balanced object defensively so a stray prose wrapper can't break us.
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
            404 -> "Model not found. Check the model name in Settings."
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

    /** True for models that accept adaptive thinking + the `effort` parameter. */
    private fun supportsThinkingAndEffort(model: String): Boolean {
        val m = model.lowercase()
        return m.contains("opus-4-6") || m.contains("opus-4-7") || m.contains("opus-4-8") ||
            m.contains("sonnet-4-6")
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        private const val SYSTEM_PROMPT =
            "You are a meticulous nutrition analyst. You estimate the weight, calories and " +
                "macronutrients (protein, carbohydrates, fats) of food as accurately as possible. " +
                "Account for typical portion sizes, cooking methods, oils, sauces and hidden " +
                "ingredients. Break each dish into its individual components. Keep the macros " +
                "internally consistent (≈4 kcal/g protein, 4 kcal/g carbs, 9 kcal/g fat) so an " +
                "item's calories roughly match its macros."

        private const val PHOTO_PROMPT =
            "Analyse the food in this photo. Identify each distinct ingredient/component and " +
                "estimate its weight in grams, calories (kcal), protein, carbohydrates and fats " +
                "(all in grams). Then provide the meal totals and a short note about your key " +
                "assumptions. Give your single best estimate rather than a range."

        private const val DESCRIPTION_PROMPT =
            "Parse the following meal description (typed or spoken) into structured food entries. " +
                "For every food mentioned, resolve the stated portion (e.g. \"150 grams\", \"a cup of cooked " +
                "white rice\", \"two eggs\") into an estimated weight in grams, then estimate its " +
                "calories (kcal), protein, carbohydrates and fats (all in grams). Convert common " +
                "household measures to grams using standard references. Then provide the meal " +
                "totals and a short note. Give your single best estimate rather than a range."

        /** json_schema for structured outputs — every object sets additionalProperties:false. */
        private val NUTRITION_SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("meal_name") { put("type", "string") }
                putJsonObject("items") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("name") { put("type", "string") }
                            putJsonObject("quantity") { put("type", "string") }
                            putJsonObject("weight_grams") { put("type", "number") }
                            putJsonObject("calories") { put("type", "number") }
                            putJsonObject("protein_g") { put("type", "number") }
                            putJsonObject("carbs_g") { put("type", "number") }
                            putJsonObject("fats_g") { put("type", "number") }
                        }
                        putJsonArray("required") {
                            add("name"); add("quantity"); add("weight_grams"); add("calories")
                            add("protein_g"); add("carbs_g"); add("fats_g")
                        }
                        put("additionalProperties", false)
                    }
                }
                putJsonObject("total_calories") { put("type", "number") }
                putJsonObject("total_protein_g") { put("type", "number") }
                putJsonObject("total_carbs_g") { put("type", "number") }
                putJsonObject("total_fats_g") { put("type", "number") }
                putJsonObject("notes") { put("type", "string") }
            }
            putJsonArray("required") {
                add("meal_name"); add("items"); add("total_calories"); add("total_protein_g")
                add("total_carbs_g"); add("total_fats_g"); add("notes")
            }
            put("additionalProperties", false)
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}

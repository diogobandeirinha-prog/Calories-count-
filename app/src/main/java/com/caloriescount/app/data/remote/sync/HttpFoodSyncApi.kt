package com.caloriescount.app.data.remote.sync

import com.caloriescount.app.data.db.FoodEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * REST implementation of [RemoteFoodApi]:
 *   POST   {baseUrl}/entries        -> { "id": "..." }
 *   DELETE {baseUrl}/entries/{id}
 *
 * [baseUrlProvider] is read on every call so changing the server URL in Settings takes
 * effect immediately. A blank base URL means "no server configured" — sync stays a no-op
 * and rows simply remain PENDING until one is set.
 */
class HttpFoodSyncApi(
    private val baseUrlProvider: () -> String,
    private val apiKeyProvider: () -> String = { "" },
    private val httpClient: OkHttpClient = defaultHttpClient()
) : RemoteFoodApi {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val isConfigured: Boolean
        get() = baseUrlProvider().isNotBlank()

    override suspend fun upsert(entry: FoodEntryEntity): String = withContext(Dispatchers.IO) {
        val base = requireBase()
        val body = json.encodeToString(SyncEntryDto.from(entry)).toRequestBody(JSON_MEDIA)
        val request = authed(Request.Builder().url("$base/entries").post(body)).build()

        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Sync upsert failed (HTTP ${response.code})")
            }
            // Prefer a server id; fall back to the local id so we still mark it synced.
            runCatching { json.parseToJsonElement(payload).jsonObject["id"]?.jsonPrimitive?.content }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: entry.id.toString()
        }
    }

    override suspend fun delete(remoteId: String) = withContext(Dispatchers.IO) {
        val base = requireBase()
        val request = authed(Request.Builder().url("$base/entries/$remoteId").delete()).build()
        httpClient.newCall(request).execute().use { response ->
            // 404 means it's already gone — treat as success.
            if (!response.isSuccessful && response.code != 404) {
                throw IOException("Sync delete failed (HTTP ${response.code})")
            }
        }
    }

    private fun requireBase(): String =
        baseUrlProvider().trim().trimEnd('/').ifBlank { error("No sync server configured") }

    private fun authed(builder: Request.Builder): Request.Builder {
        val key = apiKeyProvider()
        if (key.isNotBlank()) builder.addHeader("Authorization", "Bearer $key")
        return builder.addHeader("Content-Type", "application/json")
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

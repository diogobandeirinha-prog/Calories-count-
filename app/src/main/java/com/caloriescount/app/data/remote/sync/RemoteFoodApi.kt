package com.caloriescount.app.data.remote.sync

import com.caloriescount.app.data.db.FoodEntryEntity

/**
 * Seam between the offline-first repository and whatever backend stores the canonical
 * macro history. The sync worker calls these; any thrown exception is treated as a
 * transient failure and the work is retried once connectivity is stable again.
 */
interface RemoteFoodApi {

    /** True only when a server is configured — lets the worker no-op cleanly otherwise. */
    val isConfigured: Boolean

    /** Create or update [entry] on the server; returns the server-assigned id. */
    suspend fun upsert(entry: FoodEntryEntity): String

    /** Delete the entry with the given server id. */
    suspend fun delete(remoteId: String)
}

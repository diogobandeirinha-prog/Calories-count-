package com.caloriescount.app.data.db

/** Local sync state of a row in the offline-first model. */
enum class SyncStatus {
    /** Created/updated/deleted locally, not yet pushed to the remote server. */
    PENDING,

    /** Confirmed present on the remote server. */
    SYNCED
}

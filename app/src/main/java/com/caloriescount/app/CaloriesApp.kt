package com.caloriescount.app

import android.app.Application
import com.caloriescount.app.data.db.AppDatabase
import com.caloriescount.app.data.prefs.SettingsRepository
import com.caloriescount.app.data.remote.ClaudeClient
import com.caloriescount.app.data.remote.sync.HttpFoodSyncApi
import com.caloriescount.app.data.remote.sync.RemoteFoodApi
import com.caloriescount.app.data.repository.FoodRepository
import com.caloriescount.app.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Lightweight service locator. Holds the singletons the screens + workers depend on. */
class CaloriesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {

    private val db = AppDatabase.get(app)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Latest settings cached synchronously so the sync API (called off the main thread,
    // possibly from a worker) can read the current server URL / key without suspending.
    @Volatile private var cachedSyncUrl: String = ""
    @Volatile private var cachedApiKey: String = ""

    val settingsRepository: SettingsRepository = SettingsRepository(app)
    val claudeClient: ClaudeClient = ClaudeClient()
    val syncManager: SyncManager = SyncManager(app)

    private val remoteApi: RemoteFoodApi = HttpFoodSyncApi(
        baseUrlProvider = { cachedSyncUrl },
        apiKeyProvider = { cachedApiKey }
    )

    val foodRepository: FoodRepository = FoodRepository(
        foodDao = db.foodEntryDao(),
        favoriteDao = db.favoriteFoodDao(),
        remoteApi = remoteApi,
        syncManager = syncManager
    )

    init {
        settingsRepository.settings
            .onEach {
                cachedSyncUrl = it.syncBaseUrl
                cachedApiKey = it.apiKey
            }
            .launchIn(appScope)
    }
}

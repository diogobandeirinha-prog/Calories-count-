package com.caloriescount.app

import android.app.Application
import com.caloriescount.app.data.db.AppDatabase
import com.caloriescount.app.data.prefs.SettingsRepository
import com.caloriescount.app.data.remote.ClaudeClient
import com.caloriescount.app.data.repository.FoodRepository

/** Lightweight service locator. Holds the singletons the screens depend on. */
class CaloriesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val foodRepository: FoodRepository = FoodRepository(AppDatabase.get(app).foodEntryDao())
    val settingsRepository: SettingsRepository = SettingsRepository(app)
    val claudeClient: ClaudeClient = ClaudeClient()
}

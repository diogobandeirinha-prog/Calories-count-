package com.caloriescount.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val calorieGoal: Double = 2000.0,
    val proteinGoal: Double = 120.0
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-4-6"
    }
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            apiKey = prefs[KEY_API] ?: "",
            model = prefs[KEY_MODEL] ?: Settings.DEFAULT_MODEL,
            calorieGoal = prefs[KEY_CAL_GOAL] ?: 2000.0,
            proteinGoal = prefs[KEY_PROTEIN_GOAL] ?: 120.0
        )
    }

    suspend fun setApiKey(value: String) = context.dataStore.edit { it[KEY_API] = value.trim() }

    suspend fun setModel(value: String) = context.dataStore.edit { it[KEY_MODEL] = value.trim() }

    suspend fun setGoals(calorieGoal: Double, proteinGoal: Double) = context.dataStore.edit {
        it[KEY_CAL_GOAL] = calorieGoal
        it[KEY_PROTEIN_GOAL] = proteinGoal
    }

    companion object {
        private val KEY_API = stringPreferencesKey("api_key")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_CAL_GOAL = doublePreferencesKey("calorie_goal")
        private val KEY_PROTEIN_GOAL = doublePreferencesKey("protein_goal")
    }
}

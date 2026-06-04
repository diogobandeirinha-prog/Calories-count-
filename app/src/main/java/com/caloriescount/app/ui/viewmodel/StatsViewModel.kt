package com.caloriescount.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloriescount.app.data.db.FoodEntryEntity
import com.caloriescount.app.data.prefs.Settings
import com.caloriescount.app.data.prefs.SettingsRepository
import com.caloriescount.app.data.repository.FoodRepository
import com.caloriescount.app.data.repository.NutritionStats
import com.caloriescount.app.data.repository.StatsSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatsViewModel(
    private val repository: FoodRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val snapshot: StateFlow<StatsSnapshot?> = repository.observeAll()
        .map { NutritionStats.compute(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun delete(entry: FoodEntryEntity) {
        viewModelScope.launch { repository.delete(entry) }
    }
}

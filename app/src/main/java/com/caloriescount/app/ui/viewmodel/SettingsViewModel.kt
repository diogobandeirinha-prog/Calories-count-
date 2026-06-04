package com.caloriescount.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloriescount.app.data.prefs.Settings
import com.caloriescount.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun saveApiKey(value: String) = viewModelScope.launch { repository.setApiKey(value) }
    fun saveModel(value: String) = viewModelScope.launch { repository.setModel(value) }
    fun saveGoals(calorieGoal: Double, proteinGoal: Double) =
        viewModelScope.launch { repository.setGoals(calorieGoal, proteinGoal) }
}

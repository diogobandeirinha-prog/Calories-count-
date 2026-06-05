package com.caloriescount.app.ui.viewmodel

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.caloriescount.app.CaloriesApp

private fun app(extras: androidx.lifecycle.viewmodel.CreationExtras): CaloriesApp =
    extras[APPLICATION_KEY] as CaloriesApp

val AppViewModelFactory = viewModelFactory {
    initializer {
        val container = app(this).container
        CaptureViewModel(container.foodRepository, container.settingsRepository, container.claudeClient)
    }
    initializer {
        val container = app(this).container
        StatsViewModel(
            container.foodRepository,
            container.workoutRepository,
            container.settingsRepository,
            container.profileRepository
        )
    }
    initializer {
        SettingsViewModel(app(this).container.settingsRepository)
    }
    initializer {
        val container = app(this).container
        OnboardingViewModel(container.profileRepository, container.settingsRepository)
    }
}

package com.caloriescount.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloriescount.app.data.model.UserProfile
import com.caloriescount.app.data.prefs.ProfileRepository
import com.caloriescount.app.data.prefs.ProfileState
import com.caloriescount.app.data.prefs.SettingsRepository
import com.caloriescount.app.domain.TargetCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** null while the persisted state is still loading (gates the first frame). */
    val state: StateFlow<ProfileState?> = profileRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Finish onboarding: persist the profile and write the computed baseline goals. */
    fun complete(profile: UserProfile) {
        viewModelScope.launch {
            val targets = TargetCalculator.compute(profile)
            profileRepository.save(profile, onboardingComplete = true)
            settingsRepository.setGoals(targets.calorieTarget, targets.proteinTarget)
        }
    }

    /** Re-open onboarding to edit the profile / recalculate targets. */
    fun editProfile() {
        viewModelScope.launch { profileRepository.setOnboardingComplete(false) }
    }
}

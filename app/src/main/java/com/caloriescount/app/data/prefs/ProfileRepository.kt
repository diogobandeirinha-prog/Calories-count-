package com.caloriescount.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.caloriescount.app.data.model.ActivityLevel
import com.caloriescount.app.data.model.FitnessGoal
import com.caloriescount.app.data.model.Sex
import com.caloriescount.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

/** Onboarding completion + the persisted [UserProfile]. */
data class ProfileState(
    val onboardingComplete: Boolean,
    val profile: UserProfile
)

class ProfileRepository(private val context: Context) {

    val state: Flow<ProfileState> = context.profileDataStore.data.map { prefs ->
        val defaults = UserProfile()
        ProfileState(
            onboardingComplete = prefs[KEY_COMPLETE] ?: false,
            profile = UserProfile(
                sex = prefs[KEY_SEX]?.toEnum(Sex.entries) ?: defaults.sex,
                age = prefs[KEY_AGE] ?: defaults.age,
                heightCm = prefs[KEY_HEIGHT] ?: defaults.heightCm,
                weightKg = prefs[KEY_WEIGHT] ?: defaults.weightKg,
                activityLevel = prefs[KEY_ACTIVITY]?.toEnum(ActivityLevel.entries) ?: defaults.activityLevel,
                goal = prefs[KEY_GOAL]?.toEnum(FitnessGoal.entries) ?: defaults.goal
            )
        )
    }

    suspend fun save(profile: UserProfile, onboardingComplete: Boolean = true) {
        context.profileDataStore.edit { prefs ->
            prefs[KEY_SEX] = profile.sex.name
            prefs[KEY_AGE] = profile.age
            prefs[KEY_HEIGHT] = profile.heightCm
            prefs[KEY_WEIGHT] = profile.weightKg
            prefs[KEY_ACTIVITY] = profile.activityLevel.name
            prefs[KEY_GOAL] = profile.goal.name
            prefs[KEY_COMPLETE] = onboardingComplete
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.profileDataStore.edit { it[KEY_COMPLETE] = complete }
    }

    private fun <T : Enum<T>> String.toEnum(values: List<T>): T? =
        values.firstOrNull { it.name == this }

    companion object {
        private val KEY_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_SEX = stringPreferencesKey("sex")
        private val KEY_AGE = intPreferencesKey("age")
        private val KEY_HEIGHT = doublePreferencesKey("height_cm")
        private val KEY_WEIGHT = doublePreferencesKey("weight_kg")
        private val KEY_ACTIVITY = stringPreferencesKey("activity_level")
        private val KEY_GOAL = stringPreferencesKey("goal")
    }
}

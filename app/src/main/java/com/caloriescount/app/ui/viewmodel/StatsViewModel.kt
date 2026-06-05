package com.caloriescount.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloriescount.app.data.db.FoodEntryEntity
import com.caloriescount.app.data.db.WorkoutEntity
import com.caloriescount.app.data.model.WorkoutIntensity
import com.caloriescount.app.data.prefs.ProfileRepository
import com.caloriescount.app.data.prefs.ProfileState
import com.caloriescount.app.data.prefs.Settings
import com.caloriescount.app.data.prefs.SettingsRepository
import com.caloriescount.app.data.repository.FoodRepository
import com.caloriescount.app.data.repository.NutritionStats
import com.caloriescount.app.data.repository.StatsSnapshot
import com.caloriescount.app.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's effective goals after exercise recalibration:
 * logging a workout adds its calories back to the allowance and its protein bonus
 * to the protein goal, so the "remaining" figures update dynamically for the day.
 */
data class DayGoals(
    val consumedCalories: Double,
    val consumedProtein: Double,
    val baseCalorieGoal: Double,
    val baseProteinGoal: Double,
    val exerciseCalories: Double,
    val exerciseProteinBonus: Double
) {
    val calorieGoal: Double get() = baseCalorieGoal + exerciseCalories
    val proteinGoal: Double get() = baseProteinGoal + exerciseProteinBonus
    val remainingCalories: Double get() = calorieGoal - consumedCalories
    val remainingProtein: Double get() = proteinGoal - consumedProtein
    val boostedByExercise: Boolean get() = exerciseCalories > 0 || exerciseProteinBonus > 0
}

class StatsViewModel(
    private val repository: FoodRepository,
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    val snapshot: StateFlow<StatsSnapshot?> = repository.observeAll()
        .map { NutritionStats.compute(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val pendingSyncCount: StateFlow<Int> = repository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val profile: StateFlow<ProfileState?> = profileRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Today's workouts, newest first. */
    val todayWorkouts: StateFlow<List<WorkoutEntity>> = workoutRepository.observeAll()
        .map { all -> all.filter { it.timestamp.toLocalDate() == LocalDate.now(zone) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Today's calorie/protein goals after exercise recalibration. */
    val today: StateFlow<DayGoals?> = combine(
        repository.observeAll(),
        workoutRepository.observeAll(),
        settingsRepository.settings
    ) { entries, workouts, settings ->
        val todayDate = LocalDate.now(zone)
        val todayEntries = entries.filter { it.timestamp.toLocalDate() == todayDate }
        val todayWorkouts = workouts.filter { it.timestamp.toLocalDate() == todayDate }
        DayGoals(
            consumedCalories = todayEntries.sumOf { it.totalCalories },
            consumedProtein = todayEntries.sumOf { it.totalProteinG },
            baseCalorieGoal = settings.calorieGoal,
            baseProteinGoal = settings.proteinGoal,
            exerciseCalories = todayWorkouts.sumOf { it.caloriesBurned },
            exerciseProteinBonus = todayWorkouts.sumOf { it.proteinBonusG }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(entry: FoodEntryEntity) {
        viewModelScope.launch { repository.delete(entry) }
    }

    fun logWorkout(name: String, durationMin: Int, intensity: WorkoutIntensity) {
        val weight = profile.value?.profile?.weightKg ?: 70.0
        viewModelScope.launch { workoutRepository.addWorkout(name, durationMin, intensity, weight) }
    }

    fun deleteWorkout(id: Long) {
        viewModelScope.launch { workoutRepository.delete(id) }
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}

package com.caloriescount.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caloriescount.app.data.model.FoodItem
import com.caloriescount.app.data.prefs.SettingsRepository
import com.caloriescount.app.data.remote.AnalysisOutcome
import com.caloriescount.app.data.remote.ClaudeClient
import com.caloriescount.app.data.remote.NutritionAnalysis
import com.caloriescount.app.data.repository.FoodRepository
import com.caloriescount.app.util.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A nutrition line the user can edit before saving. Numeric fields are text for editing. */
data class EditableItem(
    val name: String,
    val quantity: String,
    val weight: String,
    val calories: String,
    val protein: String,
    val carbs: String,
    val fats: String
) {
    val weightValue: Double get() = weight.parseNum()
    val caloriesValue: Double get() = calories.parseNum()
    val proteinValue: Double get() = protein.parseNum()
    val carbsValue: Double get() = carbs.parseNum()
    val fatsValue: Double get() = fats.parseNum()

    private fun String.parseNum(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
}

enum class CaptureStage { Empty, ReadyToAnalyze, Analyzing, Reviewing }

data class CaptureUiState(
    val stage: CaptureStage = CaptureStage.Empty,
    val mealName: String = "",
    val notes: String = "",
    val items: List<EditableItem> = emptyList(),
    val errorMessage: String? = null,
    val savedMessage: String? = null,
    val hasApiKey: Boolean = true,
    /** True while a voice entry is being reviewed (no photo to persist). */
    val fromVoice: Boolean = false
) {
    val totalCalories: Double get() = items.sumOf { it.caloriesValue }
    val totalProtein: Double get() = items.sumOf { it.proteinValue }
    val totalCarbs: Double get() = items.sumOf { it.carbsValue }
    val totalFats: Double get() = items.sumOf { it.fatsValue }
}

class CaptureViewModel(
    private val repository: FoodRepository,
    private val settings: SettingsRepository,
    private val claude: ClaudeClient
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    /** The downscaled bitmap kept around for analysis + thumbnail persistence (photo path only). */
    private var workingBitmap: Bitmap? = null

    fun onPhotoPicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bitmap = ImageUtils.loadBitmap(context, uri)
            if (bitmap == null) {
                _state.update { it.copy(errorMessage = "Could not read that image.") }
                return@launch
            }
            workingBitmap = bitmap
            _state.update {
                it.copy(
                    stage = CaptureStage.ReadyToAnalyze,
                    fromVoice = false,
                    errorMessage = null,
                    savedMessage = null
                )
            }
        }
    }

    /** Analyze the selected photo. */
    fun analyze() {
        val bitmap = workingBitmap ?: return
        runAnalysis(previousStage = CaptureStage.ReadyToAnalyze, fromVoice = false) { current ->
            claude.analyzePhoto(current.apiKey, current.model, ImageUtils.encode(bitmap))
        }
    }

    /** Analyze a transcribed spoken meal (voice logging). */
    fun analyzeVoice(transcript: String) {
        workingBitmap = null
        runAnalysis(previousStage = CaptureStage.Empty, fromVoice = true) { current ->
            claude.analyzeText(current.apiKey, current.model, transcript)
        }
    }

    private fun runAnalysis(
        previousStage: CaptureStage,
        fromVoice: Boolean,
        call: suspend (com.caloriescount.app.data.prefs.Settings) -> AnalysisOutcome
    ) {
        viewModelScope.launch {
            val current = settings.settings.first()
            if (!current.hasApiKey) {
                _state.update {
                    it.copy(
                        hasApiKey = false,
                        errorMessage = "Add your Claude API key in Settings to use AI logging."
                    )
                }
                return@launch
            }
            _state.update { it.copy(stage = CaptureStage.Analyzing, fromVoice = fromVoice, errorMessage = null) }

            when (val outcome = call(current)) {
                is AnalysisOutcome.Success -> _state.update { it.toReviewing(outcome.analysis) }
                is AnalysisOutcome.Error -> _state.update {
                    it.copy(stage = previousStage, errorMessage = outcome.message)
                }
            }
        }
    }

    private fun CaptureUiState.toReviewing(a: NutritionAnalysis): CaptureUiState = copy(
        stage = CaptureStage.Reviewing,
        mealName = a.mealName,
        notes = a.notes,
        items = a.items.map { item ->
            EditableItem(
                name = item.name,
                quantity = item.quantity,
                weight = num(item.weightGrams),
                calories = num(item.calories),
                protein = num(item.proteinG),
                carbs = num(item.carbsG),
                fats = num(item.fatsG)
            )
        }.ifEmpty {
            listOf(EditableItem("", "", num(0.0), num(a.totalCalories), num(a.totalProteinG), num(a.totalCarbsG), num(a.totalFatsG)))
        }
    )

    fun updateMealName(value: String) = _state.update { it.copy(mealName = value) }
    fun updateNotes(value: String) = _state.update { it.copy(notes = value) }

    fun updateItem(index: Int, item: EditableItem) = _state.update {
        it.copy(items = it.items.toMutableList().also { list -> list[index] = item })
    }

    fun addItem() = _state.update {
        it.copy(items = it.items + EditableItem("", "", "", "", "", "", ""))
    }

    fun removeItem(index: Int) = _state.update {
        it.copy(items = it.items.toMutableList().also { list -> list.removeAt(index) })
    }

    fun save(context: Context, onDone: () -> Unit) {
        val s = _state.value
        if (s.items.none { it.name.isNotBlank() || it.caloriesValue > 0 }) {
            _state.update { it.copy(errorMessage = "Add at least one food item before saving.") }
            return
        }
        viewModelScope.launch {
            val photoPath = workingBitmap?.let { ImageUtils.persistThumbnail(context, it) }
            val items = s.items.map {
                FoodItem(
                    name = it.name.ifBlank { "Item" },
                    quantity = it.quantity,
                    weightGrams = it.weightValue,
                    calories = it.caloriesValue,
                    proteinG = it.proteinValue,
                    carbsG = it.carbsValue,
                    fatsG = it.fatsValue
                )
            }
            repository.addEntry(
                timestamp = System.currentTimeMillis(),
                mealName = s.mealName,
                items = items,
                notes = s.notes,
                photoPath = photoPath
            )
            reset()
            _state.update { it.copy(savedMessage = "Meal saved.") }
            onDone()
        }
    }

    fun reset() {
        workingBitmap = null
        _state.value = CaptureUiState()
    }

    fun consumeMessages() = _state.update { it.copy(errorMessage = null, savedMessage = null) }

    private fun num(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
}

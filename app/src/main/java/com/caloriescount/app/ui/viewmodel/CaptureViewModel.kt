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
import com.caloriescount.app.data.repository.FoodRepository
import com.caloriescount.app.util.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A nutrition line the user can edit before saving. */
data class EditableItem(
    val name: String,
    val quantity: String,
    val calories: String,
    val protein: String
) {
    val caloriesValue: Double get() = calories.replace(',', '.').toDoubleOrNull() ?: 0.0
    val proteinValue: Double get() = protein.replace(',', '.').toDoubleOrNull() ?: 0.0
}

enum class CaptureStage { Empty, ReadyToAnalyze, Analyzing, Reviewing }

data class CaptureUiState(
    val stage: CaptureStage = CaptureStage.Empty,
    val mealName: String = "",
    val notes: String = "",
    val items: List<EditableItem> = emptyList(),
    val errorMessage: String? = null,
    val savedMessage: String? = null,
    val hasApiKey: Boolean = true
) {
    val totalCalories: Double get() = items.sumOf { it.caloriesValue }
    val totalProtein: Double get() = items.sumOf { it.proteinValue }
}

class CaptureViewModel(
    private val repository: FoodRepository,
    private val settings: SettingsRepository,
    private val claude: ClaudeClient
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    /** The downscaled bitmap kept around for analysis + thumbnail persistence. */
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
                it.copy(stage = CaptureStage.ReadyToAnalyze, errorMessage = null, savedMessage = null)
            }
        }
    }

    fun hasPhoto(): Boolean = workingBitmap != null

    fun analyze() {
        val bitmap = workingBitmap ?: return
        viewModelScope.launch {
            val current = settings.settings.first()
            if (!current.hasApiKey) {
                _state.update {
                    it.copy(
                        hasApiKey = false,
                        errorMessage = "Add your Claude API key in Settings to analyze photos."
                    )
                }
                return@launch
            }
            _state.update { it.copy(stage = CaptureStage.Analyzing, errorMessage = null) }

            val encoded = ImageUtils.encode(bitmap)
            when (val outcome = claude.analyzePhoto(current.apiKey, current.model, encoded)) {
                is AnalysisOutcome.Success -> {
                    val a = outcome.analysis
                    _state.update {
                        it.copy(
                            stage = CaptureStage.Reviewing,
                            mealName = a.mealName,
                            notes = a.notes,
                            items = a.items.map { item ->
                                EditableItem(
                                    name = item.name,
                                    quantity = item.quantity,
                                    calories = formatNumber(item.calories),
                                    protein = formatNumber(item.proteinG)
                                )
                            }.ifEmpty {
                                listOf(EditableItem("", "", formatNumber(a.totalCalories), formatNumber(a.totalProteinG)))
                            }
                        )
                    }
                }
                is AnalysisOutcome.Error -> _state.update {
                    it.copy(stage = CaptureStage.ReadyToAnalyze, errorMessage = outcome.message)
                }
            }
        }
    }

    fun updateMealName(value: String) = _state.update { it.copy(mealName = value) }
    fun updateNotes(value: String) = _state.update { it.copy(notes = value) }

    fun updateItem(index: Int, item: EditableItem) = _state.update {
        it.copy(items = it.items.toMutableList().also { list -> list[index] = item })
    }

    fun addItem() = _state.update {
        it.copy(items = it.items + EditableItem("", "", "", ""))
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
                    calories = it.caloriesValue,
                    proteinG = it.proteinValue
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

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
}

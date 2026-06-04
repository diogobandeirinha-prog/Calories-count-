package com.caloriescount.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caloriescount.app.data.db.FoodEntryEntity
import com.caloriescount.app.data.db.WorkoutEntity
import com.caloriescount.app.data.model.WorkoutIntensity
import com.caloriescount.app.data.repository.PeriodSummary
import com.caloriescount.app.data.repository.StatsSnapshot
import com.caloriescount.app.ui.components.asGrams
import com.caloriescount.app.ui.components.asKcal
import com.caloriescount.app.ui.theme.CalorieColor
import com.caloriescount.app.ui.theme.ProteinColor
import com.caloriescount.app.ui.viewmodel.AppViewModelFactory
import com.caloriescount.app.ui.viewmodel.DayGoals
import com.caloriescount.app.ui.viewmodel.StatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class Period(val label: String) { Daily("Daily"), Weekly("Weekly"), Monthly("Monthly") }

@Composable
fun HistoryScreen(viewModel: StatsViewModel = viewModel(factory = AppViewModelFactory)) {
    val snapshot by viewModel.snapshot.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val pendingSync by viewModel.pendingSyncCount.collectAsState()
    val today by viewModel.today.collectAsState()
    val todayWorkouts by viewModel.todayWorkouts.collectAsState()
    var period by remember { mutableStateOf(Period.Daily) }
    var showWorkoutDialog by remember { mutableStateOf(false) }

    if (showWorkoutDialog) {
        LogWorkoutDialog(
            onDismiss = { showWorkoutDialog = false },
            onConfirm = { name, minutes, intensity ->
                viewModel.logWorkout(name, minutes, intensity)
                showWorkoutDialog = false
            }
        )
    }

    val summaries = snapshot?.let { periodSummaries(it, period) } ?: emptyList()

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Your nutrition", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (pendingSync > 0) {
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        if (settings.syncEnabled) "$pendingSync waiting to sync"
                        else "$pendingSync saved locally · set a sync server in Settings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        today?.let { goals -> item { TodayCard(goals) } }

        item {
            WorkoutSection(
                workouts = todayWorkouts,
                onLog = { showWorkoutDialog = true },
                onDelete = viewModel::deleteWorkout
            )
        }

        item {
            TabRow(selectedTabIndex = period.ordinal) {
                Period.entries.forEach { p ->
                    Tab(selected = period == p, onClick = { period = p }, text = { Text(p.label) })
                }
            }
        }

        if (summaries.isEmpty()) {
            item {
                Text(
                    "No meals logged yet. Add your first meal from the Add tab.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(summaries, key = { it.key }) { summary ->
                PeriodCard(summary, onDelete = viewModel::delete)
            }
        }
    }
}

@Composable
private fun TodayCard(goals: DayGoals) {
    val remaining = goals.remainingCalories.roundToInt()
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Text(
                if (remaining >= 0) "$remaining kcal left" else "${-remaining} kcal over",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (remaining >= 0) CalorieColor else MaterialTheme.colorScheme.error
            )
            Text(
                "${goals.consumedCalories.roundToInt()} eaten of ${goals.calorieGoal.roundToInt()} kcal goal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GoalBar(goals.consumedCalories, goals.calorieGoal, CalorieColor)

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Protein", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${goals.consumedProtein.roundToInt()} / ${goals.proteinGoal.roundToInt()} g",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GoalBar(goals.consumedProtein, goals.proteinGoal, ProteinColor)

            if (goals.boostedByExercise) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Recalibrated for today's exercise: " +
                            "+${goals.exerciseCalories.roundToInt()} kcal" +
                            if (goals.exerciseProteinBonus > 0) ", +${goals.exerciseProteinBonus.roundToInt()} g protein" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalBar(value: Double, goal: Double, color: Color) {
    val fraction = if (goal > 0) (value / goal).coerceIn(0.0, 1.0).toFloat() else 0f
    LinearProgressIndicator(
        progress = { fraction },
        color = color,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    )
}

@Composable
private fun WorkoutSection(
    workouts: List<WorkoutEntity>,
    onLog: () -> Unit,
    onDelete: (Long) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Today's workouts", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = onLog) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Log", modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (workouts.isEmpty()) {
                Text(
                    "No workouts today. Log one to add the burned calories back and boost your protein goal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                workouts.forEach { w ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(w.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${w.intensity.lowercase().replaceFirstChar { it.uppercase() }} · " +
                                    "${w.durationMin} min · +${w.caloriesBurned.roundToInt()} kcal" +
                                    if (w.proteinBonusG > 0) ", +${w.proteinBonusG.roundToInt()} g protein" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDelete(w.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete workout")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogWorkoutDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, minutes: Int, intensity: WorkoutIntensity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("45") }
    var intensity by remember { mutableStateOf(WorkoutIntensity.INTENSE) }
    val minutes = duration.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, minutes, intensity) },
                enabled = minutes > 0
            ) { Text("Log workout") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Log a workout") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    label = { Text("Duration (min)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    "Intensity",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutIntensity.entries.forEach { i ->
                        FilterChip(
                            selected = i == intensity,
                            onClick = { intensity = i },
                            label = { Text(i.label) }
                        )
                    }
                }
            }
        }
    )
}

private fun periodSummaries(snap: StatsSnapshot, period: Period): List<PeriodSummary> = when (period) {
    Period.Daily -> snap.byDay
    Period.Weekly -> snap.byWeek
    Period.Monthly -> snap.byMonth
}

@Composable
private fun PeriodCard(summary: PeriodSummary, onDelete: (FoodEntryEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(summary.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        summary.totals.calories.asKcal(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "P ${summary.totals.protein.asGrams()} • " +
                            "C ${summary.totals.carbs.asGrams()} • F ${summary.totals.fats.asGrams()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                summary.entries.forEach { entry ->
                    MealRow(entry, onDelete)
                }
            }
        }
    }
}

@Composable
private fun MealRow(entry: FoodEntryEntity, onDelete: (FoodEntryEntity) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.mealName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${timeFormat.format(Date(entry.timestamp))}  •  ${entry.totalCalories.asKcal()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "P ${entry.totalProteinG.asGrams()} • C ${entry.totalCarbsG.asGrams()} • " +
                    "F ${entry.totalFatsG.asGrams()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.items.size > 1) {
                Text(
                    entry.items.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { onDelete(entry) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete meal")
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

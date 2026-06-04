package com.caloriescount.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caloriescount.app.data.db.FoodEntryEntity
import com.caloriescount.app.data.repository.PeriodSummary
import com.caloriescount.app.data.repository.StatsSnapshot
import com.caloriescount.app.ui.components.TotalsCard
import com.caloriescount.app.ui.components.asGrams
import com.caloriescount.app.ui.components.asKcal
import com.caloriescount.app.ui.viewmodel.AppViewModelFactory
import com.caloriescount.app.ui.viewmodel.StatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Period(val label: String) { Daily("Daily"), Weekly("Weekly"), Monthly("Monthly") }

@Composable
fun HistoryScreen(viewModel: StatsViewModel = viewModel(factory = AppViewModelFactory)) {
    val snapshot by viewModel.snapshot.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var period by remember { mutableStateOf(Period.Daily) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Your nutrition", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        val snap = snapshot
        if (snap == null) {
            Text(
                "Loading…",
                modifier = Modifier.padding(top = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        if (snap.byDay.isEmpty()) {
            Text(
                "No meals logged yet. Add your first photo from the Add tab.",
                modifier = Modifier.padding(top = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        TotalsCard(
            title = "Today",
            totals = snap.today,
            modifier = Modifier.padding(vertical = 12.dp),
            calorieGoal = settings.calorieGoal,
            proteinGoal = settings.proteinGoal
        )

        TabRow(selectedTabIndex = period.ordinal) {
            Period.entries.forEach { p ->
                Tab(
                    selected = period == p,
                    onClick = { period = p },
                    text = { Text(p.label) }
                )
            }
        }

        val summaries = periodSummaries(snap, period)
        LazyColumn(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(summaries, key = { it.key }) { summary ->
                PeriodCard(summary, onDelete = viewModel::delete)
            }
        }
    }
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

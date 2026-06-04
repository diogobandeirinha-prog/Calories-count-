package com.caloriescount.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caloriescount.app.data.repository.Totals
import com.caloriescount.app.ui.theme.CalorieColor
import com.caloriescount.app.ui.theme.ProteinColor
import kotlin.math.roundToInt

fun Double.asKcal(): String = "${roundToInt()} kcal"
fun Double.asGrams(): String = "${roundToInt()} g"

@Composable
fun TotalsCard(
    title: String,
    totals: Totals,
    modifier: Modifier = Modifier,
    calorieGoal: Double? = null,
    proteinGoal: Double? = null
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${totals.entryCount} ${if (totals.entryCount == 1) "meal" else "meals"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Metric("Calories", totals.calories.asKcal(), CalorieColor)
                Metric("Protein", totals.protein.asGrams(), ProteinColor)
            }
            if (calorieGoal != null && calorieGoal > 0) {
                GoalBar("Calorie goal", totals.calories, calorieGoal, CalorieColor)
            }
            if (proteinGoal != null && proteinGoal > 0) {
                GoalBar("Protein goal", totals.protein, proteinGoal, ProteinColor)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GoalBar(label: String, value: Double, goal: Double, color: androidx.compose.ui.graphics.Color) {
    val fraction = (value / goal).coerceIn(0.0, 1.0).toFloat()
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                "${value.roundToInt()} / ${goal.roundToInt()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}

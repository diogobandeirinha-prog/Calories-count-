package com.caloriescount.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caloriescount.app.data.model.ActivityLevel
import com.caloriescount.app.data.model.FitnessGoal
import com.caloriescount.app.data.model.Sex
import com.caloriescount.app.data.model.UserProfile
import com.caloriescount.app.domain.TargetCalculator
import com.caloriescount.app.ui.viewmodel.AppViewModelFactory
import com.caloriescount.app.ui.viewmodel.OnboardingViewModel
import kotlin.math.roundToInt

private const val STEP_COUNT = 4

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = AppViewModelFactory)
) {
    val loaded by viewModel.state.collectAsState()

    var step by remember { mutableStateOf(0) }
    var goal by remember { mutableStateOf(FitnessGoal.MAINTENANCE) }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var activity by remember { mutableStateOf(ActivityLevel.MODERATE) }
    var ageText by remember { mutableStateOf("30") }
    var heightText by remember { mutableStateOf("175") }
    var weightText by remember { mutableStateOf("75") }
    var seeded by remember { mutableStateOf(false) }

    // Prefill from the saved profile when re-editing.
    LaunchedEffect(loaded) {
        val s = loaded ?: return@LaunchedEffect
        if (!seeded && s.onboardingComplete) {
            goal = s.profile.goal
            sex = s.profile.sex
            activity = s.profile.activityLevel
            ageText = s.profile.age.toString()
            heightText = s.profile.heightCm.roundToInt().toString()
            weightText = s.profile.weightKg.roundToInt().toString()
        }
        seeded = true
    }

    val profile = UserProfile(
        sex = sex,
        age = ageText.toIntOrNull() ?: 0,
        heightCm = heightText.replace(',', '.').toDoubleOrNull() ?: 0.0,
        weightKg = weightText.replace(',', '.').toDoubleOrNull() ?: 0.0,
        activityLevel = activity,
        goal = goal
    )

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Set up your plan",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Step ${step + 1} of $STEP_COUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            when (step) {
                0 -> GoalStep(goal) { goal = it }
                1 -> BodyStep(
                    sex = sex, onSex = { sex = it },
                    ageText = ageText, onAge = { ageText = it.digits() },
                    heightText = heightText, onHeight = { heightText = it.digits() },
                    weightText = weightText, onWeight = { weightText = it.digits() }
                )
                2 -> ActivityStep(activity) { activity = it }
                3 -> ReviewStep(profile)
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) {
                        Text("Back")
                    }
                }
                val canAdvance = if (step == 1) profile.isValid else true
                Button(
                    onClick = {
                        if (step < STEP_COUNT - 1) {
                            step++
                        } else {
                            viewModel.complete(profile)
                            onComplete()
                        }
                    },
                    enabled = canAdvance,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (step < STEP_COUNT - 1) "Next" else "Finish")
                }
            }
        }
    }
}

@Composable
private fun GoalStep(selected: FitnessGoal, onSelect: (FitnessGoal) -> Unit) {
    Text("What's your goal?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
    FitnessGoal.entries.forEach { g ->
        SelectableCard(
            title = g.label,
            subtitle = g.description,
            selected = g == selected,
            onClick = { onSelect(g) }
        )
    }
}

@Composable
private fun BodyStep(
    sex: Sex, onSex: (Sex) -> Unit,
    ageText: String, onAge: (String) -> Unit,
    heightText: String, onHeight: (String) -> Unit,
    weightText: String, onWeight: (String) -> Unit
) {
    Text("About you", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { s ->
            FilterChip(selected = s == sex, onClick = { onSex(s) }, label = { Text(s.label) })
        }
    }
    OutlinedTextField(
        value = ageText, onValueChange = onAge, label = { Text("Age (years)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    )
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = heightText, onValueChange = onHeight, label = { Text("Height (cm)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = weightText, onValueChange = onWeight, label = { Text("Weight (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActivityStep(selected: ActivityLevel, onSelect: (ActivityLevel) -> Unit) {
    Text("How active are you?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
    ActivityLevel.entries.forEach { a ->
        SelectableCard(
            title = a.label,
            subtitle = a.description,
            selected = a == selected,
            onClick = { onSelect(a) }
        )
    }
}

@Composable
private fun ReviewStep(profile: UserProfile) {
    val targets = TargetCalculator.compute(profile)
    Text("Your baseline plan", style = MaterialTheme.typography.titleMedium)
    Text(
        "Calculated with the Mifflin-St Jeor equation for your ${profile.goal.label.lowercase()} goal.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewRow("Daily calories", "${targets.calorieTargetRounded} kcal", highlight = true)
            ReviewRow("Daily protein", "${targets.proteinTargetRounded} g", highlight = true)
            ReviewRow("BMR", "${targets.bmr.roundToInt()} kcal")
            ReviewRow("Maintenance (TDEE)", "${targets.tdee.roundToInt()} kcal")
        }
    }
    Text(
        "Log an intense workout any day and the app adds the burned calories back and " +
            "bumps your protein goal for that day.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun ReviewRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SelectableCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .selectable(selected = selected, onClick = onClick),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun String.digits(): String = filter { it.isDigit() }

package com.caloriescount.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.caloriescount.app.ui.components.asGrams
import com.caloriescount.app.ui.components.asKcal
import com.caloriescount.app.data.db.FavoriteFoodEntity
import com.caloriescount.app.ui.viewmodel.AppViewModelFactory
import com.caloriescount.app.ui.viewmodel.CaptureStage
import com.caloriescount.app.ui.viewmodel.CaptureViewModel
import com.caloriescount.app.ui.viewmodel.EditableItem
import com.caloriescount.app.util.ImageUtils

@Composable
fun CaptureScreen(
    onSaved: () -> Unit,
    viewModel: CaptureViewModel = viewModel(factory = AppViewModelFactory)
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            previewUri = uri
            viewModel.onPhotoPicked(context, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) {
            previewUri = uri
            viewModel.onPhotoPicked(context, uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (_, uri) = ImageUtils.newCameraOutput(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Voice logging: launch the system speech recognizer, then parse the transcript.
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            previewUri = null
            viewModel.analyzeDescription(transcript)
        }
    }
    val launchSpeech = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you ate, e.g. '150 g grilled chicken and a cup of rice'")
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure { /* surfaced below via the snackbar if no recognizer */ }
            .isSuccess
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && !launchSpeech()) {
            // ActivityNotFound etc. — no speech recognizer available
        }
    }

    var showTypeDialog by remember { mutableStateOf(false) }
    if (showTypeDialog) {
        TypeMealDialog(
            onDismiss = { showTypeDialog = false },
            onConfirm = { text ->
                previewUri = null
                viewModel.analyzeDescription(text)
                showTypeDialog = false
            }
        )
    }

    LaunchedEffect(state.errorMessage, state.savedMessage) {
        state.errorMessage?.let { snackbarHost.showSnackbar(it) }
        state.savedMessage?.let { snackbarHost.showSnackbar(it) }
        if (state.errorMessage != null || state.savedMessage != null) viewModel.consumeMessages()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Log a meal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            if (!state.fromText) {
                PhotoArea(previewUri = previewUri)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Camera")
                }
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Gallery")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Speak")
                }
                OutlinedButton(
                    onClick = { showTypeDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Type")
                }
            }

            when (state.stage) {
                CaptureStage.ReadyToAnalyze -> Button(
                    onClick = viewModel::analyze,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Analyze photo") }

                CaptureStage.Analyzing -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (state.fromText) "Parsing your meal…" else "Estimating calories & macros…")
                }

                CaptureStage.Reviewing -> ReviewSection(state, viewModel, onSaved)
                CaptureStage.Empty -> {
                    Text(
                        "Snap a photo of your dish, or tap “Speak” / “Type” to describe what you ate " +
                            "(e.g. list every ingredient and amount). The app estimates each ingredient's " +
                            "weight, calories and macros, and you can fine-tune the numbers before saving.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (favorites.isNotEmpty()) {
                        FrequentFoods(favorites, onPick = viewModel::startFromFavorite)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeMealDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("Analyze") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Type your meal") },
        text = {
            Column {
                Text(
                    "List the ingredients and amounts, e.g. \"150 g grilled chicken breast, " +
                        "1 cup cooked white rice, 1 tbsp olive oil\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Ingredients") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrequentFoods(
    favorites: List<FavoriteFoodEntity>,
    onPick: (FavoriteFoodEntity) -> Unit
) {
    Column {
        Text(
            "Frequent foods",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            favorites.forEach { fav ->
                SuggestionChip(
                    onClick = { onPick(fav) },
                    label = { Text("${fav.name} · ${fav.calories.asKcal()}") }
                )
            }
        }
    }
}

@Composable
private fun PhotoArea(previewUri: Uri?) {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (previewUri != null) {
                AsyncImage(
                    model = previewUri,
                    contentDescription = "Selected meal photo",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Restaurant,
                        contentDescription = null,
                        modifier = Modifier.height(48.dp).width(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No photo yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReviewSection(
    state: com.caloriescount.app.ui.viewmodel.CaptureUiState,
    viewModel: CaptureViewModel,
    onSaved: () -> Unit
) {
    val context = LocalContext.current

    OutlinedTextField(
        value = state.mealName,
        onValueChange = viewModel::updateMealName,
        label = { Text("Meal name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    state.items.forEachIndexed { index, item ->
        ItemEditor(
            item = item,
            onChange = { viewModel.updateItem(index, it) },
            onRemove = { viewModel.removeItem(index) }
        )
    }

    TextButton(onClick = viewModel::addItem) {
        Icon(Icons.Filled.Add, null)
        Spacer(Modifier.width(4.dp))
        Text("Add item")
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Total", fontWeight = FontWeight.Bold)
            Text(
                "${state.totalCalories.asKcal()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Protein ${state.totalProtein.asGrams()}  •  " +
                    "Carbs ${state.totalCarbs.asGrams()}  •  Fats ${state.totalFats.asGrams()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (state.notes.isNotBlank()) {
        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::updateNotes,
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth()
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = viewModel::reset, modifier = Modifier.weight(1f)) {
            Text("Discard")
        }
        Button(
            onClick = { viewModel.save(context, onSaved) },
            modifier = Modifier.weight(1f)
        ) { Text("Save meal") }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ItemEditor(
    item: EditableItem,
    onChange: (EditableItem) -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { onChange(item.copy(name = it)) },
                    label = { Text("Food") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color(0xFFC62828))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = item.quantity,
                    onValueChange = { onChange(item.copy(quantity = it)) },
                    label = { Text("Quantity") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = item.weight,
                    label = "Weight (g)",
                    onValueChange = { onChange(item.copy(weight = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NumberField(
                    value = item.calories,
                    label = "Calories",
                    onValueChange = { onChange(item.copy(calories = it)) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = item.protein,
                    label = "Protein (g)",
                    onValueChange = { onChange(item.copy(protein = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NumberField(
                    value = item.carbs,
                    label = "Carbs (g)",
                    onValueChange = { onChange(item.copy(carbs = it)) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = item.fats,
                    label = "Fats (g)",
                    onValueChange = { onChange(item.copy(fats = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

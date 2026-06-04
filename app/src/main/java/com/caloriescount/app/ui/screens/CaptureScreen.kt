package com.caloriescount.app.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.caloriescount.app.ui.components.asGrams
import com.caloriescount.app.ui.components.asKcal
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

            PhotoArea(previewUri = previewUri, stage = state.stage)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
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
                    Text("Estimating calories & protein…")
                }

                CaptureStage.Reviewing -> ReviewSection(state, viewModel, onSaved)
                CaptureStage.Empty -> Text(
                    "Take or pick a photo of your dish. The app estimates the calories and protein, " +
                        "and you can fine-tune the numbers before saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PhotoArea(previewUri: Uri?, stage: CaptureStage) {
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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Total", fontWeight = FontWeight.Bold)
            Text(
                "${state.totalCalories.asKcal()}  •  ${state.totalProtein.asGrams()} protein",
                fontWeight = FontWeight.Bold
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
            OutlinedTextField(
                value = item.quantity,
                onValueChange = { onChange(item.copy(quantity = it)) },
                label = { Text("Quantity (e.g. 150 g)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = item.calories,
                    onValueChange = { onChange(item.copy(calories = it.filter { c -> c.isDigit() || c == '.' || c == ',' })) },
                    label = { Text("Calories") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.protein,
                    onValueChange = { onChange(item.copy(protein = it.filter { c -> c.isDigit() || c == '.' || c == ',' })) },
                    label = { Text("Protein (g)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

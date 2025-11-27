package co.kr.qgen.feature.generation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.model.Difficulty
import co.kr.qgen.core.model.Language
import co.kr.qgen.core.model.TopicPreset
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerationScreen(
    viewModel: GenerationViewModel = koinViewModel(),
    onNavigateToQuiz: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is GenerationSideEffect.NavigateToQuiz -> onNavigateToQuiz()
                is GenerationSideEffect.ShowError -> onShowMessage(effect.message)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Title
        Text(
            text = "QGen – 문제 생성",
            style = MaterialTheme.typography.headlineMedium
        )

        // Topic Input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("주제", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = uiState.topic,
                onValueChange = { viewModel.onTopicChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("예: Android Coroutines") },
                enabled = !uiState.isLoading
            )

            // Topic Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopicPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = uiState.selectedPreset == preset,
                        onClick = { viewModel.onPresetSelected(preset) },
                        label = { Text(preset.displayName) },
                        enabled = !uiState.isLoading
                    )
                }
            }
        }

        // Difficulty
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("난이도", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Difficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = uiState.difficulty == difficulty,
                        onClick = { viewModel.onDifficultyChanged(difficulty) },
                        label = { Text(difficulty.displayName) },
                        enabled = !uiState.isLoading
                    )
                }
            }
        }

        // Count
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("문항 수", style = MaterialTheme.typography.titleMedium)
                Text("${uiState.count}문항", style = MaterialTheme.typography.bodyLarge)
            }
            Slider(
                value = uiState.count.toFloat(),
                onValueChange = { viewModel.onCountChanged(it.toInt()) },
                valueRange = 1f..50f,
                steps = 48,
                enabled = !uiState.isLoading
            )
        }

        // Choice Count
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("보기 개수", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.choiceCount == 4,
                        onClick = { viewModel.onChoiceCountChanged(4) },
                        enabled = !uiState.isLoading
                    )
                    Text("4지선다")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.choiceCount == 5,
                        onClick = { viewModel.onChoiceCountChanged(5) },
                        enabled = !uiState.isLoading
                    )
                    Text("5지선다")
                }
            }
        }

        // Language
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("문제 언어", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Language.entries.forEach { language ->
                    FilterChip(
                        selected = uiState.language == language,
                        onClick = { viewModel.onLanguageChanged(language) },
                        label = { Text(language.displayName) },
                        enabled = !uiState.isLoading
                    )
                }
            }
        }

        // Mock API Toggle (Debug)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mock API 사용 (개발용)", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = uiState.useMockApi,
                onCheckedChange = { viewModel.onMockApiToggled(it) },
                enabled = !uiState.isLoading
            )
        }

        // Generate Button
        Button(
            onClick = { viewModel.onGenerateClicked() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !uiState.isLoading && uiState.topic.isNotBlank()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (uiState.isLoading) "생성 중..." else "문제 생성하기")
        }

        // Error Message
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

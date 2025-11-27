package co.kr.qgen.feature.generation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.model.Difficulty
import co.kr.qgen.core.model.Language
import co.kr.qgen.core.model.TopicPreset
import co.kr.qgen.core.ui.theme.QGenExamTheme
import co.kr.qgen.core.ui.theme.examPaperBackground
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
            .examPaperBackground()
            .verticalScroll(rememberScrollState())
            .padding(QGenExamTheme.Dimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(QGenExamTheme.Dimensions.sectionSpacing)
    ) {
        // Title
        Text(
            text = "QGen",
            style = QGenExamTheme.Typography.screenTitleStyle
        )
        
        Text(
            text = "문제 생성",
            style = QGenExamTheme.Typography.sectionHeaderStyle
        )

        // Topic Input
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "주제",
                style = QGenExamTheme.Typography.labelTextStyle
            )
            OutlinedTextField(
                value = uiState.topic,
                onValueChange = { viewModel.onTopicChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { 
                    Text(
                        "예: Android Coroutines",
                        style = QGenExamTheme.Typography.bodyTextStyle,
                        color = QGenExamTheme.Colors.TextTertiary
                    )
                },
                enabled = !uiState.isLoading,
                textStyle = QGenExamTheme.Typography.bodyTextStyle,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = QGenExamTheme.Colors.ExamBorder,
                    unfocusedBorderColor = QGenExamTheme.Colors.DividerColor,
                    focusedContainerColor = QGenExamTheme.Colors.CardBackground,
                    unfocusedContainerColor = QGenExamTheme.Colors.CardBackground
                )
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
                        label = { 
                            Text(
                                preset.displayName,
                                style = QGenExamTheme.Typography.smallTextStyle
                            )
                        },
                        enabled = !uiState.isLoading,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = QGenExamTheme.Colors.CardBackground,
                            selectedContainerColor = QGenExamTheme.Colors.ExamBorder,
                            labelColor = QGenExamTheme.Colors.TextPrimary,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = !uiState.isLoading,
                            selected = uiState.selectedPreset == preset,
                            borderColor = QGenExamTheme.Colors.DividerColor,
                            selectedBorderColor = QGenExamTheme.Colors.ExamBorder
                        )
                    )
                }
            }
        }

        // Difficulty
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("난이도", style = QGenExamTheme.Typography.labelTextStyle)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Difficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = uiState.difficulty == difficulty,
                        onClick = { viewModel.onDifficultyChanged(difficulty) },
                        label = { 
                            Text(
                                difficulty.displayName,
                                style = QGenExamTheme.Typography.smallTextStyle
                            )
                        },
                        enabled = !uiState.isLoading,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = QGenExamTheme.Colors.CardBackground,
                            selectedContainerColor = QGenExamTheme.Colors.ExamBorder,
                            labelColor = QGenExamTheme.Colors.TextPrimary,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = !uiState.isLoading,
                            selected = uiState.difficulty == difficulty,
                            borderColor = QGenExamTheme.Colors.DividerColor,
                            selectedBorderColor = QGenExamTheme.Colors.ExamBorder
                        )
                    )
                }
            }
        }

        // Count
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("문항 수", style = QGenExamTheme.Typography.labelTextStyle)
                Text(
                    "${uiState.count}문항",
                    style = QGenExamTheme.Typography.bodyTextStyle
                )
            }
            Slider(
                value = uiState.count.toFloat(),
                onValueChange = { viewModel.onCountChanged(it.toInt()) },
                valueRange = 1f..50f,
                steps = 48,
                enabled = !uiState.isLoading,
                colors = SliderDefaults.colors(
                    thumbColor = QGenExamTheme.Colors.ExamBorder,
                    activeTrackColor = QGenExamTheme.Colors.ExamBorder,
                    inactiveTrackColor = QGenExamTheme.Colors.DividerColor
                )
            )
        }

        // Choice Count
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("보기 개수", style = QGenExamTheme.Typography.labelTextStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.choiceCount == 4,
                        onClick = { viewModel.onChoiceCountChanged(4) },
                        enabled = !uiState.isLoading,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = QGenExamTheme.Colors.ExamBorder,
                            unselectedColor = QGenExamTheme.Colors.DividerColor
                        )
                    )
                    Text("4지선다", style = QGenExamTheme.Typography.bodyTextStyle)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.choiceCount == 5,
                        onClick = { viewModel.onChoiceCountChanged(5) },
                        enabled = !uiState.isLoading,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = QGenExamTheme.Colors.ExamBorder,
                            unselectedColor = QGenExamTheme.Colors.DividerColor
                        )
                    )
                    Text("5지선다", style = QGenExamTheme.Typography.bodyTextStyle)
                }
            }
        }

        // Language
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("문제 언어", style = QGenExamTheme.Typography.labelTextStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Language.entries.forEach { language ->
                    FilterChip(
                        selected = uiState.language == language,
                        onClick = { viewModel.onLanguageChanged(language) },
                        label = { 
                            Text(
                                language.displayName,
                                style = QGenExamTheme.Typography.smallTextStyle
                            )
                        },
                        enabled = !uiState.isLoading,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = QGenExamTheme.Colors.CardBackground,
                            selectedContainerColor = QGenExamTheme.Colors.ExamBorder,
                            labelColor = QGenExamTheme.Colors.TextPrimary,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = !uiState.isLoading,
                            selected = uiState.language == language,
                            borderColor = QGenExamTheme.Colors.DividerColor,
                            selectedBorderColor = QGenExamTheme.Colors.ExamBorder
                        )
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
            Text(
                "Mock API 사용 (개발용)",
                style = QGenExamTheme.Typography.bodyTextStyle
            )
            Switch(
                checked = uiState.useMockApi,
                onCheckedChange = { viewModel.onMockApiToggled(it) },
                enabled = !uiState.isLoading,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = QGenExamTheme.Colors.CardBackground,
                    checkedTrackColor = QGenExamTheme.Colors.ExamBorder,
                    uncheckedThumbColor = QGenExamTheme.Colors.CardBackground,
                    uncheckedTrackColor = QGenExamTheme.Colors.DividerColor
                )
            )
        }

        // Generate Button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.onGenerateClicked() },
            enabled = !uiState.isLoading && uiState.topic.isNotBlank(),
            color = QGenExamTheme.Colors.ExamBorder,
            shape = RoundedCornerShape(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                        Text(
                            "생성 중...",
                            style = QGenExamTheme.Typography.buttonTextStyle,
                            color = Color.White
                        )
                    }
                } else {
                    Text(
                        "문제 생성하기",
                        style = QGenExamTheme.Typography.buttonTextStyle,
                        color = Color.White
                    )
                }
            }
        }

        // Error Message
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage!!,
                color = QGenExamTheme.Colors.ErrorColor,
                style = QGenExamTheme.Typography.bodyTextStyle
            )
        }
    }
}

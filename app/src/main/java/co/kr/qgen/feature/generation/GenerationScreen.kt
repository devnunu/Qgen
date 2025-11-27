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
import co.kr.qgen.core.ui.components.ExamButton
import co.kr.qgen.core.ui.components.ExamFilterButton
import co.kr.qgen.core.ui.theme.*
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
            .examPaperBackground()
            .verticalScroll(rememberScrollState())
            .padding(ExamDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(ExamDimensions.SectionSpacing)
    ) {
        // Title
        Text(
            text = "QGen 모의고사 생성",
            style = ExamTypography.examTitleTextStyle
        )

        // Topic Input
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "주제",
                style = ExamTypography.examLabelTextStyle
            )
            OutlinedTextField(
                value = uiState.topic,
                onValueChange = { viewModel.onTopicChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { 
                    Text(
                        "예: Android Coroutines",
                        style = ExamTypography.examBodyTextStyle
                    )
                },
                enabled = !uiState.isLoading,
                textStyle = ExamTypography.examBodyTextStyle,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ExamColors.ExamBorderGray,
                    unfocusedBorderColor = ExamColors.ExamButtonBorder,
                    focusedContainerColor = ExamColors.ExamCardBackground,
                    unfocusedContainerColor = ExamColors.ExamCardBackground
                ),
                shape = ExamShapes.ButtonShape
            )

            // Topic Presets - ExamFilterButton
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopicPreset.entries.forEach { preset ->
                    ExamFilterButton(
                        text = preset.displayName,
                        isSelected = uiState.selectedPreset == preset,
                        onClick = { viewModel.onPresetSelected(preset) },
                        enabled = !uiState.isLoading
                    )
                }
            }
        }

        // Difficulty - ExamFilterButton
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("난이도", style = ExamTypography.examLabelTextStyle)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Difficulty.entries.forEach { difficulty ->
                    ExamFilterButton(
                        text = difficulty.displayName,
                        isSelected = uiState.difficulty == difficulty,
                        onClick = { viewModel.onDifficultyChanged(difficulty) },
                        enabled = !uiState.isLoading
                    )
                }
            }
        }

        // Count - Slider (최소 디자인)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("문항 수", style = ExamTypography.examLabelTextStyle)
                Text(
                    "${uiState.count}문항",
                    style = ExamTypography.examBodyTextStyle
                )
            }
            Slider(
                value = uiState.count.toFloat(),
                onValueChange = { viewModel.onCountChanged(it.toInt()) },
                valueRange = 1f..50f,
                steps = 48,
                enabled = !uiState.isLoading,
                colors = SliderDefaults.colors(
                    thumbColor = ExamColors.ExamBorderGray,
                    activeTrackColor = ExamColors.ExamBorderGray,
                    inactiveTrackColor = ExamColors.DividerColor
                )
            )
        }

        // Choice Count - ExamFilterButton
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("보기 개수", style = ExamTypography.examLabelTextStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExamFilterButton(
                    text = "4지선다",
                    isSelected = uiState.choiceCount == 4,
                    onClick = { viewModel.onChoiceCountChanged(4) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f)
                )
                ExamFilterButton(
                    text = "5지선다",
                    isSelected = uiState.choiceCount == 5,
                    onClick = { viewModel.onChoiceCountChanged(5) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Language - ExamFilterButton
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("문제 언어", style = ExamTypography.examLabelTextStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Language.entries.forEach { language ->
                    ExamFilterButton(
                        text = language.displayName,
                        isSelected = uiState.language == language,
                        onClick = { viewModel.onLanguageChanged(language) },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Mock API Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Mock API 사용 (개발용)",
                style = ExamTypography.examSmallTextStyle
            )
            Switch(
                checked = uiState.useMockApi,
                onCheckedChange = { viewModel.onMockApiToggled(it) },
                enabled = !uiState.isLoading,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ExamColors.ExamCardBackground,
                    checkedTrackColor = ExamColors.ExamBorderGray,
                    uncheckedThumbColor = ExamColors.ExamCardBackground,
                    uncheckedTrackColor = ExamColors.DividerColor
                )
            )
        }

        // Generate Button - ExamButton (CTA)
        ExamButton(
            text = "문제 생성하기",
            onClick = { viewModel.onGenerateClicked() },
            enabled = !uiState.isLoading && uiState.topic.isNotBlank(),
            isLoading = uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )

        // Error Message
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage!!,
                color = ExamColors.ErrorColor,
                style = ExamTypography.examBodyTextStyle
            )
        }
    }
}

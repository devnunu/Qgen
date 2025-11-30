package co.kr.qgen.feature.generation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.model.Difficulty
import co.kr.qgen.core.ui.components.ExamButton
import co.kr.qgen.core.ui.components.ExamFilterButton
import co.kr.qgen.core.ui.components.QGenScaffold
import co.kr.qgen.core.ui.theme.ExamColors
import co.kr.qgen.core.ui.theme.ExamDimensions
import co.kr.qgen.core.ui.theme.ExamShapes
import co.kr.qgen.core.ui.theme.ExamTypography
import co.kr.qgen.core.ui.theme.examPaperBackground
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GenerationScreen(
    viewModel: GenerationViewModel = koinViewModel(),
    onNavigateToLoading: () -> Unit,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Side effects 처리
    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is GenerationSideEffect.NavigateToLoading -> {
                    onNavigateToLoading()
                }
                is GenerationSideEffect.ShowError -> {
                    onShowMessage(effect.message)
                }
            }
        }
    }

    QGenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("문제 생성", style = ExamTypography.examTitleTextStyle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ExamColors.ExamBackground
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ExamColors.ExamBackground)
                    .padding(horizontal = ExamDimensions.ScreenPadding, vertical = 16.dp)
            ) {
                // Error Message
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = ExamColors.ErrorColor,
                        style = ExamTypography.examBodyTextStyle,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Generate Button
                ExamButton(
                    text = "문제 생성하기",
                    onClick = { viewModel.onGenerateClicked() },
                    enabled = uiState.topic.isNotBlank(),
                    isLoading = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        containerColor = ExamColors.ExamBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                            "주제를 입력해주세요",
                            style = ExamTypography.examBodyTextStyle,
                            color = ExamColors.ExamTextSecondary
                        )
                    },
                    enabled = true,
                    textStyle = ExamTypography.examBodyTextStyle,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExamColors.ExamBorderGray,
                        unfocusedBorderColor = ExamColors.ExamButtonBorder,
                        focusedContainerColor = ExamColors.ExamCardBackground,
                        unfocusedContainerColor = ExamColors.ExamCardBackground,
                        focusedPlaceholderColor = ExamColors.ExamTextSecondary,
                        unfocusedPlaceholderColor = ExamColors.ExamTextSecondary
                    ),
                    shape = ExamShapes.ButtonShape,
                    supportingText = {
                        Text(
                            "${uiState.topic.length}/30",
                            style = ExamTypography.examSmallTextStyle,
                            color = ExamColors.ExamTextSecondary
                        )
                    }
                )

                // Recent Topics
                if (uiState.recentTopics.isNotEmpty()) {
                    Text(
                        "최근 입력",
                        style = ExamTypography.examSmallTextStyle
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.recentTopics.forEach { topic ->
                            ExamFilterButton(
                                text = topic,
                                isSelected = false,
                                onClick = { viewModel.onRecentTopicSelected(topic) },
                                enabled = true
                            )
                        }
                    }
                }
            }

            // Description Input (Optional)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "상세 설명 (선택사항)",
                    style = ExamTypography.examLabelTextStyle
                )
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.onDescriptionChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "AI에게 추가로 전달할 상세 설명을 입력하세요",
                            style = ExamTypography.examBodyTextStyle,
                            color = ExamColors.ExamTextSecondary
                        )
                    },
                    enabled = true,
                    textStyle = ExamTypography.examBodyTextStyle,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExamColors.ExamBorderGray,
                        unfocusedBorderColor = ExamColors.ExamButtonBorder,
                        focusedContainerColor = ExamColors.ExamCardBackground,
                        unfocusedContainerColor = ExamColors.ExamCardBackground,
                        focusedPlaceholderColor = ExamColors.ExamTextSecondary,
                        unfocusedPlaceholderColor = ExamColors.ExamTextSecondary
                    ),
                    shape = ExamShapes.ButtonShape,
                    minLines = 3,
                    maxLines = 5,
                    supportingText = {
                        Text(
                            "${uiState.description.length}/300",
                            style = ExamTypography.examSmallTextStyle,
                            color = ExamColors.ExamTextSecondary
                        )
                    }
                )
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
                            enabled = true
                        )
                    }
                }
            }

            // Count - ExamFilterButton (5, 10, 15, 20) in 2x2 grid
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("문항 수", style = ExamTypography.examLabelTextStyle)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // First row: 5, 10
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExamFilterButton(
                            text = "5문항",
                            isSelected = uiState.count == 5,
                            onClick = { viewModel.onCountChanged(5) },
                            enabled = true,
                            modifier = Modifier.weight(1f)
                        )
                        ExamFilterButton(
                            text = "10문항",
                            isSelected = uiState.count == 10,
                            onClick = { viewModel.onCountChanged(10) },
                            enabled = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Second row: 15, 20
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExamFilterButton(
                            text = "15문항",
                            isSelected = uiState.count == 15,
                            onClick = { viewModel.onCountChanged(15) },
                            enabled = true,
                            modifier = Modifier.weight(1f)
                        )
                        ExamFilterButton(
                            text = "20문항",
                            isSelected = uiState.count == 20,
                            onClick = { viewModel.onCountChanged(20) },
                            enabled = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Choice Count - ExamFilterButton
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("보기 개수", style = ExamTypography.examLabelTextStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExamFilterButton(
                        text = "4지선다",
                        isSelected = uiState.choiceCount == 4,
                        onClick = { viewModel.onChoiceCountChanged(4) },
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    )
                    ExamFilterButton(
                        text = "5지선다",
                        isSelected = uiState.choiceCount == 5,
                        onClick = { viewModel.onChoiceCountChanged(5) },
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    )
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
                    enabled = true,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ExamColors.ExamCardBackground,
                        checkedTrackColor = ExamColors.ExamBorderGray,
                        uncheckedThumbColor = ExamColors.ExamCardBackground,
                        uncheckedTrackColor = ExamColors.DividerColor
                    )
                )
            }
        }
    }
}

package co.kr.qgen.feature.generation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
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
import co.kr.qgen.core.model.TopicPreset
import co.kr.qgen.core.ui.components.ExamButton
import co.kr.qgen.core.ui.components.ExamFilterButton
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
    onNavigateToLoading: (topic: String, difficulty: String, count: Int, choiceCount: Int, tags: String?) -> Unit,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Side effects 처리
    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is GenerationSideEffect.NavigateToLoading -> {
                    onNavigateToLoading(
                        effect.topic,
                        effect.difficulty,
                        effect.count,
                        effect.choiceCount,
                        effect.tags
                    )
                }
                is GenerationSideEffect.ShowError -> {
                    onShowMessage(effect.message)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            "예: Android Coroutines",
                            style = ExamTypography.examBodyTextStyle
                        )
                    },
                    enabled = true,
                    textStyle = ExamTypography.examBodyTextStyle,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExamColors.ExamBorderGray,
                        unfocusedBorderColor = ExamColors.ExamButtonBorder,
                        focusedContainerColor = ExamColors.ExamCardBackground,
                        unfocusedContainerColor = ExamColors.ExamCardBackground
                    ),
                    shape = ExamShapes.ButtonShape
                )

                // Tags Input
                OutlinedTextField(
                    value = uiState.tags,
                    onValueChange = { viewModel.onTagsChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { 
                        Text(
                            "태그 (예: Android, Coroutine, 면접)",
                            style = ExamTypography.examBodyTextStyle
                        )
                    },
                    enabled = true,
                    textStyle = ExamTypography.examBodyTextStyle,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExamColors.ExamBorderGray,
                        unfocusedBorderColor = ExamColors.ExamButtonBorder,
                        focusedContainerColor = ExamColors.ExamCardBackground,
                        unfocusedContainerColor = ExamColors.ExamCardBackground
                    ),
                    shape = ExamShapes.ButtonShape
                )

                // Recent Topics
                if (uiState.recentTopics.isNotEmpty()) {
                    Text(
                        "최근 검색어",
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

                // Topic Presets - ExamFilterButton
                Text(
                    "추천 주제",
                    style = ExamTypography.examSmallTextStyle
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TopicPreset.entries.forEach { preset ->
                        ExamFilterButton(
                            text = preset.displayName,
                            isSelected = uiState.selectedPreset == preset,
                            onClick = { viewModel.onPresetSelected(preset) },
                            enabled = true
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
                            enabled = true
                        )
                    }
                }
            }

            // Count - ExamFilterButton (5, 10, 15, 20)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("문항 수", style = ExamTypography.examLabelTextStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20).forEach { count ->
                        ExamFilterButton(
                            text = "${count}문항",
                            isSelected = uiState.count == count,
                            onClick = { viewModel.onCountChanged(count) },
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

            // Generate Button - ExamButton (CTA)
            ExamButton(
                text = "문제 생성하기",
                onClick = { viewModel.onGenerateClicked() },
                enabled = uiState.topic.isNotBlank(),
                isLoading = false, // 로딩 화면을 따로 띄우므로 버튼 자체 로딩은 끔
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
}

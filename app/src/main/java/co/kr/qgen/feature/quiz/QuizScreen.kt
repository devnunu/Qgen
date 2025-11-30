package co.kr.qgen.feature.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.model.Question
import co.kr.qgen.core.ui.components.ExamButton
import co.kr.qgen.core.ui.components.ExamChoiceItem
import co.kr.qgen.core.ui.components.ExamQuestionHeader
import co.kr.qgen.core.ui.components.QGenScaffold
import co.kr.qgen.core.ui.components.getCircledNumber
import co.kr.qgen.core.ui.theme.ExamColors
import co.kr.qgen.core.ui.theme.ExamDimensions
import co.kr.qgen.core.ui.theme.ExamTypography
import co.kr.qgen.core.ui.theme.examPaperBackground
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: QuizViewModel = koinViewModel(),
    onNavigateToResult: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shouldNavigateToResult by viewModel.shouldNavigateToResult.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { uiState.questions.size })
    val scope = rememberCoroutineScope()
    var showBackDialog by remember { mutableStateOf(false) }

    // 뒤로가기 처리
    BackHandler {
        showBackDialog = true
    }

    // 뒤로가기 확인 다이얼로그
    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title = { Text("퀴즈 종료") },
            text = { Text("퀴즈를 종료하고 돌아가시겠습니까?\n진행 상황이 저장되지 않습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackDialog = false }) {
                    Text("취소")
                }
            },
            containerColor = co.kr.qgen.core.ui.theme.ExamColors.ExamCardBackground
        )
    }

    LaunchedEffect(shouldNavigateToResult) {
        if (shouldNavigateToResult) {
            viewModel.onNavigatedToResult()
            onNavigateToResult()
        }
    }

    if (uiState.questions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().examPaperBackground(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = ExamColors.ExamTextPrimary)
        }
        return
    }

    QGenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "문제 풀이 (${pagerState.currentPage + 1}/${uiState.questions.size})",
                        style = ExamTypography.examTitleTextStyle
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { showBackDialog = true }) {
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
        containerColor = ExamColors.ExamBackground,
        bottomBar = {
            QuizBottomBar(
                currentPage = pagerState.currentPage,
                totalPages = uiState.questions.size,
                answeredCount = uiState.userAnswers.size,
                onPrevious = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                onSubmit = { viewModel.submitQuiz() }
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            val question = uiState.questions[page]
            QuestionPage(
                questionNumber = page + 1,
                question = question,
                selectedAnswer = uiState.userAnswers[question.id],
                onAnswerSelected = { answerId ->
                    viewModel.onAnswerSelected(question.id, answerId)
                },
                points = 4 // Default points
            )
        }
    }
}

@Composable
fun QuestionPage(
    questionNumber: Int,
    question: Question,
    selectedAnswer: String?,
    onAnswerSelected: (String) -> Unit,
    points: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ExamDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(ExamDimensions.SectionSpacing)
    ) {
        // Question Header (텍스트만)
        ExamQuestionHeader(
            number = questionNumber,
            questionText = question.stem,
            points = points
        )

        // Choices
        Column(verticalArrangement = Arrangement.spacedBy(ExamDimensions.ChoiceSpacing)) {
            question.choices.forEach { choice ->
                ExamChoiceItem(
                    number = getCircledNumber(choice.id),
                    text = choice.text,
                    isSelected = selectedAnswer == choice.id,
                    onClick = { onAnswerSelected(choice.id) }
                )
            }
        }
    }
}

@Composable
fun QuizBottomBar(
    currentPage: Int,
    totalPages: Int,
    answeredCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ExamDimensions.ScreenPadding)
    ) {
        HorizontalDivider(color = ExamColors.ExamBorderGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Previous Button
            ExamButton(
                text = "이전 문제",
                onClick = onPrevious,
                enabled = currentPage > 0,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Submit or Next Button
            if (currentPage == totalPages - 1) {
                ExamButton(
                    text = "답안 제출",
                    onClick = onSubmit,
                    enabled = answeredCount == totalPages,
                    modifier = Modifier.weight(1f),
                    isSelected = true // Highlight
                )
            } else {
                ExamButton(
                    text = "다음 문제",
                    onClick = onNext,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

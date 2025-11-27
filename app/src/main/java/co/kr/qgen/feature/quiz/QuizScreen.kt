package co.kr.qgen.feature.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.model.Question
import co.kr.qgen.core.ui.components.*
import co.kr.qgen.core.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun QuizScreen(
    viewModel: QuizViewModel = koinViewModel(),
    onNavigateToResult: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shouldNavigateToResult by viewModel.shouldNavigateToResult.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { uiState.questions.size })
    val scope = rememberCoroutineScope()

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

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ExamDimensions.ScreenPadding, vertical = 16.dp)
            ) {
                Text(
                    "문제 풀이 영역",
                    style = ExamTypography.examTitleTextStyle
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "문제 ${pagerState.currentPage + 1} / ${uiState.questions.size}",
                    style = ExamTypography.examSmallTextStyle
                )
            }
        },
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
        },
        containerColor = ExamColors.ExamBackground
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
        // Question Box
        ExamQuestionContainer {
            ExamQuestionHeader(
                number = questionNumber,
                questionText = question.stem,
                points = points
            )
        }

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

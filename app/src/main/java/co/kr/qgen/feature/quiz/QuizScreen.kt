package co.kr.qgen.feature.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.model.Question
import co.kr.qgen.core.ui.theme.QGenExamTheme
import co.kr.qgen.core.ui.theme.examPaperBackground
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: QuizViewModel = koinViewModel(),
    onNavigateToResult: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shouldNavigateToResult by viewModel.shouldNavigateToResult.collectAsStateWithLifecycle()

    // Navigate when quiz is submitted
    LaunchedEffect(shouldNavigateToResult) {
        if (shouldNavigateToResult) {
            viewModel.onNavigatedToResult()
            onNavigateToResult()
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.questions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("문제를 불러올 수 없습니다")
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { uiState.questions.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }

    Scaffold(
        modifier = Modifier.examPaperBackground(),
        topBar = {
            Surface(
                color = QGenExamTheme.Colors.CardBackground,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(QGenExamTheme.Dimensions.screenPadding)
                ) {
                    Text(
                        "문제 ${pagerState.currentPage + 1} / ${uiState.questions.size}",
                        style = QGenExamTheme.Typography.sectionHeaderStyle
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (pagerState.currentPage + 1).toFloat() / uiState.questions.size },
                        modifier = Modifier.fillMaxWidth(),
                        color = QGenExamTheme.Colors.ExamBorder,
                        trackColor = QGenExamTheme.Colors.DividerColor
                    )
                }
            }
        },
        bottomBar = {
            QuizBottomBar(
                currentPage = pagerState.currentPage,
                totalPages = uiState.questions.size,
                answeredCount = viewModel.getAnsweredCount(),
                onPrevious = {
                    if (pagerState.currentPage > 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                },
                onNext = {
                    if (pagerState.currentPage < uiState.questions.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                onSubmit = { viewModel.submitQuiz() }
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            QuestionPage(
                questionNumber = page + 1,
                question = uiState.questions[page],
                selectedAnswer = uiState.userAnswers[uiState.questions[page].id],
                onAnswerSelected = { choiceId ->
                    viewModel.onAnswerSelected(uiState.questions[page].id, choiceId)
                }
            )
        }
    }
}

@Composable
fun QuestionPage(
    questionNumber: Int,
    question: Question,
    selectedAnswer: String?,
    onAnswerSelected: (String) -> Unit
) {
    co.kr.qgen.core.ui.components.ExamQuestionLayout(
        questionNumber = questionNumber,
        question = question,
        selectedAnswer = selectedAnswer,
        onAnswerSelected = onAnswerSelected,
        points = 4 // Can be dynamic based on question metadata
    )
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
    Surface(
        color = QGenExamTheme.Colors.CardBackground,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(QGenExamTheme.Dimensions.screenPadding)
        ) {
            Text(
                text = "답변한 문제: $answeredCount / $totalPages",
                style = QGenExamTheme.Typography.bodyTextStyle,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Previous Button
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = currentPage > 0,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = QGenExamTheme.Colors.TextPrimary
                    ),
                    border = BorderStroke(1.dp, QGenExamTheme.Colors.ExamBorder)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("이전", style = QGenExamTheme.Typography.buttonTextStyle)
                }

                // Submit or Next Button
                if (currentPage == totalPages - 1) {
                    Button(
                        onClick = onSubmit,
                        enabled = answeredCount == totalPages,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = QGenExamTheme.Colors.ExamBorder,
                            contentColor = Color.White
                        )
                    ) {
                        Text("채점하기", style = QGenExamTheme.Typography.buttonTextStyle)
                    }
                } else {
                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = QGenExamTheme.Colors.ExamBorder,
                            contentColor = Color.White
                        )
                    ) {
                        Text("다음", style = QGenExamTheme.Typography.buttonTextStyle)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "다음")
                    }
                }
            }
        }
    }
}

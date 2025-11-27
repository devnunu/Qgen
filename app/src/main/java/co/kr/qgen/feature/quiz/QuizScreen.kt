package co.kr.qgen.feature.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.model.Question
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("문제 ${pagerState.currentPage + 1} / ${uiState.questions.size}")
                        LinearProgressIndicator(
                            progress = { (pagerState.currentPage + 1).toFloat() / uiState.questions.size },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            )
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
    question: Question,
    selectedAnswer: String?,
    onAnswerSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Question Stem
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = question.stem,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Choices
        Text(
            text = "답을 선택하세요",
            style = MaterialTheme.typography.titleMedium
        )

        question.choices.forEach { choice ->
            ChoiceCard(
                choice = choice,
                isSelected = selectedAnswer == choice.id,
                onSelected = { onAnswerSelected(choice.id) }
            )
        }
    }
}

@Composable
fun ChoiceCard(
    choice: co.kr.qgen.core.model.QuestionChoice,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelected,
                role = Role.RadioButton
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${choice.id}. ${choice.text}",
                style = MaterialTheme.typography.bodyLarge
            )
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
    Surface(
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "답변한 문제: $answeredCount / $totalPages",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Previous Button
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = currentPage > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("이전")
                }

                // Submit or Next Button
                if (currentPage == totalPages - 1) {
                    Button(
                        onClick = onSubmit,
                        enabled = answeredCount == totalPages
                    ) {
                        Text("채점하기")
                    }
                } else {
                    Button(onClick = onNext) {
                        Text("다음")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "다음")
                    }
                }
            }
        }
    }
}

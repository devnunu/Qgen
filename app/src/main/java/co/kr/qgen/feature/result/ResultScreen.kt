package co.kr.qgen.feature.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.feature.quiz.ResultItem
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel = koinViewModel(),
    onNavigateHome: () -> Unit
) {
    val quizResult by viewModel.quizResult.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("채점 결과") }
            )
        }
    ) { paddingValues ->
        if (quizResult == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("결과를 불러올 수 없습니다")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Score Summary Card
            ScoreSummaryCard(
                score = quizResult!!.score,
                correctCount = quizResult!!.correctCount,
                wrongCount = quizResult!!.wrongCount,
                totalQuestions = quizResult!!.totalQuestions
            )

            // Results List
            Text(
                text = "문제별 결과",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            quizResult!!.resultItems.forEachIndexed { index, resultItem ->
                ResultItemCard(
                    index = index + 1,
                    resultItem = resultItem
                )
            }

            // Retry Button
            Button(
                onClick = onNavigateHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("다시 풀기")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ScoreSummaryCard(
    score: Int,
    correctCount: Int,
    wrongCount: Int,
    totalQuestions: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "점수",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${score}점",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "정답",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$correctCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "오답",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$wrongCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF44336)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "전체",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$totalQuestions",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ResultItemCard(
    index: Int,
    resultItem: ResultItem
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (resultItem.isCorrect) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "문제 $index",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (resultItem.isCorrect) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Close
                        },
                        contentDescription = null,
                        tint = if (resultItem.isCorrect) {
                            Color(0xFF4CAF50)
                        } else {
                            Color(0xFFF44336)
                        }
                    )
                    Text(
                        text = if (resultItem.isCorrect) "정답" else "오답",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (resultItem.isCorrect) {
                            Color(0xFF4CAF50)
                        } else {
                            Color(0xFFF44336)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Divider()

            // Question Stem
            Text(
                text = resultItem.question.stem,
                style = MaterialTheme.typography.bodyMedium
            )

            // User Answer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "내 답:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = resultItem.userAnswer?.let { answerId ->
                        resultItem.question.choices.find { it.id == answerId }?.let {
                            "${it.id}. ${it.text}"
                        } ?: "선택 안함"
                    } ?: "선택 안함",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (resultItem.isCorrect) {
                        Color(0xFF4CAF50)
                    } else {
                        Color(0xFFF44336)
                    }
                )
            }

            // Correct Answer
            if (!resultItem.isCorrect) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "정답:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = resultItem.question.choices.find {
                            it.id == resultItem.question.correctChoiceId
                        }?.let {
                            "${it.id}. ${it.text}"
                        } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            // Explanation
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "해설",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = resultItem.question.explanation,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

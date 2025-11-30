package co.kr.qgen.feature.result

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kr.qgen.core.ui.components.ExamButton
import co.kr.qgen.core.ui.components.ExamQuestionContainer
import co.kr.qgen.core.ui.components.getCircledNumber
import co.kr.qgen.core.ui.theme.ExamColors
import co.kr.qgen.core.ui.theme.ExamDimensions
import co.kr.qgen.core.ui.theme.ExamTypography
import co.kr.qgen.core.ui.theme.examPaperBackground
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel = koinViewModel(),
    onNavigateHome: () -> Unit
) {
    val quizResult by viewModel.quizResult.collectAsStateWithLifecycle()

    if (quizResult == null) {
        Box(
            modifier = Modifier.fillMaxSize().examPaperBackground(),
            contentAlignment = Alignment.Center
        ) {
            Text("결과를 불러올 수 없습니다.", style = ExamTypography.examBodyTextStyle)
        }
        return
    }

    val result = quizResult!!

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("채점 결과", style = ExamTypography.examTitleTextStyle) },
                actions = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "홈으로"
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

        // Score Summary
        ExamQuestionContainer {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "총점",
                    style = ExamTypography.examSectionHeaderStyle
                )
                Text(
                    "${result.score}점",
                    style = ExamTypography.examTitleTextStyle.copy(
                        fontSize = ExamTypography.examTitleTextStyle.fontSize * 1.5f
                    )
                )
                HorizontalDivider(color = ExamColors.ExamBorderGray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("정답", style = ExamTypography.examSmallTextStyle)
                        Text(
                            "${result.correctCount}",
                            style = ExamTypography.examSectionHeaderStyle,
                            color = ExamColors.SuccessColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("오답", style = ExamTypography.examSmallTextStyle)
                        Text(
                            "${result.wrongCount}",
                            style = ExamTypography.examSectionHeaderStyle,
                            color = ExamColors.ErrorColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("전체", style = ExamTypography.examSmallTextStyle)
                        Text(
                            "${result.totalQuestions}",
                            style = ExamTypography.examSectionHeaderStyle
                        )
                    }
                }
            }
        }

        // Result Items
        result.resultItems.forEachIndexed { index, item ->
            ResultItemView(index + 1, item)
        }

        // Retry Button
        ExamButton(
            text = "다시 풀기",
            onClick = onNavigateHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )
        }
    }
}

@Composable
fun ResultItemView(number: Int, item: co.kr.qgen.feature.quiz.ResultItem) {
    ExamQuestionContainer {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$number.",
                style = ExamTypography.examNumberTextStyle
            )
            Text(
                text = if (item.isCorrect) "[정답]" else "[오답]",
                style = ExamTypography.examNumberTextStyle.copy(
                    fontWeight = if (item.isCorrect) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (item.isCorrect) ExamColors.SuccessColor else ExamColors.ErrorColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Question Stem
        Text(
            text = item.question.stem,
            style = ExamTypography.examBodyTextStyle
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Choices
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.question.choices.forEach { choice ->
                val isSelected = choice.id == item.userAnswer
                val isCorrect = choice.id == item.question.correctChoiceId
                
                val textStyle = if (isCorrect) {
                    ExamTypography.examChoiceTextStyle.copy(fontWeight = FontWeight.Bold)
                } else {
                    ExamTypography.examChoiceTextStyle
                }
                
                val prefix = when {
                    isCorrect && isSelected -> "◎ " // Correct and Selected
                    isCorrect -> "○ " // Correct but not selected
                    isSelected -> "● " // Wrong and Selected
                    else -> "  "
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = prefix + getCircledNumber(choice.id),
                        style = textStyle,
                        color = if (isCorrect) ExamColors.SuccessColor 
                               else if (isSelected) ExamColors.ErrorColor 
                               else ExamColors.ExamTextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = choice.text,
                        style = textStyle,
                        color = if (isCorrect) ExamColors.SuccessColor 
                               else if (isSelected) ExamColors.ErrorColor 
                               else ExamColors.ExamTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        // Explanation (if wrong)
        if (!item.isCorrect) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = ExamColors.ExamBorderGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "해설: ${item.question.explanation}",
                style = ExamTypography.examBodyTextStyle.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

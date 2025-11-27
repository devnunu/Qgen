package co.kr.qgen.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import co.kr.qgen.core.model.Question
import co.kr.qgen.core.model.QuestionChoice
import co.kr.qgen.core.ui.theme.ExamBulletPoint
import co.kr.qgen.core.ui.theme.ExamChoiceNumber
import co.kr.qgen.core.ui.theme.ExamQuestionContainer
import co.kr.qgen.core.ui.theme.QGenExamTheme

/**
 * Exam-style Question Layout
 * Mimics Korean mock exam paper design
 */
@Composable
fun ExamQuestionLayout(
    questionNumber: Int,
    question: Question,
    selectedAnswer: String?,
    onAnswerSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    points: Int = 4 // Default points per question
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(QGenExamTheme.Dimensions.questionSpacing)
    ) {
        // Question Header: Number + Stem + Points
        ExamQuestionHeader(
            questionNumber = questionNumber,
            stem = question.stem,
            points = points
        )
        
        // Question Content Box (if needed for complex questions)
        // For simple questions, this can be omitted
        // For now, we'll show the stem in a box if it's long
        if (question.stem.length > 100) {
            ExamQuestionContainer {
                Text(
                    text = question.stem,
                    style = QGenExamTheme.Typography.boxContentStyle
                )
            }
        }
        
        // Answer Choices
        ExamAnswerChoices(
            choices = question.choices,
            selectedAnswer = selectedAnswer,
            onAnswerSelected = onAnswerSelected
        )
    }
}

/**
 * Question Header with number, stem, and points
 */
@Composable
private fun ExamQuestionHeader(
    questionNumber: Int,
    stem: String,
    points: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Question Number
        Text(
            text = "$questionNumber.",
            style = QGenExamTheme.Typography.questionNumberStyle
        )
        
        // Question Stem
        Text(
            text = buildAnnotatedString {
                withStyle(QGenExamTheme.Typography.questionStemStyle.toSpanStyle()) {
                    append(stem)
                }
                append(" ")
                withStyle(QGenExamTheme.Typography.questionPointsStyle.toSpanStyle()) {
                    append("[${points}점]")
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Answer Choices in exam style (①, ②, ③, ④, ⑤)
 */
@Composable
private fun ExamAnswerChoices(
    choices: List<QuestionChoice>,
    selectedAnswer: String?,
    onAnswerSelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(QGenExamTheme.Dimensions.choiceSpacingVertical)
    ) {
        choices.forEach { choice ->
            ExamAnswerChoice(
                choice = choice,
                isSelected = selectedAnswer == choice.id,
                onSelected = { onAnswerSelected(choice.id) }
            )
        }
    }
}

/**
 * Single Answer Choice
 */
@Composable
private fun ExamAnswerChoice(
    choice: QuestionChoice,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelected)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(QGenExamTheme.Dimensions.choiceSpacingHorizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Choice Number (①, ②, etc.)
        ExamChoiceNumber(
            number = getCircledNumber(choice.id),
            isSelected = isSelected
        )
        
        // Choice Text
        Text(
            text = choice.text,
            style = QGenExamTheme.Typography.boxContentStyle,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Convert choice ID (A, B, C, D, E) to circled number (①, ②, ③, ④, ⑤)
 */
private fun getCircledNumber(choiceId: String): String {
    return when (choiceId.uppercase()) {
        "A" -> "①"
        "B" -> "②"
        "C" -> "③"
        "D" -> "④"
        "E" -> "⑤"
        else -> choiceId
    }
}

/**
 * Exam Question with Content Box
 * For questions that need a bordered content area
 */
@Composable
fun ExamQuestionWithContentBox(
    questionNumber: Int,
    stem: String,
    contentLines: List<String>,
    choices: List<QuestionChoice>,
    selectedAnswer: String?,
    onAnswerSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    points: Int = 4
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(QGenExamTheme.Dimensions.questionSpacing)
    ) {
        // Question Header
        ExamQuestionHeader(
            questionNumber = questionNumber,
            stem = stem,
            points = points
        )
        
        // Content Box with bullet points
        ExamQuestionContainer {
            contentLines.forEach { line ->
                ExamBulletPoint(text = line)
            }
        }
        
        // Answer Choices
        ExamAnswerChoices(
            choices = choices,
            selectedAnswer = selectedAnswer,
            onAnswerSelected = onAnswerSelected
        )
    }
}

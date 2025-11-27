package co.kr.qgen.core.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * QGen Exam Theme - Korean Mock Exam Style
 * Paper-like design with serif fonts
 */
object QGenExamTheme {
    
    // Colors - Paper-like exam style
    object Colors {
        val PaperBackground = Color(0xFFFAFAFA) // Light paper color
        val CardBackground = Color(0xFFFFFFFF) // Pure white for cards
        val ExamBorder = Color(0xFF000000) // Black border
        val TextPrimary = Color(0xFF000000) // Black text
        val TextSecondary = Color(0xFF666666) // Gray text
        val TextTertiary = Color(0xFF999999) // Light gray text
        val ExamChoiceSelected = Color(0xFF000000)
        val ExamChoiceUnselected = Color(0xFFFFFFFF)
        val ExamChoiceBorder = Color(0xFF000000)
        val DividerColor = Color(0xFFE0E0E0)
        val SuccessColor = Color(0xFF2E7D32) // Dark green for correct
        val ErrorColor = Color(0xFFC62828) // Dark red for wrong
    }
    
    // Typography - Serif-based for exam feel
    object Typography {
        // Screen titles
        val screenTitleStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Colors.TextPrimary,
            lineHeight = 32.sp
        )
        
        // Section headers
        val sectionHeaderStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Colors.TextPrimary,
            lineHeight = 24.sp
        )
        
        // Body text
        val bodyTextStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextPrimary,
            lineHeight = 24.sp
        )
        
        // Small text
        val smallTextStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextSecondary,
            lineHeight = 18.sp
        )
        
        // Button text
        val buttonTextStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Colors.TextPrimary
        )
        
        // Question number
        val questionNumberStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Colors.TextPrimary,
            lineHeight = 32.sp
        )
        
        // Question stem
        val questionStemStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextPrimary,
            lineHeight = 25.6.sp
        )
        
        // Question points
        val questionPointsStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextPrimary
        )
        
        // Box content
        val boxContentStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextPrimary,
            lineHeight = 27.sp
        )
        
        // Choice number
        val choiceNumberStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextPrimary
        )
        
        // Bullet
        val bulletStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = Colors.TextPrimary
        )
        
        // Label text
        val labelTextStyle = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Colors.TextSecondary
        )
    }
    
    // Dimensions
    object Dimensions {
        val borderWidth = 3.dp
        val thinBorderWidth = 1.dp
        val boxPadding = 16.dp
        val screenPadding = 20.dp
        val cardPadding = 16.dp
        val bulletIndent = 24.dp
        val choiceSpacingVertical = 16.dp
        val choiceSpacingHorizontal = 8.dp
        val questionSpacing = 24.dp
        val sectionSpacing = 32.dp
    }
}

/**
 * Modifier extension for exam paper background
 */
fun Modifier.examPaperBackground() = this
    .fillMaxSize()
    .background(QGenExamTheme.Colors.PaperBackground)

/**
 * Exam Question Container with thick border
 */
@Composable
fun ExamQuestionContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = QGenExamTheme.Dimensions.borderWidth,
                color = QGenExamTheme.Colors.ExamBorder,
                shape = RoundedCornerShape(0.dp)
            ),
        color = QGenExamTheme.Colors.CardBackground
    ) {
        Column(
            modifier = Modifier.padding(QGenExamTheme.Dimensions.boxPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * Exam-style Card (for general use)
 */
@Composable
fun ExamCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = QGenExamTheme.Colors.CardBackground,
        shadowElevation = 0.dp,
        border = BorderStroke(
            QGenExamTheme.Dimensions.thinBorderWidth,
            QGenExamTheme.Colors.DividerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(QGenExamTheme.Dimensions.cardPadding),
            content = content
        )
    }
}

/**
 * Exam Choice Number (①, ②, ③, etc.)
 */
@Composable
fun ExamChoiceNumber(
    number: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .border(
                width = 1.5.dp,
                color = QGenExamTheme.Colors.ExamChoiceBorder,
                shape = CircleShape
            )
            .background(
                color = if (isSelected) {
                    QGenExamTheme.Colors.ExamChoiceSelected
                } else {
                    QGenExamTheme.Colors.ExamChoiceUnselected
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            style = QGenExamTheme.Typography.choiceNumberStyle,
            color = if (isSelected) Color.White else QGenExamTheme.Colors.TextPrimary
        )
    }
}

/**
 * Bullet point for exam content
 */
@Composable
fun ExamBulletPoint(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "○",
            style = QGenExamTheme.Typography.bulletStyle
        )
        Text(
            text = text,
            style = QGenExamTheme.Typography.boxContentStyle,
            modifier = Modifier.weight(1f)
        )
    }
}

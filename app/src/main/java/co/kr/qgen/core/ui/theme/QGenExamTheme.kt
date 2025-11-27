package co.kr.qgen.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// (A) Colors - 모의고사 시험지 색상
@Immutable
object ExamColors {
    val ExamBackground = Color(0xFFFAF8F2) // 아이보리/크림톤
    val ExamCardBackground = Color(0xFFFFFFFF) // 흰색
    val ExamBorderGray = Color(0xFF3A3A3A) // 짙은 회색 테두리
    val ExamTextPrimary = Color(0xFF1D1D1D) // 잉크색 느낌
    val ExamTextSecondary = Color(0xFF4D4D4D)
    val ExamTextTertiary = Color(0xFF999999)
    val ExamChoiceCircle = Color(0xFF2F2F2F)
    val ExamButtonBorder = Color(0xFF4A4A4A)
    val ExamHighlight = Color(0xFFE8E3D8) // 선택/포커스 시 연한 잉크색 강조
    val DividerColor = Color(0xFFE0E0E0)
    val ErrorColor = Color(0xFFB00020)
    val SuccessColor = Color(0xFF2E7D32)
    val DisabledBackground = Color(0xFFF5F5F5)
    val DisabledText = Color(0xFFBDBDBD)
}

// (B) Typography - Serif 중심
@Immutable
object ExamTypography {
    val examTitleTextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 33.6.sp, // 1.4 line-height
        color = ExamColors.ExamTextPrimary
    )
    
    val examBodyTextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 27.2.sp, // 1.6 line-height
        color = ExamColors.ExamTextPrimary
    )
    
    val examSmallTextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp, // 1.5 line-height
        color = ExamColors.ExamTextSecondary
    )
    
    val examChoiceTextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.5.sp, // 1.5 line-height
        color = ExamColors.ExamTextPrimary
    )
    
    val examNumberTextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = ExamColors.ExamTextPrimary
    )
    
    val examButtonTextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = ExamColors.ExamTextPrimary
    )
    
    val examSectionHeaderStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 27.sp, // 1.5 line-height
        color = ExamColors.ExamTextPrimary
    )
    
    val examLabelTextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = ExamColors.ExamTextSecondary
    )
}

// (C) Shapes & Dimensions
object ExamShapes {
    val PaperCardShape = RectangleShape // 직각 모서리
    val ButtonShape = RoundedCornerShape(2.dp) // 살짝 둥근 직각 느낌
}

object ExamDimensions {
    val ExamBoxBorderWidth = 2.dp
    val ExamButtonBorderWidth = 1.dp
    val ScreenPadding = 24.dp
    val SectionSpacing = 32.dp
    val ItemSpacing = 16.dp
    val ChoiceSpacing = 12.dp
}

// (D) Modifier Utils
fun Modifier.examPaperBackground() = this
    .fillMaxSize()
    .background(ExamColors.ExamBackground)

fun Modifier.examBorder() = this.border(
    width = ExamDimensions.ExamBoxBorderWidth,
    color = ExamColors.ExamBorderGray,
    shape = ExamShapes.PaperCardShape
)

fun Modifier.examButtonBorder() = this.border(
    width = ExamDimensions.ExamButtonBorderWidth,
    color = ExamColors.ExamButtonBorder,
    shape = ExamShapes.ButtonShape
)

fun Modifier.examSectionPadding() = this.fillMaxSize()

fun Modifier.examChoiceHighlight(isSelected: Boolean) = this.background(
    color = if (isSelected) ExamColors.ExamHighlight else Color.Transparent
)

// (E) MaterialTheme Override
@Composable
fun QGenExamTheme(
    content: @Composable () -> Unit
) {
    val materialColorScheme = lightColorScheme(
        primary = ExamColors.ExamTextPrimary,
        onPrimary = Color.White,
        background = ExamColors.ExamBackground,
        surface = ExamColors.ExamCardBackground,
        onSurface = ExamColors.ExamTextPrimary,
        error = ExamColors.ErrorColor
    )

    MaterialTheme(
        colorScheme = materialColorScheme,
        content = content
    )
}

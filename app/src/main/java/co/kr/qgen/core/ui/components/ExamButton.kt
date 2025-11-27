package co.kr.qgen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.kr.qgen.core.ui.theme.ExamColors
import co.kr.qgen.core.ui.theme.ExamShapes
import co.kr.qgen.core.ui.theme.ExamTypography
import co.kr.qgen.core.ui.theme.examButtonBorder

/**
 * ExamButton - 모의고사 스타일 버튼
 * 흰 바탕 + 얇은 회색 테두리 + Serif 텍스트
 */
@Composable
fun ExamButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    isLoading: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
) {
    val backgroundColor = when {
        !enabled -> ExamColors.DisabledBackground
        isSelected -> ExamColors.ExamHighlight
        else -> ExamColors.ExamCardBackground
    }
    
    val textColor = when {
        !enabled -> ExamColors.DisabledText
        else -> ExamColors.ExamTextPrimary
    }

    Box(
        modifier = modifier
            .clip(ExamShapes.ButtonShape)
            .background(backgroundColor)
            .examButtonBorder()
            .clickable(enabled = enabled && !isLoading, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = ExamColors.ExamTextPrimary
                )
                Text(
                    text = text,
                    style = ExamTypography.examButtonTextStyle,
                    color = textColor
                )
            }
        } else {
            Text(
                text = text,
                style = ExamTypography.examButtonTextStyle,
                color = textColor
            )
        }
    }
}

/**
 * ExamFilterButton - 작은 필터 버튼 (Chip 스타일)
 */
@Composable
fun ExamFilterButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ExamButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isSelected = isSelected,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    )
}

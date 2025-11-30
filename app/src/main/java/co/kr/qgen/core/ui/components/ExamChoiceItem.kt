package co.kr.qgen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.kr.qgen.core.ui.theme.ExamColors
import co.kr.qgen.core.ui.theme.ExamTypography
import co.kr.qgen.core.util.unescapeString

/**
 * ExamChoiceItem - 모의고사 스타일 선택지
 * 원형 테두리 안에 숫자 + 지문 텍스트
 * 선택되면 원의 배경과 텍스트 색 반전
 */
@Composable
fun ExamChoiceItem(
    number: String, // "1", "2", "3", "4", "5"
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 원형 숫자 (선택시 원의 배경/텍스트 반전)
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    color = if (isSelected) ExamColors.ExamTextPrimary else Color.Transparent
                )
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = ExamColors.ExamChoiceCircle,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = ExamTypography.examChoiceTextStyle.copy(
                    fontSize = ExamTypography.examChoiceTextStyle.fontSize * 0.85f
                ),
                color = if (isSelected) ExamColors.ExamCardBackground else ExamColors.ExamTextPrimary
            )
        }

        // 지문 텍스트
        Text(
            text = text.unescapeString(),
            style = ExamTypography.examChoiceTextStyle,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 선택지 ID를 일반 숫자로 변환 (A->1, B->2, ...)
 */
fun getCircledNumber(choiceId: String): String {
    return when (choiceId.uppercase()) {
        "A" -> "1"
        "B" -> "2"
        "C" -> "3"
        "D" -> "4"
        "E" -> "5"
        else -> choiceId
    }
}

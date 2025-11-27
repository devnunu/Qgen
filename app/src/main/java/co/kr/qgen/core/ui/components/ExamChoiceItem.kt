package co.kr.qgen.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import co.kr.qgen.core.ui.theme.ExamColors
import co.kr.qgen.core.ui.theme.ExamTypography
import co.kr.qgen.core.ui.theme.examChoiceHighlight

/**
 * ExamChoiceItem - 모의고사 스타일 선택지
 * ① ② ③ ④ ⑤ 동그라미 숫자 + 지문 텍스트
 */
@Composable
fun ExamChoiceItem(
    number: String, // ①, ②, ③, ④, ⑤
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .examChoiceHighlight(isSelected)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 동그라미 숫자
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
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
                    fontSize = ExamTypography.examChoiceTextStyle.fontSize * 0.9f
                ),
                color = ExamColors.ExamTextPrimary
            )
        }
        
        // 지문 텍스트
        Text(
            text = text,
            style = ExamTypography.examChoiceTextStyle,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 선택지 ID를 동그라미 숫자로 변환
 */
fun getCircledNumber(choiceId: String): String {
    return when (choiceId.uppercase()) {
        "A" -> "①"
        "B" -> "②"
        "C" -> "③"
        "D" -> "④"
        "E" -> "⑤"
        else -> choiceId
    }
}

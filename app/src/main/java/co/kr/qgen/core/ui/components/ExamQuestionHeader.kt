package co.kr.qgen.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.kr.qgen.core.ui.theme.ExamTypography

/**
 * ExamQuestionHeader - 모의고사 스타일 문제 헤더
 * "18. 지문 [4점]" 형식
 */
@Composable
fun ExamQuestionHeader(
    number: Int,
    questionText: String,
    points: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 문제 번호 (Serif Bold)
        Text(
            text = "$number.",
            style = ExamTypography.examNumberTextStyle
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 본문 제목 + 점수
        Text(
            text = "$questionText [${points}점]",
            style = ExamTypography.examBodyTextStyle,
            modifier = Modifier.weight(1f)
        )
    }
}

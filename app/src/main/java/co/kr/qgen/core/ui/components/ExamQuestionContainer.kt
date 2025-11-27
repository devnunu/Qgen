package co.kr.qgen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.kr.qgen.core.ui.theme.ExamColors
import co.kr.qgen.core.ui.theme.ExamDimensions
import co.kr.qgen.core.ui.theme.examBorder

/**
 * ExamQuestionContainer - 모의고사 스타일 문제 컨테이너
 * 굵은 테두리(2dp) + 넓은 내부 padding
 */
@Composable
fun ExamQuestionContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ExamColors.ExamCardBackground)
            .examBorder()
            .padding(ExamDimensions.ItemSpacing)
    ) {
        Column(content = content)
    }
}

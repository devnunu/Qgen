package co.kr.qgen.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Symbols 'bolt' icon
val BoltIcon: ImageVector = ImageVector.Builder(
    name = "Bolt",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.White),
        fillAlpha = 1.0f,
        stroke = null,
        strokeAlpha = 1.0f,
        strokeLineWidth = 1.0f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter,
        strokeLineMiter = 1.0f,
        pathFillType = PathFillType.NonZero
    ) {
        // Google Material Symbols 'bolt' path
        moveTo(11f, 21f)
        lineTo(12f, 14f)
        lineTo(8f, 14f)
        lineTo(13f, 3f)
        lineTo(12f, 10f)
        lineTo(16f, 10f)
        lineTo(11f, 21f)
        close()
    }
}.build()

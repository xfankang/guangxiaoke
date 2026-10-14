package com.jianke.schedule.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 「关于」用的图标：一个圆圈里面一个感叹号。 */
val AboutCircleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AboutCircle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 圆环
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
        ) {
            moveTo(12f, 3f)
            arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = 18f)
            arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = -18f)
            close()
        }
        // 感叹号的一竖
        path(fill = SolidColor(Color.Black)) {
            moveTo(11.15f, 7.2f)
            lineTo(12.85f, 7.2f)
            lineTo(12.85f, 13.2f)
            lineTo(11.15f, 13.2f)
            close()
        }
        // 感叹号的一点
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 14.9f)
            arcToRelative(0.95f, 0.95f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = 1.9f)
            arcToRelative(0.95f, 0.95f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = -1.9f)
            close()
        }
    }.build()
}

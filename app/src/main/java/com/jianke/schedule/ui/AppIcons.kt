package com.jianke.schedule.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 分享图标：三个圆点两条连线（Material share 造型）。 */
val ShareIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ShareNodes",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val stroke = SolidColor(Color.Black)
        // 三个节点圆：右上 / 左中 / 右下
        path(fill = null, stroke = stroke, strokeLineWidth = 1.8f) {
            moveTo(18f, 2.4f)
            arcToRelative(2.6f, 2.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = 5.2f)
            arcToRelative(2.6f, 2.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = -5.2f)
            close()
        }
        path(fill = null, stroke = stroke, strokeLineWidth = 1.8f) {
            moveTo(6f, 9.4f)
            arcToRelative(2.6f, 2.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = 5.2f)
            arcToRelative(2.6f, 2.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = -5.2f)
            close()
        }
        path(fill = null, stroke = stroke, strokeLineWidth = 1.8f) {
            moveTo(18f, 16.4f)
            arcToRelative(2.6f, 2.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = 5.2f)
            arcToRelative(2.6f, 2.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = -5.2f)
            close()
        }
        // 两条连线：把节点圆串起来
        path(fill = null, stroke = stroke, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(8.2f, 10.9f)
            lineTo(15.8f, 6.6f)
            moveTo(8.2f, 13.1f)
            lineTo(15.8f, 17.4f)
        }
    }.build()
}

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

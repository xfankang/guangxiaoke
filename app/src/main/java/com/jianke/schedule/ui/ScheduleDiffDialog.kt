package com.jianke.schedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jianke.schedule.data.Course
import com.jianke.schedule.data.ScheduleDiff
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val DAY_CN = "一二三四五六日"

/**
 * 自动检查发现课表有变化时弹出来，直接告诉用户「变了什么」。
 *
 * 学校调课大多只是换教室或改周次，如果只说「课表有更新」，用户还得自己一格格找。
 */
@Composable
fun ScheduleDiffDialog(diff: ScheduleDiff, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "课表有更新", style = MiuixTheme.textStyles.title3)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = diff.summary(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (diff.added.isNotEmpty()) {
                        GroupTitle("新增")
                        diff.added.forEach { Line(it.name, slotOf(it) + "  " + it.room) }
                    }
                    if (diff.changed.isNotEmpty()) {
                        if (diff.added.isNotEmpty()) Spacer(Modifier.height(10.dp))
                        GroupTitle("有变动")
                        diff.changed.forEach { Line(it.name, it.detail) }
                    }
                    if (diff.removed.isNotEmpty()) {
                        if (diff.added.isNotEmpty() || diff.changed.isNotEmpty()) Spacer(Modifier.height(10.dp))
                        GroupTitle("已移除")
                        diff.removed.forEach { Line(it.name, slotOf(it)) }
                    }
                }

                Spacer(Modifier.height(18.dp))
                TextButton(
                    text = "知道了",
                    onClick = onDismiss,
                    modifier = Modifier.width(200.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun Line(name: String, detail: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = name, style = MiuixTheme.textStyles.body2)
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

private fun slotOf(c: Course): String =
    "周" + DAY_CN.getOrElse(c.dayOfWeek - 1) { '?' } + " 第 " + c.periodText + " 节"

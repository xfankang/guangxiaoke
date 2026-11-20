package com.jianke.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 学期周次校正弹窗。
 *
 * 用「现在是第几周」而不是「开学日期」来校正：学生一定知道自己第几周
 * （课表页头就写着），但从日历里挑开学日期反而是反直觉的。保存时把周次
 * 折算成开学日期（本周一 - (周次-1) 周）存下来，之后仍按日期自动前进。
 */
@Composable
fun SemesterCalibrationDialog(
    currentWeek: Int,
    totalWeeks: Int,
    onDismiss: () -> Unit,
    onConfirm: (week: Int, totalWeeks: Int) -> Unit,
) {
    val maxTotal = totalWeeks.coerceIn(1, 30)
    var total by remember { mutableIntStateOf(maxTotal) }
    var week by remember { mutableIntStateOf(currentWeek.coerceIn(1, maxTotal)) }
    // 总周数被调小后，当前周次不能超过它（避免存出「第 20 周 / 共 18 周」）
    LaunchedEffect(total) {
        if (week > total) week = total
    }
    val previewStart = remember(week) {
        LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks((week - 1).toLong())
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "校正学期周次",
                    style = MiuixTheme.textStyles.title3,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "课表页显示第几周，这里就选第几周。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "当前周次",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        NumberPicker(
                            value = week,
                            onValueChange = { week = it },
                            modifier = Modifier.width(120.dp),
                            range = 1..total,
                            label = { "第 " + it + " 周" },
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "学期总周数",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        NumberPicker(
                            value = total,
                            onValueChange = { total = it },
                            modifier = Modifier.width(120.dp),
                            range = 1..30,
                            label = { it.toString() + " 周" },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "校正后开学日期：" + previewStart,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onConfirm(week, total) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "保存",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

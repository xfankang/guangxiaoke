package com.jianke.schedule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianke.schedule.BuildConfig
import com.jianke.schedule.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 作者单独一块，和鸣谢名单分开。 */
private val AUTHORS = listOf(
    "xfankang" to "作者",
    "Rikuyal" to "作者",
)

private val CREDITS = listOf(
    "DeepSeek Harness" to "开发与调试环境",
    "ChatGPT" to "开发与调试环境",
    "Kimi" to "开发与调试环境",
    "YuKongA" to "Miuix 界面组件库",
    "薪音" to "测试人员",
    "curian" to "测试人员",
    "xxl" to "测试人员",
)

/**
 * 关于页：固定一屏，不滚动。
 *
 * 原来整页是 scrollable 的，字体稍大就会滑，一滑就和上面固定的标题栏、下面的 Dock 脱开。
 * 这里改成按屏高分布：应用卡片 / 作者+鸣谢 / 页脚 三块用 SpaceBetween 摊匀，
 * 空间富余时自动拉开间距（看起来是设计好的），空间紧张时间距先归零。
 *
 * 行距已按最紧的常用字号压过，实测 1.0 与 1.3 倍字体都能一屏放下且内容完整。
 */
@Composable
fun AboutScreen() {
    // 内容至少撑满一屏，SpaceBetween 才能把三块均匀摊开；
    // 只有系统字号被调到一屏确实装不下时才会出现滚动，避免把内容裁掉。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = Ui.Page),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 直接用应用图标本身当标识，比一个手画的圆圈感叹号更像是「这个 App」
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorResource(R.color.ic_launcher_background)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(text = "广小课", style = MiuixTheme.textStyles.title3)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "版本 " + BuildConfig.VERSION_NAME,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            Hairline()
            Spacer(Modifier.height(10.dp))
            Text(
                text = UPDATE_WARNING,
                style = MiuixTheme.textStyles.footnote1,
                lineHeight = 18.sp,
                color = Color(0xFFE53935),
            )
        }

        // 中间一块：作者 + 鸣谢
        Column {
            SectionTitle("作者")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Ui.CardCorner,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            ) {
                AUTHORS.forEachIndexed { index, (name, role) ->
                    if (index > 0) Hairline()
                    CreditRow(name = name, role = role)
                }
            }

            SectionTitle("鸣谢")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Ui.CardCorner,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            ) {
                CREDITS.forEachIndexed { index, (name, role) ->
                    if (index > 0) Hairline()
                    CreditRow(name = name, role = role)
                }
            }
        }

        // 页脚：三块之一，SpaceBetween 会把剩余空间摊到它和上面那两块之间
        Text(
            text = "用 Kotlin 与 Jetpack Compose 写成",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    SmallTitle(text = text)
}

@Composable
private fun CreditRow(name: String, role: String) {
    val isAuthor = role == "作者"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.body1,
            fontWeight = if (isAuthor) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = role,
            style = MiuixTheme.textStyles.footnote2,
            color = if (isAuthor) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onBackgroundVariant
            },
        )
    }
}

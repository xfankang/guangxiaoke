package com.jianke.schedule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

private val CREDITS = listOf(
    "DeepSeek Harness" to "开发与调试环境",
    "YuKongA" to "Miuix 界面组件库",
    "薪音" to "测试",
    "curian" to "测试",
    "xxl" to "测试",
    "xfankang" to "作者",
)

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(4.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Ui.Page),
            cornerRadius = 18.dp,
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 直接用应用图标本身当标识，比一个手画的圆圈感叹号更像是「这个 App」
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(18.dp))
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
                Spacer(Modifier.width(15.dp))
                Column {
                    Text(text = "广小课", style = MiuixTheme.textStyles.title3)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "版本 " + BuildConfig.VERSION_NAME,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
            Spacer(Modifier.height(15.dp))
            Hairline()
            Spacer(Modifier.height(13.dp))
            Text(
                text = UPDATE_WARNING,
                style = MiuixTheme.textStyles.footnote1,
                lineHeight = 19.sp,
                color = Color(0xFFE53935),
            )
        }

        SectionTitle("鸣谢")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Ui.Page),
            cornerRadius = Ui.CardCorner,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        ) {
            CREDITS.forEachIndexed { index, (name, role) ->
                if (index > 0) Hairline()
                CreditRow(name = name, role = role)
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            text = "用 Kotlin 与 Jetpack Compose 写成",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    SmallTitle(
        text = text,
        modifier = Modifier.padding(horizontal = Ui.Page),
    )
}

@Composable
private fun CreditRow(name: String, role: String) {
    val isAuthor = role == "作者"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
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

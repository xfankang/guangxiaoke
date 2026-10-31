package com.jianke.schedule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
 * 系统字号超过这个倍数时改用「自然高度 + 滚动」。
 * 再往上每一行都塞不进按比例分到的高度，分隔线会压到文字上。
 */
private const val STRETCH_MAX_FONT_SCALE = 1.35f

/**
 * 关于页。
 *
 * 默认（1.0～1.3 倍字号）是一屏固定、不滚动的：应用信息 / 作者 / 鸣谢 / 页脚 按内容比例分配高度，
 * 两张名单卡片自己撑开、行在卡内等分——这样卡片是「铺满」的，不会出现卡片很小、上下大片留白。
 *
 * 系统字号调到 1.35 倍以上时，一屏已经塞不下自然排版的九行名单，
 * 强行等分会把分隔线压到字上；这时回退成自然高度 + 允许滚动，宁可滑也不能压字或丢内容。
 */
@Composable
fun AboutScreen() {
    val stretch = LocalDensity.current.fontScale <= STRETCH_MAX_FONT_SCALE

    val modifier = Modifier
        .fillMaxSize()
        .then(if (stretch) Modifier else Modifier.verticalScroll(rememberScrollState()))
        .padding(horizontal = Ui.Page)

    Column(modifier = modifier) {
        AppInfoCard()

        SectionTitle("作者")
        if (stretch) {
            // 权重按行数分：作者 2 行、鸣谢 7 行，两张卡加起来正好吃掉剩余高度
            StretchCreditCard(items = AUTHORS, modifier = Modifier.weight(AUTHORS.size.toFloat()))
        } else {
            NaturalCreditCard(items = AUTHORS)
        }

        SectionTitle("鸣谢")
        if (stretch) {
            StretchCreditCard(items = CREDITS, modifier = Modifier.weight(CREDITS.size.toFloat()))
        } else {
            NaturalCreditCard(items = CREDITS)
        }

        Text(
            text = "用 Kotlin 与 Jetpack Compose 写成",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (stretch) 10.dp else 20.dp),
        )
    }
}

/** 顶部应用信息卡：图标、名称、版本，以及那句红字提醒。 */
@Composable
private fun AppInfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
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
            lineHeight = 19.sp,
            color = Color(0xFFE53935),
        )
    }
}

/** 撑满卡片：整体高度由外部 weight 决定，内部每一行等分剩余高度并垂直居中。 */
@Composable
private fun StretchCreditCard(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Ui.CardCorner,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            items.forEachIndexed { index, (name, role) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CreditRow(name = name, role = role, contentPadding = 2.dp)
                }
                if (index < items.lastIndex) Hairline()
            }
        }
    }
}

/** 自然高度卡片：大字号时用，行按自身内容排版，整页可滚动。 */
@Composable
private fun NaturalCreditCard(items: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Ui.CardCorner,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    ) {
        items.forEachIndexed { index, (name, role) ->
            if (index > 0) Hairline()
            CreditRow(name = name, role = role, contentPadding = 12.dp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    SmallTitle(text = text)
}

/** 一行名单：名字靠左、角色靠右，两者垂直居中对齐。 */
@Composable
private fun CreditRow(
    name: String,
    role: String,
    contentPadding: androidx.compose.ui.unit.Dp,
) {
    val isAuthor = role == "作者"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.body1,
            fontWeight = if (isAuthor) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
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

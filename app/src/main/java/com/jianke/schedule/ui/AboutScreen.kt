package com.jianke.schedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jianke.schedule.BuildConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SmallTitle(
            text = "关于",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AboutCircleIcon,
                    contentDescription = null,
                    modifier = Modifier.width(28.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(text = "广小课", style = MiuixTheme.textStyles.title4)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "版本 " + BuildConfig.VERSION_NAME,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SmallTitle(
            text = "鸣谢",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            CreditRow(name = "DeepSeek Harness", role = "开发与调试环境")
            Spacer(Modifier.height(10.dp))
            CreditRow(name = "YuKongA", role = "Miuix 界面组件库")
            Spacer(Modifier.height(10.dp))
            CreditRow(name = "薪音", role = "测试")
            Spacer(Modifier.height(10.dp))
            CreditRow(name = "curian", role = "测试")
            Spacer(Modifier.height(10.dp))
            CreditRow(name = "xxl", role = "测试")
            Spacer(Modifier.height(10.dp))
            CreditRow(name = "xfankang", role = "作者")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CreditRow(name: String, role: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MiuixTheme.textStyles.body1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = role,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

package com.hlju.funlinkbluetooth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hlju.funlinkbluetooth.core.NearbyApp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppDetailScreen(
    app: NearbyApp,
    currentQuality: Int,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val showQualityWarning = !app.canRunWithQuality(currentQuality)
    val backdrop = rememberPageBackdrop()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                modifier = Modifier.defaultBlurSurface(backdrop),
                color = Color.Transparent,
                title = app.displayName,
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        backgroundColor = Color.Transparent,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        contentWindowInsets = defaultPageWindowInsets(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .layerBackdrop(backdrop),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showQualityWarning) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "带宽不足：当前 ${qualityLabel(currentQuality)}，需要 ${qualityLabel(app.requiredQuality)}",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                app.Content()
            }
        }
    }
}

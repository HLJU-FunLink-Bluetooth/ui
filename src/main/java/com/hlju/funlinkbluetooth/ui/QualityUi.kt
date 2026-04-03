package com.hlju.funlinkbluetooth.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.android.gms.nearby.connection.BandwidthInfo
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun qualityLabel(quality: Int): String = when (quality) {
    BandwidthInfo.Quality.HIGH -> "高"
    BandwidthInfo.Quality.MEDIUM -> "中"
    BandwidthInfo.Quality.LOW -> "低"
    else -> "未知"
}

@Composable
internal fun qualityColor(quality: Int): Color {
    val primary = MiuixTheme.colorScheme.primary
    return when (quality) {
        BandwidthInfo.Quality.HIGH -> primary
        BandwidthInfo.Quality.MEDIUM -> primary.copy(alpha = 0.72f)
        BandwidthInfo.Quality.LOW -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onBackgroundVariant
    }
}

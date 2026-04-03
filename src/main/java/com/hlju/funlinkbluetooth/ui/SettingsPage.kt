package com.hlju.funlinkbluetooth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SettingsPage(
    bottomInset: Dp,
    themeMode: AppThemeMode,
    onOpenThemeSettings: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBackdrop()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBlurSurface(backdrop),
                color = Color.Transparent,
                title = "设置",
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = defaultPageWindowInsets()
    ) { innerPadding ->
        SettingsHomeContent(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .layerBackdrop(backdrop)
                .padding(horizontal = 12.dp),
            topPadding = innerPadding.calculateTopPadding() + 12.dp,
            bottomPadding = innerPadding.calculateBottomPadding() + bottomInset + 12.dp,
            currentThemeText = themeMode.displayName(),
            onOpenThemePage = onOpenThemeSettings
        )
    }
}

@Composable
fun ThemeSettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBackdrop()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                modifier = Modifier.defaultBlurSurface(backdrop),
                color = Color.Transparent,
                title = "主题设置",
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        backgroundColor = Color.Transparent
                    ) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        contentWindowInsets = defaultPageWindowInsets()
    ) { innerPadding ->
        ThemeSettingsContent(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .layerBackdrop(backdrop)
                .padding(horizontal = 12.dp),
            topPadding = innerPadding.calculateTopPadding() + 12.dp,
            bottomPadding = innerPadding.calculateBottomPadding() + 12.dp,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange
        )
    }
}

@Composable
private fun SettingsHomeContent(
    modifier: Modifier,
    topPadding: Dp,
    bottomPadding: Dp,
    currentThemeText: String,
    onOpenThemePage: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = topPadding,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = null
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "主题设置",
                    summary = currentThemeText,
                    onClick = onOpenThemePage,
                )
            }
        }
    }
}

@Composable
private fun ThemeSettingsContent(
    modifier: Modifier,
    topPadding: Dp,
    bottomPadding: Dp,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = topPadding,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = null
    ) {
        item {
            SmallTitle(text = "显示模式")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        RadioButtonPreference(
                            title = mode.displayName(),
                            summary = if (mode == themeMode) "当前正在使用" else null,
                            selected = mode == themeMode,
                            onClick = {
                                if (mode != themeMode) {
                                    onThemeModeChange(mode)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun AppThemeMode.displayName(): String = when (this) {
    AppThemeMode.System -> "跟随系统"
    AppThemeMode.Light -> "浅色"
    AppThemeMode.Dark -> "深色"
}

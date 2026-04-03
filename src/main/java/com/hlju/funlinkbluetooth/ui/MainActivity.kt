package com.hlju.funlinkbluetooth.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kyant.backdrop.backdrops.layerBackdrop as legacyLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberLegacyLayerBackdrop
import com.hlju.funlinkbluetooth.core.AppRegistry
import com.hlju.funlinkbluetooth.core.NearbyApp
import com.hlju.funlinkbluetooth.ui.component.FloatingBottomBar
import com.hlju.funlinkbluetooth.ui.component.FloatingBottomBarItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

enum class AppThemeMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromStorage(value: String?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: System
        }
    }
}

private const val APP_PREFS_NAME = "funlink_settings"
private const val PREF_THEME_MODE = "theme_mode"

fun ComponentActivity.setFunLinkContent() {
    AppRegistry.setActiveApp(AppRegistry.apps.firstOrNull()?.id)

    setContent {
        val prefs = remember {
            getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
        }
        var themeMode by remember {
            mutableStateOf(
                AppThemeMode.fromStorage(
                    prefs.getString(PREF_THEME_MODE, null),
                ),
            )
        }
        val systemDarkMode = isSystemInDarkTheme()
        val darkMode = when (themeMode) {
            AppThemeMode.System -> systemDarkMode
            AppThemeMode.Light -> false
            AppThemeMode.Dark -> true
        }
        DisposableEffect(darkMode) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { darkMode },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { darkMode },
            )
            window.isNavigationBarContrastEnforced = false
            onDispose {}
        }

        val themeController = remember(themeMode) {
            ThemeController(
                colorSchemeMode = ColorSchemeMode.System,
                isDark = when (themeMode) {
                    AppThemeMode.System -> null
                    AppThemeMode.Light -> false
                    AppThemeMode.Dark -> true
                },
            )
        }
        MiuixTheme(
            controller = themeController,
            smoothRounding = true,
        ) {
            AppContent(
                themeMode = themeMode,
                onThemeModeChange = { newMode ->
                    if (newMode != themeMode) {
                        themeMode = newMode
                        prefs.edit { putString(PREF_THEME_MODE, newMode.storageValue) }
                    }
                },
            )
        }
    }
}

private data class TabItem(
    val icon: ImageVector,
    val label: String,
)

@Composable
private fun AppContent(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val nearbyState = remember(context) { NearbyUiState(context) }
    val currentQuality by nearbyState.currentQuality.collectAsState()
    val tabs = remember {
        listOf(
            TabItem(MiuixIcons.Link, "设备"),
            TabItem(MiuixIcons.GridView, "游戏中心"),
            TabItem(MiuixIcons.Settings, "设置"),
        )
    }
    val backStack = remember {
        mutableStateListOf<UiRoute>(UiRoute.Home)
    }
    val currentRoute by remember {
        derivedStateOf { backStack.lastOrNull() ?: UiRoute.Home }
    }

    fun popRoute() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    BackHandler(enabled = backStack.size > 1) {
        popRoute()
    }

    DisposableEffect(nearbyState) {
        AppRegistry.bindConnectionsClientProvider { nearbyState.connectionsClient }
        AppRegistry.bindConnectedEndpointsProvider { nearbyState.connectedEndpoints.value }
        AppRegistry.bindCurrentQualityProvider { nearbyState.currentQuality.value }
        onDispose {
            nearbyState.connectionsClient.stopAdvertising()
            nearbyState.connectionsClient.stopDiscovery()
            nearbyState.connectionsClient.stopAllEndpoints()
            nearbyState.resetConnections()
            nearbyState.clearLastError()
            nearbyState.setRole(NearbyUiState.Role.HOST)
        }
    }

    LaunchedEffect(currentRoute) {
        nearbyState.allowDisruptiveUpgrade.value = currentRoute !is UiRoute.AppDetail
    }

    LaunchedEffect(currentRoute) {
        val route = currentRoute
        val fallbackAppId = AppRegistry.apps.firstOrNull()?.id
        val targetAppId = when (route) {
            is UiRoute.AppDetail -> route.appId
            else -> fallbackAppId
        }
        AppRegistry.setActiveApp(targetAppId)
    }

    val routes = entryProvider<UiRoute> {
        entry<UiRoute.Home> {
            HomeScreen(
                themeMode = themeMode,
                nearbyState = nearbyState,
                currentQuality = currentQuality,
                tabs = tabs,
                onAppClick = { app ->
                    backStack.add(UiRoute.AppDetail(app.id))
                },
                onOpenThemeSettings = {
                    backStack.add(UiRoute.SettingsTheme)
                },
            )
        }
        entry<UiRoute.AppDetail> { route ->
            val app = AppRegistry.apps.firstOrNull { it.id == route.appId }
            if (app == null) {
                MissingAppScreen(onBack = ::popRoute)
            } else {
                AppDetailScreen(
                    app = app,
                    currentQuality = currentQuality,
                    onBack = ::popRoute,
                )
            }
        }
        entry<UiRoute.SettingsTheme> {
            ThemeSettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onBack = ::popRoute,
            )
        }
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = routes,
    )

    NavDisplay(
        entries = entries,
        onBack = ::popRoute,
    )
}

@Composable
private fun HomeScreen(
    themeMode: AppThemeMode,
    nearbyState: NearbyUiState,
    currentQuality: Int,
    tabs: List<TabItem>,
    onAppClick: (NearbyApp) -> Unit,
    onOpenThemeSettings: () -> Unit,
) {
    val systemDarkMode = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.System -> systemDarkMode
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    var bottomBarTargetPage by remember { mutableStateOf(pagerState.currentPage) }
    var pagerScrollJob by remember { mutableStateOf<Job?>(null) }
    val backdrop = rememberPageBackdrop()
    val bottomBarBackdrop = rememberLegacyLayerBackdrop()
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingBarBottomPadding = 12.dp + navBottomPadding

    fun navigateToPage(index: Int) {
        if (index == bottomBarTargetPage) return
        bottomBarTargetPage = index
        if (index == pagerState.currentPage && !pagerState.isScrollInProgress) return
        pagerScrollJob?.cancel()
        pagerScrollJob = scope.launch {
            try {
                pagerState.animateScrollToPage(index)
            } catch (_: CancellationException) {
                // Navigation animation interrupted by a newer navigation request.
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow {
            if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
        }.collect { page ->
            if (bottomBarTargetPage != page) bottomBarTargetPage = page
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != 0) {
                val client = nearbyState.connectionsClient
                if (nearbyState.isDiscovering.value) {
                    client.stopDiscovery()
                    nearbyState.onDiscoveryStopped()
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                FloatingBottomBar(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .align(Alignment.BottomCenter)
                        .padding(bottom = floatingBarBottomPadding),
                    selectedIndex = { bottomBarTargetPage },
                    onSelected = ::navigateToPage,
                    backdrop = bottomBarBackdrop,
                    tabsCount = tabs.size,
                    isBlurEnabled = true,
                    isDark = isDark,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val tabColor = MiuixTheme.colorScheme.onSurface
                        FloatingBottomBarItem(
                            onClick = { navigateToPage(index) },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tabColor,
                            )
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = tabColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = defaultPageWindowInsets(),
    ) { innerPadding ->
        val pageBottomInset = innerPadding.calculateBottomPadding()
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .legacyLayerBackdrop(bottomBarBackdrop)
                .layerBackdrop(backdrop),
        ) { page ->
            when (page) {
                0 -> DevicesPage(
                    nearbyState = nearbyState,
                    bottomInset = pageBottomInset,
                )

                1 -> CenterPage(
                    apps = AppRegistry.apps,
                    currentQuality = currentQuality,
                    bottomInset = pageBottomInset,
                    onAppClick = onAppClick,
                )

                else -> SettingsPage(
                    bottomInset = pageBottomInset,
                    themeMode = themeMode,
                    onOpenThemeSettings = onOpenThemeSettings,
                )
            }
        }
    }
}

@Composable
private fun MissingAppScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = defaultPageWindowInsets(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "程序不存在或不可用",
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onBack),
            )
        }
    }
}

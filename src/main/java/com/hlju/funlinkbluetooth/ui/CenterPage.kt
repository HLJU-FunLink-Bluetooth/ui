package com.hlju.funlinkbluetooth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hlju.funlinkbluetooth.core.NearbyApp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val PageSpacing = 12.dp
private val ItemHorizontalPadding = 14.dp
private val ItemVerticalPadding = 12.dp
private val ItemGap = 12.dp
private val ItemIconSize = 36.dp
private val ChevronBaseSize = 18f

private fun Modifier.animatedFullWidthCard(): Modifier {
    return fillMaxWidth().animateContentSize()
}

@Composable
fun CenterPage(
    apps: List<NearbyApp>,
    currentQuality: Int,
    bottomInset: Dp,
    onAppClick: (NearbyApp) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val filteredApps by remember(apps, query) {
        derivedStateOf {
            if (query.isBlank()) {
                apps
            } else {
                apps.filter { app ->
                    app.displayName.contains(query, ignoreCase = true) ||
                        app.name.contains(query, ignoreCase = true)
                }
            }
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBackdrop()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBlurSurface(backdrop),
                color = Color.Transparent,
                title = "游戏中心",
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = defaultPageWindowInsets()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .layerBackdrop(backdrop)
                .padding(horizontal = PageSpacing),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + PageSpacing,
                bottom = innerPadding.calculateBottomPadding() + bottomInset + PageSpacing
            ),
            verticalArrangement = Arrangement.spacedBy(PageSpacing),
            overscrollEffect = null
        ) {
            item {
                SearchSection(
                    query = query,
                    expanded = searchExpanded,
                    onQueryChange = { query = it },
                    onExpandedChange = { searchExpanded = it }
                )
            }

            // Bandwidth quality indicator
            item {
                AnimatedVisibility(
                    visible = currentQuality > 0,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val qualityTone by animateColorAsState(
                        targetValue = qualityColor(currentQuality),
                        animationSpec = spring(),
                        label = "qualityTone"
                    )
                    Card(
                        modifier = Modifier
                            .animatedFullWidthCard()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "当前带宽",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                            Text(
                                text = qualityLabel(currentQuality),
                                fontWeight = FontWeight.Bold,
                                color = qualityTone
                            )
                        }
                    }
                }
            }

            if (filteredApps.isEmpty()) {
                item {
                    EmptyAppsCard(
                        text = if (query.isBlank()) "暂无可用程序" else "未找到匹配程序"
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier
                            .animatedFullWidthCard()
                    ) {
                        Column {
                            filteredApps.forEachIndexed { index, app ->
                                val canRun = app.canRunWithQuality(currentQuality)
                                AppRow(
                                    app = app,
                                    canRun = canRun,
                                    onClick = { onAppClick(app) }
                                )
                                if (index < filteredApps.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 60.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSection(
    query: String,
    expanded: Boolean,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit
) {
    SearchBar(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = DpSize(0.dp, 0.dp),
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                label = "输入程序名称"
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {}
}

@Composable
private fun AppRow(
    app: NearbyApp,
    canRun: Boolean,
    onClick: () -> Unit
) {
    val titleTone by animateColorAsState(
        targetValue = if (canRun) {
            MiuixTheme.colorScheme.onSurface
        } else {
            MiuixTheme.colorScheme.onBackgroundVariant
        },
        animationSpec = spring(),
        label = "titleTone"
    )
    val chevronScale by animateFloatAsState(
        targetValue = if (canRun) 1f else 0.8f,
        animationSpec = spring(),
        label = "chevronScale"
    )

    Card(
        modifier = Modifier
            .animatedFullWidthCard(),
        showIndication = canRun,
        onClick = if (canRun) onClick else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ItemHorizontalPadding, vertical = ItemVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap)
        ) {
            app.AppIcon(modifier = Modifier.size(ItemIconSize))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.displayName,
                    style = MiuixTheme.textStyles.main,
                    color = titleTone,
                    fontWeight = FontWeight.Medium
                )
                AppRequirementHint(requiredQuality = app.requiredQuality, canRun = canRun)
            }
            AnimatedVisibility(
                visible = canRun,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = "进入程序",
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.size((ChevronBaseSize * chevronScale).dp)
                )
            }
        }
    }
}

@Composable
private fun AppRequirementHint(requiredQuality: Int, canRun: Boolean) {
    val hintText = when {
        !canRun -> "带宽不足（需要：${qualityLabel(requiredQuality)}）"
        requiredQuality > 0 -> "需要：${qualityLabel(requiredQuality)}"
        else -> null
    }
    if (hintText == null) return

    Text(
        text = hintText,
        fontSize = 11.sp,
        color = if (canRun) {
            MiuixTheme.colorScheme.onBackgroundVariant
        } else {
            MiuixTheme.colorScheme.error
        },
        maxLines = 1
    )
}

@Composable
private fun EmptyAppsCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

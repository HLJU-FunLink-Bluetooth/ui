package com.hlju.funlinkbluetooth.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.BandwidthInfo
import com.google.android.gms.nearby.connection.ConnectionOptions
import com.google.android.gms.nearby.connection.ConnectionType
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.Strategy
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun DevicesPage(
    nearbyState: NearbyUiState,
    bottomInset: Dp
) {
    val context = LocalContext.current
    val status by nearbyState.status.collectAsState()
    val role by nearbyState.role.collectAsState()
    val isAdvertising by nearbyState.isAdvertising.collectAsState()
    val isDiscovering by nearbyState.isDiscovering.collectAsState()
    val hostRoomName by nearbyState.hostRoomName.collectAsState()
    val clientConnectionName by nearbyState.clientConnectionName.collectAsState()
    val connectedEndpoints by nearbyState.connectedEndpoints.collectAsState()
    val discoveredEndpoints by nearbyState.discoveredEndpoints.collectAsState()
    val pendingConnections by nearbyState.pendingConnections.collectAsState()
    val lastError by nearbyState.lastError.collectAsState()
    val endpointBandwidth by nearbyState.endpointBandwidth.collectAsState()
    val allowUpgrade by nearbyState.allowDisruptiveUpgrade.collectAsState()

    val client = nearbyState.connectionsClient

    val isHost = role == NearbyUiState.Role.HOST
    val isRefreshing = status == NearbyUiState.Status.DISCOVERING
    val connectedList = connectedEndpoints.toList().sortedBy { it.endpointName.lowercase() }
    val connectedById = connectedList.associateBy { it.endpointId }
    val discoveredList = discoveredEndpoints
        .filterNot { connectedById.containsKey(it.endpointId) }
        .sortedBy { it.endpointName.lowercase() }
    val connectedRoomName = connectedList.firstOrNull()?.endpointName.orEmpty()
    val bestLinkQuality = endpointBandwidth.values.minOfOrNull { it.quality } ?: -1
    val discoveredCount = discoveredList.size
    val pendingCount = pendingConnections.size
    val statusAccent by animateColorAsState(
        targetValue = statusColor(status),
        animationSpec = spring(),
        label = "statusAccent"
    )
    val qualityAccent by animateColorAsState(
        targetValue = qualityColor(bestLinkQuality),
        animationSpec = spring(),
        label = "qualityAccent"
    )
    val roleAccent by animateColorAsState(
        targetValue = if (role == NearbyUiState.Role.HOST) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.86f)
        },
        animationSpec = spring(),
        label = "roleAccent"
    )
    val statusIconScale by animateFloatAsState(
        targetValue = if (status == NearbyUiState.Status.CONNECTING || status == NearbyUiState.Status.ACTIVE) {
            1.08f
        } else {
            1f
        },
        animationSpec = spring(),
        label = "statusIconScale"
    )

    var roomNameInput by remember { mutableStateOf(hostRoomName) }
    var clientNameInput by remember { mutableStateOf(clientConnectionName) }
    var pendingStartHost by remember { mutableStateOf(false) }
    var pendingStartScan by remember { mutableStateOf(false) }
    var showRoleSwitchConfirmDialog by remember { mutableStateOf(false) }
    var pendingRoleSwitchTarget by remember { mutableStateOf<NearbyUiState.Role?>(null) }
    var lastHostToggleAtMs by remember { mutableLongStateOf(0L) }
    val hostToggleDebounceMs = 900L

    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    }
    var hasPermissions by remember(requiredPermissions) {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    fun runWithPermissions(isHostOperation: Boolean, action: () -> Unit) {
        if (hasPermissions) {
            action()
        } else {
            pendingStartHost = isHostOperation
            pendingStartScan = !isHostOperation
        }
    }

    fun startClientScan() {
        val normalized = clientNameInput.trim()
        clientNameInput = normalized
        nearbyState.setClientConnectionName(normalized)
        if (normalized.isBlank()) {
            nearbyState.onValidationError("请输入连接名")
            return
        }
        runWithPermissions(isHostOperation = false) {
            client.startDiscovery(
                nearbyState.serviceId,
                nearbyState.endpointDiscoveryCallback,
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
            ).addOnSuccessListener {
                nearbyState.onDiscoveryStarted()
            }.addOnFailureListener {
                nearbyState.onOperationError("扫描失败", it)
            }
        }
    }

    fun startHostBroadcast() {
        val normalized = roomNameInput.trim()
        roomNameInput = normalized
        nearbyState.setHostRoomName(normalized)
        if (normalized.isBlank()) {
            return
        }
        runWithPermissions(isHostOperation = true) {
            // Stop discovery if active
            if (isDiscovering) {
                client.stopDiscovery()
                nearbyState.onDiscoveryStopped()
            }
            client.startAdvertising(
                normalized,
                nearbyState.serviceId,
                nearbyState.connectionLifecycleCallback,
                AdvertisingOptions.Builder()
                    .setStrategy(Strategy.P2P_STAR)
                    .setConnectionType(if (allowUpgrade) ConnectionType.DISRUPTIVE else ConnectionType.NON_DISRUPTIVE)
                    .build()
            ).addOnSuccessListener {
                nearbyState.onAdvertisingStarted()
            }.addOnFailureListener {
                nearbyState.onOperationError("启动广播失败", it)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermissions = grants.all { it.value }
        if (!hasPermissions) {
            pendingStartHost = false
            pendingStartScan = false
            return@rememberLauncherForActivityResult
        }
        if (pendingStartHost) {
            pendingStartHost = false
            startHostBroadcast()
        } else if (pendingStartScan) {
            pendingStartScan = false
            startClientScan()
        }
    }

    fun applyRoleSwitch(targetRole: NearbyUiState.Role) {
        client.stopDiscovery()
        client.stopAdvertising()
        client.stopAllEndpoints()
        nearbyState.resetConnections()
        nearbyState.setRole(targetRole)
        pendingStartHost = false
        pendingStartScan = false
    }

    fun requestRoleSwitch(targetRole: NearbyUiState.Role) {
        if (targetRole == role) return
        if (connectedEndpoints.isNotEmpty()) {
            pendingRoleSwitchTarget = targetRole
            showRoleSwitchConfirmDialog = true
            return
        }
        applyRoleSwitch(targetRole)
    }

    fun rejectIncomingConnection(endpointId: String) {
        nearbyState.rejectPendingConnection(endpointId)
        client.rejectConnection(endpointId)
            .addOnFailureListener { nearbyState.onOperationError("拒绝连接失败", it) }
    }

    fun requestConnectionToEndpoint(endpointId: String) {
        if (!nearbyState.prepareOutgoingConnection(endpointId)) return
        client.requestConnection(
            nearbyState.connectionRequestName(),
            endpointId,
            nearbyState.connectionLifecycleCallback,
            createConnectionOptions(allowUpgrade)
        ).addOnFailureListener {
            nearbyState.onOutgoingConnectionFailed(endpointId, it)
        }
    }

    LaunchedEffect(requiredPermissions) {
        pendingStartHost = false
        pendingStartScan = false
        if (!hasPermissions) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(hostRoomName) {
        if (roomNameInput != hostRoomName) roomNameInput = hostRoomName
    }

    LaunchedEffect(clientConnectionName) {
        if (clientNameInput != clientConnectionName) clientNameInput = clientConnectionName
    }

    DisposableEffect(Unit) {
        onDispose {
            client.stopDiscovery()
            nearbyState.onDiscoveryStopped()
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBackdrop()
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.defaultBlurSurface(backdrop),
                color = Color.Transparent,
                title = "趣连蓝牙",
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
                .padding(horizontal = 10.dp)
                .layerBackdrop(backdrop),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 10.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomInset + 10.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            overscrollEffect = null
        ) {
            // Status dashboard
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val gap = 10.dp
                    val columnWidth = (maxWidth - gap) / 2
                    val smallCardHeight = (columnWidth - gap) / 2
                    val rowHeight = columnWidth
                    val cardScale = (columnWidth / 180.dp).coerceIn(0.78f, 1.15f)
                    val leftStatusSize = (24f * cardScale).coerceIn(18f, 28f).sp
                    val leftSummarySize = (11f * cardScale).coerceIn(10f, 12f).sp
                    val smallTitleSize = (12f * cardScale).coerceIn(10f, 13f).sp
                    val smallMainSize = (19f * cardScale).coerceIn(15f, 22f).sp
                    val smallCountSize = (24f * cardScale).coerceIn(18f, 28f).sp
                    val smallHintSize = (11f * cardScale).coerceIn(9f, 12f).sp

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        // Status card
                        Card(
                            modifier = Modifier
                                .width(columnWidth)
                                .fillMaxHeight(),
                            pressFeedbackType = PressFeedbackType.Tilt
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.SearchDevice,
                                                contentDescription = "状态",
                                                tint = statusAccent,
                                                modifier = Modifier.size(
                                                    (18f * cardScale * statusIconScale).coerceIn(16f, 24f).dp
                                                )
                                            )
                                            AnimatedContent(
                                                targetState = statusHeadline(status),
                                                label = "statusHeadline"
                                            ) { headline ->
                                                Text(
                                                    text = headline,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = leftStatusSize,
                                                    color = statusAccent
                                                )
                                            }
                                        }
                                        AnimatedContent(
                                            targetState = statusSummary(
                                                status = status,
                                                isHost = isHost,
                                                isAdvert = isAdvertising,
                                                hostRoomName = hostRoomName,
                                                connectedRoomName = connectedRoomName
                                            ),
                                            label = "statusSummary"
                                        ) { summary ->
                                            Text(
                                                text = summary,
                                                fontSize = leftSummarySize,
                                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        AnimatedVisibility(
                                            visible = !lastError.isNullOrBlank(),
                                            enter = fadeIn(),
                                            exit = fadeOut()
                                        ) {
                                            Text(
                                                text = lastError ?: "",
                                                fontSize = leftSummarySize,
                                                color = MiuixTheme.colorScheme.error,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "连接质量",
                                                fontSize = leftSummarySize,
                                                color = MiuixTheme.colorScheme.onBackgroundVariant
                                            )
                                            Text(
                                                text = qualityLabel(bestLinkQuality),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = leftSummarySize,
                                                color = qualityAccent
                                            )
                                        }
                                        Text(
                                            text = "发现 $discoveredCount · 待处理 $pendingCount",
                                            fontSize = leftSummarySize,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Right column: Role + Connected count
                        Column(
                            modifier = Modifier
                                .width(columnWidth)
                                .fillMaxHeight()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(smallCardHeight),
                                pressFeedbackType = PressFeedbackType.Sink,
                                showIndication = true,
                                onClick = {
                                    val target = if (role == NearbyUiState.Role.HOST) {
                                        NearbyUiState.Role.CLIENT
                                    } else {
                                        NearbyUiState.Role.HOST
                                    }
                                    requestRoleSwitch(target)
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "当前角色",
                                        fontSize = smallTitleSize,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant
                                    )
                                    Text(
                                        text = if (role == NearbyUiState.Role.HOST) "主机 Host" else "客机 Client",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = smallMainSize,
                                        color = roleAccent
                                    )
                                    Text(
                                        text = "点按切换角色",
                                        fontSize = smallHintSize,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(gap))

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(smallCardHeight),
                                pressFeedbackType = PressFeedbackType.Tilt
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (isHost) {
                                        Text(
                                            text = "已连接设备",
                                            fontSize = smallTitleSize,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant
                                        )
                                        Text(
                                            text = "${connectedList.size} 台",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (smallCountSize.value * 0.8f).sp,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (connectedList.isEmpty()) {
                                                "等待设备加入"
                                            } else {
                                                "质量 ${qualityLabel(bestLinkQuality)} · 待处理 $pendingCount"
                                            },
                                            fontSize = smallHintSize,
                                            color = if (connectedList.isEmpty()) {
                                                MiuixTheme.colorScheme.onBackgroundVariant
                                            } else {
                                                qualityAccent
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = "连接状态",
                                            fontSize = smallTitleSize,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant
                                        )
                                        Text(
                                            text = connectedRoomName.ifBlank { "未连接" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = smallMainSize,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (connectedRoomName.isBlank()) {
                                                "发现 $discoveredCount 个房间，可点击连接"
                                            } else {
                                                "链路质量 ${qualityLabel(bestLinkQuality)}"
                                            },
                                            fontSize = smallHintSize,
                                            color = if (connectedRoomName.isBlank()) {
                                                MiuixTheme.colorScheme.onBackgroundVariant
                                            } else {
                                                qualityAccent
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isHost) {
                // Host mode: room name input + broadcast button
                item {
                    NameActionCard(
                        value = roomNameInput,
                        onValueChange = { roomNameInput = it },
                        label = "房间名",
                        actionText = if (isAdvertising) "停止广播" else "开始广播",
                        hint = "房间名会自动记忆，下次可直接广播",
                        onActionClick = {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastHostToggleAtMs < hostToggleDebounceMs) return@NameActionCard
                            lastHostToggleAtMs = now
                            if (isAdvertising) {
                                client.stopAdvertising()
                                nearbyState.onAdvertisingStopped()
                                return@NameActionCard
                            }
                            startHostBroadcast()
                            if (pendingStartHost) permissionLauncher.launch(requiredPermissions)
                        }
                    )
                }

                // Connected devices list (host)
                item {
                    SectionHeader(title = "已连接设备")
                }

                item {
                    if (connectedList.isEmpty()) {
                        EmptyStateCard(text = "暂无已连接设备")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            connectedList.forEach { endpoint ->
                                val bw = endpointBandwidth[endpoint.endpointId]
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Ok,
                                            contentDescription = "设备",
                                            tint = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = endpoint.endpointName,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        if (bw != null) {
                                            Text(
                                                text = qualityLabel(bw.quality),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = qualityColor(bw.quality)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Client mode: connection name + scan button
                item {
                    NameActionCard(
                        value = clientNameInput,
                        onValueChange = {
                            clientNameInput = it
                            nearbyState.setClientConnectionName(it)
                        },
                        label = "连接名",
                        actionText = if (isRefreshing) "停止扫描" else "开始扫描",
                        hint = "连接名会自动记忆，下次可直接扫描",
                        onActionClick = {
                            if (isRefreshing) {
                                client.stopDiscovery()
                                nearbyState.onDiscoveryStopped()
                                return@NameActionCard
                            }
                            startClientScan()
                            if (pendingStartScan) permissionLauncher.launch(requiredPermissions)
                        }
                    )
                }

                // Discovered rooms
                item {
                    SectionHeader(title = "附近房间") {
                        if (isRefreshing) {
                            InfiniteProgressIndicator()
                        }
                    }
                }

                item {
                    if (discoveredList.isEmpty()) {
                        EmptyStateCard(text = "暂无附近房间")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            discoveredList.forEach { endpoint ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    pressFeedbackType = PressFeedbackType.Tilt,
                                    showIndication = true,
                                    onClick = {
                                        requestConnectionToEndpoint(endpoint.endpointId)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.SearchDevice,
                                            contentDescription = "房间",
                                            tint = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = endpoint.endpointName,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(2.dp)) }
        }

        // Accept connection dialog
        val acceptTarget = pendingConnections.firstOrNull()
        ConfirmationDialog(
            show = acceptTarget != null,
            title = "确认接受连接",
            summary = if (acceptTarget != null) {
                "是否接受来自 ${acceptTarget.endpointName} 的连接请求？"
            } else {
                "是否接受此连接请求？"
            },
            onDismissRequest = {
                val endpointId = acceptTarget?.endpointId ?: return@ConfirmationDialog
                rejectIncomingConnection(endpointId)
            },
        ) {
            DialogActionRow(
                cancelText = "取消",
                confirmText = "接受连接",
                onCancel = {
                    val endpointId = acceptTarget?.endpointId ?: return@DialogActionRow
                    rejectIncomingConnection(endpointId)
                },
                onConfirm = {
                    val endpointId = acceptTarget?.endpointId ?: return@DialogActionRow
                    if (!nearbyState.prepareAcceptConnection(endpointId)) {
                        return@DialogActionRow
                    }
                    client.acceptConnection(endpointId, nearbyState.payloadCallback)
                        .addOnFailureListener {
                            nearbyState.onAcceptConnectionFailed(endpointId, it)
                        }
                }
            )
        }

        // Role switch confirmation dialog
        val switchTarget = pendingRoleSwitchTarget
        val switchSummary = when {
            role == NearbyUiState.Role.HOST && switchTarget == NearbyUiState.Role.CLIENT ->
                "当前已连接设备，切换到 Client 会停止广播。是否继续切换？"
            role == NearbyUiState.Role.CLIENT && switchTarget == NearbyUiState.Role.HOST ->
                "当前已连接 Host，切换到 Host 前建议确认连接状态。是否继续切换？"
            else -> "当前已有连接设备，切换模式可能影响当前连接。是否继续切换？"
        }

        ConfirmationDialog(
            show = showRoleSwitchConfirmDialog && switchTarget != null,
            title = "确认切换模式",
            summary = switchSummary,
            onDismissRequest = {
                showRoleSwitchConfirmDialog = false
                pendingRoleSwitchTarget = null
            },
        ) {
            DialogActionRow(
                cancelText = "取消",
                confirmText = "确定切换",
                destructiveConfirm = true,
                onCancel = {
                    showRoleSwitchConfirmDialog = false
                    pendingRoleSwitchTarget = null
                },
                onConfirm = {
                    val target = pendingRoleSwitchTarget ?: return@DialogActionRow
                    showRoleSwitchConfirmDialog = false
                    pendingRoleSwitchTarget = null
                    applyRoleSwitch(target)
                }
            )
        }
    }
}

@Composable
private fun ConfirmationDialog(
    show: Boolean,
    title: String,
    summary: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!show) return

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                content()
            }
        }
    }
}

@Composable
private fun statusColor(status: NearbyUiState.Status): Color {
    val primary = MiuixTheme.colorScheme.primary
    return when (status) {
        NearbyUiState.Status.ACTIVE -> primary
        NearbyUiState.Status.ERROR -> MiuixTheme.colorScheme.error
        NearbyUiState.Status.ADVERTISING -> primary.copy(alpha = 0.92f)
        NearbyUiState.Status.DISCOVERING -> primary.copy(alpha = 0.75f)
        NearbyUiState.Status.CONNECTING -> primary.copy(alpha = 0.86f)
        NearbyUiState.Status.IDLE -> MiuixTheme.colorScheme.onBackgroundVariant
    }
}

private fun statusHeadline(status: NearbyUiState.Status): String = when (status) {
    NearbyUiState.Status.IDLE -> "待机"
    NearbyUiState.Status.ADVERTISING -> "广播中"
    NearbyUiState.Status.DISCOVERING -> "扫描中"
    NearbyUiState.Status.CONNECTING -> "连接中"
    NearbyUiState.Status.ACTIVE -> "连接稳定"
    NearbyUiState.Status.ERROR -> "连接异常"
}

private fun statusSummary(
    status: NearbyUiState.Status,
    isHost: Boolean,
    isAdvert: Boolean,
    hostRoomName: String,
    connectedRoomName: String
): String = when {
    status == NearbyUiState.Status.ERROR -> "连接异常，请检查权限和蓝牙状态"
    status == NearbyUiState.Status.CONNECTING -> "正在建立连接，请稍候"
    isHost && isAdvert -> "房间：${hostRoomName.ifBlank { "未命名房间" }}，等待设备加入"
    isHost -> "尚未广播，可随时开始"
    connectedRoomName.isNotBlank() -> "已连接到：$connectedRoomName"
    status == NearbyUiState.Status.DISCOVERING -> "正在搜索附近房间"
    else -> "未连接，可开始扫描"
}

private fun createConnectionOptions(allowUpgrade: Boolean): ConnectionOptions {
    return ConnectionOptions.Builder()
        .setConnectionType(
            if (allowUpgrade) ConnectionType.DISRUPTIVE
            else ConnectionType.NON_DISRUPTIVE
        )
        .build()
}

@Composable
private fun SectionHeader(
    title: String,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onBackgroundVariant
        )
        trailingContent()
    }
}

@Composable
private fun EmptyStateCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onBackgroundVariant
        )
    }
}

@Composable
private fun NameActionCard(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    actionText: String,
    hint: String,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    label = label,
                    useLabelAsPlaceholder = true,
                    singleLine = true
                )
                TextButton(
                    text = actionText,
                    onClick = onActionClick
                )
            }
            Text(
                text = hint,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }
    }
}

@Composable
private fun DialogActionRow(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    destructiveConfirm: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            text = cancelText,
            modifier = Modifier.weight(1f),
            onClick = onCancel,
        )
        TextButton(
            text = confirmText,
            modifier = Modifier.weight(1f),
            colors = if (destructiveConfirm) {
                ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.errorContainer,
                    textColor = MiuixTheme.colorScheme.error,
                )
            } else {
                ButtonDefaults.textButtonColors()
            },
            onClick = onConfirm,
        )
    }
}


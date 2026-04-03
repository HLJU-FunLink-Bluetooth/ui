package com.hlju.funlinkbluetooth.ui

import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.BandwidthInfo
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionType
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.hlju.funlinkbluetooth.core.AppRegistry
import com.hlju.funlinkbluetooth.core.NearbyError
import com.hlju.funlinkbluetooth.core.NearbyEndpointInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class NearbyUiState(context: Context) {

    enum class Status { IDLE, ADVERTISING, DISCOVERING, CONNECTING, ACTIVE, ERROR }
    enum class Role { CLIENT, HOST }

    data class PendingConnectionInfo(
        val endpointId: String,
        val endpointName: String
    )

    companion object {
        private const val PREFS_NAME = "funlink_nearby_settings"
        private const val PREF_HOST_ROOM_NAME = "host_room_name"
        private const val PREF_CLIENT_CONNECTION_NAME = "client_connection_name"
    }

    val connectionsClient = Nearby.getConnectionsClient(context)
    val serviceId: String = context.packageName

    private val settingsPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val deviceId = UUID.randomUUID().toString().take(8)

    private val connectionStateLock = Any()
    private val outgoingRequests = mutableSetOf<String>()
    private val awaitingConnectionResult = mutableSetOf<String>()
    private val endpointNames = mutableMapOf<String, String>()

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _role = MutableStateFlow(Role.HOST)
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _hostRoomName = MutableStateFlow(loadPersistedHostRoomName())
    val hostRoomName: StateFlow<String> = _hostRoomName.asStateFlow()

    private val _clientConnectionName = MutableStateFlow(loadPersistedClientConnectionName())
    val clientConnectionName: StateFlow<String> = _clientConnectionName.asStateFlow()

    private val _connectedEndpoints = MutableStateFlow<Set<NearbyEndpointInfo>>(emptySet())
    val connectedEndpoints: StateFlow<Set<NearbyEndpointInfo>> = _connectedEndpoints.asStateFlow()

    private val _discoveredEndpoints = MutableStateFlow<List<NearbyEndpointInfo>>(emptyList())
    val discoveredEndpoints: StateFlow<List<NearbyEndpointInfo>> = _discoveredEndpoints.asStateFlow()

    private val _pendingConnections = MutableStateFlow<List<PendingConnectionInfo>>(emptyList())
    val pendingConnections: StateFlow<List<PendingConnectionInfo>> = _pendingConnections.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _endpointBandwidth = MutableStateFlow<Map<String, BandwidthInfo>>(emptyMap())
    val endpointBandwidth: StateFlow<Map<String, BandwidthInfo>> = _endpointBandwidth.asStateFlow()

    private val _currentQuality = MutableStateFlow(0)
    val currentQuality: StateFlow<Int> = _currentQuality.asStateFlow()

    val allowDisruptiveUpgrade = MutableStateFlow(true)

    val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val remoteName = info.endpointName.ifBlank { endpointId }
            endpointNames[endpointId] = remoteName

            if (isSelfEndpointName(remoteName)) {
                clearEndpointTracking(endpointId)
                connectionsClient.rejectConnection(endpointId)
                updateStatus()
                return
            }

            val outgoingRequest = synchronized(connectionStateLock) {
                awaitingConnectionResult.add(endpointId)
                outgoingRequests.contains(endpointId)
            }

            if (outgoingRequest) {
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnFailureListener { exception ->
                        synchronized(connectionStateLock) {
                            outgoingRequests.remove(endpointId)
                            awaitingConnectionResult.remove(endpointId)
                        }
                        _lastError.value = NearbyError.fromException(
                            prefix = "接受连接失败",
                            exception = exception
                        ).displayMessage
                        updateStatus()
                    }
                return
            }

            addPendingConnection(endpointId, remoteName)
            updateStatus()
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            synchronized(connectionStateLock) {
                outgoingRequests.remove(endpointId)
                awaitingConnectionResult.remove(endpointId)
            }
            removePendingConnection(endpointId)

            if (result.status.isSuccess) {
                val remoteName = endpointNames[endpointId] ?: endpointId
                if (isSelfEndpointName(remoteName)) {
                    clearEndpointTracking(endpointId)
                    connectionsClient.disconnectFromEndpoint(endpointId)
                    updateStatus()
                    return
                }

                val current = _connectedEndpoints.value.toMutableSet()
                if (current.none { it.endpointId == endpointId }) {
                    current.add(NearbyEndpointInfo(endpointId = endpointId, endpointName = remoteName))
                    _connectedEndpoints.value = current
                    AppRegistry.dispatchConnected(endpointId, remoteName)
                }
                removeDiscoveredEndpoint(endpointId)
                _lastError.value = null
            } else {
                clearEndpointTracking(endpointId)
                _lastError.value = NearbyError.Api(
                    prefix = "连接失败",
                    statusCode = result.status.statusCode
                ).displayMessage
            }

            updateStatus()
        }

        override fun onDisconnected(endpointId: String) {
            clearEndpointTracking(endpointId)

            val current = _connectedEndpoints.value.toMutableSet()
            val removed = current.removeAll { it.endpointId == endpointId }
            _connectedEndpoints.value = current
            if (removed) {
                AppRegistry.dispatchDisconnect(endpointId)
            }

            _endpointBandwidth.value = _endpointBandwidth.value - endpointId
            updateCurrentQuality()
            updateStatus()
        }

        override fun onBandwidthChanged(endpointId: String, bandwidthInfo: BandwidthInfo) {
            _endpointBandwidth.value = _endpointBandwidth.value + (endpointId to bandwidthInfo)
            updateCurrentQuality()

            val endpoint = _connectedEndpoints.value.firstOrNull { it.endpointId == endpointId }
            if (endpoint != null) {
                _connectedEndpoints.value = _connectedEndpoints.value
                    .map {
                        if (it.endpointId == endpointId) it.copy(bandwidthInfo = bandwidthInfo)
                        else it
                    }
                    .toSet()
            }

            AppRegistry.dispatchBandwidthChanged(endpointId, bandwidthInfo)
        }
    }

    val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, discoveredInfo: DiscoveredEndpointInfo) {
            if (_connectedEndpoints.value.any { it.endpointId == endpointId }) return
            if (_pendingConnections.value.any { it.endpointId == endpointId }) return

            val waitingConnectionResult = synchronized(connectionStateLock) {
                awaitingConnectionResult.contains(endpointId)
            }
            if (waitingConnectionResult) return

            val remoteName = discoveredInfo.endpointName.ifBlank { endpointId }
            if (isSelfEndpointName(remoteName)) return
            endpointNames[endpointId] = remoteName

            val endpoints = _discoveredEndpoints.value.toMutableList()
            val index = endpoints.indexOfFirst { it.endpointId == endpointId }
            val endpointInfo = NearbyEndpointInfo(endpointId = endpointId, endpointName = remoteName)
            if (index >= 0) {
                endpoints[index] = endpointInfo
            } else {
                endpoints.add(endpointInfo)
            }
            _discoveredEndpoints.value = endpoints
        }

        override fun onEndpointLost(endpointId: String) {
            removeDiscoveredEndpoint(endpointId)
        }
    }

    val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    AppRegistry.dispatchMessage(endpointId, bytes)
                }

                Payload.Type.FILE,
                Payload.Type.STREAM -> {
                    AppRegistry.dispatchPayload(endpointId, payload)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            AppRegistry.dispatchPayloadTransferUpdate(endpointId, update)
        }
    }

    fun setRole(role: Role) {
        _role.value = role
        updateStatus()
    }

    fun onAdvertisingStarted() {
        _isAdvertising.value = true
        updateStatus()
    }

    fun onAdvertisingStopped() {
        _isAdvertising.value = false
        updateStatus()
    }

    fun onDiscoveryStarted() {
        _isDiscovering.value = true
        updateStatus()
    }

    fun onDiscoveryStopped() {
        _isDiscovering.value = false
        _discoveredEndpoints.value = emptyList()
        updateStatus()
    }

    fun onOperationError(prefix: String, exception: Exception) {
        _lastError.value = NearbyError.fromException(prefix, exception).displayMessage
        updateStatus()
    }

    fun onValidationError(message: String) {
        _lastError.value = message
        updateStatus()
    }

    fun clearLastError() {
        _lastError.value = null
        updateStatus()
    }

    fun setHostRoomName(name: String) {
        val normalized = name.trim()
        if (_hostRoomName.value == normalized) return
        _hostRoomName.value = normalized
        settingsPrefs.edit { putString(PREF_HOST_ROOM_NAME, normalized) }
    }

    fun setClientConnectionName(name: String) {
        val normalized = name.trim()
        if (_clientConnectionName.value == normalized) return
        _clientConnectionName.value = normalized
        settingsPrefs.edit { putString(PREF_CLIENT_CONNECTION_NAME, normalized) }
    }

    fun connectionRequestName(): String {
        return _clientConnectionName.value.ifBlank { deviceId }
    }

    fun prepareOutgoingConnection(endpointId: String): Boolean {
        if (_connectedEndpoints.value.any { it.endpointId == endpointId }) return false
        if (_pendingConnections.value.any { it.endpointId == endpointId }) return false
        if (isSelfEndpointName(endpointNames[endpointId])) {
            _lastError.value = "已忽略自身设备连接请求"
            updateStatus()
            return false
        }

        val canRequest = synchronized(connectionStateLock) {
            if (outgoingRequests.contains(endpointId) || awaitingConnectionResult.contains(endpointId)) {
                false
            } else {
                outgoingRequests.add(endpointId)
                awaitingConnectionResult.add(endpointId)
                true
            }
        }
        if (!canRequest) return false

        _lastError.value = null
        updateStatus()
        return true
    }

    fun onOutgoingConnectionFailed(endpointId: String, exception: Exception) {
        synchronized(connectionStateLock) {
            outgoingRequests.remove(endpointId)
            awaitingConnectionResult.remove(endpointId)
        }
        _lastError.value = NearbyError.fromException(
            prefix = "发起连接失败",
            exception = exception
        ).displayMessage
        updateStatus()
    }

    fun prepareAcceptConnection(endpointId: String): Boolean {
        if (_pendingConnections.value.none { it.endpointId == endpointId }) return false
        removePendingConnection(endpointId)
        synchronized(connectionStateLock) {
            awaitingConnectionResult.add(endpointId)
        }
        _lastError.value = null
        updateStatus()
        return true
    }

    fun onAcceptConnectionFailed(endpointId: String, exception: Exception) {
        synchronized(connectionStateLock) {
            awaitingConnectionResult.remove(endpointId)
        }
        _lastError.value = NearbyError.fromException(
            prefix = "接受连接失败",
            exception = exception
        ).displayMessage
        updateStatus()
    }

    fun rejectPendingConnection(endpointId: String) {
        removePendingConnection(endpointId)
        synchronized(connectionStateLock) {
            outgoingRequests.remove(endpointId)
            awaitingConnectionResult.remove(endpointId)
        }
        endpointNames.remove(endpointId)
        updateStatus()
    }

    fun resetConnections() {
        val disconnectedIds = _connectedEndpoints.value.map { it.endpointId }
        synchronized(connectionStateLock) {
            outgoingRequests.clear()
            awaitingConnectionResult.clear()
        }
        endpointNames.clear()
        _connectedEndpoints.value = emptySet()
        _discoveredEndpoints.value = emptyList()
        _pendingConnections.value = emptyList()
        _endpointBandwidth.value = emptyMap()
        _currentQuality.value = 0
        _isAdvertising.value = false
        _isDiscovering.value = false
        disconnectedIds.forEach { AppRegistry.dispatchDisconnect(it) }
        updateStatus()
    }

    private fun updateCurrentQuality() {
        val bandwidths = _endpointBandwidth.value.values
        _currentQuality.value = if (bandwidths.isEmpty()) {
            0
        } else {
            bandwidths.minOf { it.quality }
        }
    }

    private fun updateStatus() {
        val hasAwaiting = synchronized(connectionStateLock) {
            awaitingConnectionResult.isNotEmpty()
        }
        _status.value = when {
            _connectedEndpoints.value.isNotEmpty() -> Status.ACTIVE
            _pendingConnections.value.isNotEmpty() || hasAwaiting -> Status.CONNECTING
            !_lastError.value.isNullOrBlank() -> Status.ERROR
            _isDiscovering.value -> Status.DISCOVERING
            _isAdvertising.value -> Status.ADVERTISING
            else -> Status.IDLE
        }
    }

    private fun addPendingConnection(endpointId: String, endpointName: String) {
        val current = _pendingConnections.value.toMutableList()
        val index = current.indexOfFirst { it.endpointId == endpointId }
        val info = PendingConnectionInfo(endpointId = endpointId, endpointName = endpointName)
        if (index >= 0) {
            current[index] = info
        } else {
            current.add(info)
        }
        _pendingConnections.value = current
    }

    private fun removePendingConnection(endpointId: String) {
        val current = _pendingConnections.value
        if (current.none { it.endpointId == endpointId }) return
        _pendingConnections.value = current.filterNot { it.endpointId == endpointId }
    }

    private fun removeDiscoveredEndpoint(endpointId: String) {
        val current = _discoveredEndpoints.value
        if (current.none { it.endpointId == endpointId }) return
        _discoveredEndpoints.value = current.filterNot { it.endpointId == endpointId }
    }

    private fun isSelfEndpointName(endpointName: String?): Boolean {
        return endpointName != null && endpointName == deviceId
    }

    private fun clearEndpointTracking(endpointId: String) {
        synchronized(connectionStateLock) {
            outgoingRequests.remove(endpointId)
            awaitingConnectionResult.remove(endpointId)
        }
        removePendingConnection(endpointId)
        removeDiscoveredEndpoint(endpointId)
        endpointNames.remove(endpointId)
    }

    private fun loadPersistedHostRoomName(): String {
        return settingsPrefs.getString(PREF_HOST_ROOM_NAME, "")?.trim().orEmpty()
    }

    private fun loadPersistedClientConnectionName(): String {
        return settingsPrefs.getString(PREF_CLIENT_CONNECTION_NAME, "")?.trim().orEmpty()
    }
}

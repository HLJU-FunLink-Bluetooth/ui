package com.hlju.funlinkbluetooth.ui

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.BandwidthInfo
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NearbyUiStateTest {

    private val connectionsClient = mockk<ConnectionsClient>(relaxed = true)
    private lateinit var state: NearbyUiState

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("funlink_nearby_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        mockkStatic(Nearby::class)
        every { Nearby.getConnectionsClient(any()) } returns connectionsClient
        state = NearbyUiState(context)
    }

    @After
    fun tearDown() {
        state.connectionsClient.stopAdvertising()
        state.connectionsClient.stopDiscovery()
        state.connectionsClient.stopAllEndpoints()
        state.resetConnections()
        unmockkStatic(Nearby::class)
    }

    @Test
    fun `initial state is idle and host`() {
        assertEquals(NearbyUiState.Status.IDLE, state.status.value)
        assertEquals(NearbyUiState.Role.HOST, state.role.value)
        assertFalse(state.isAdvertising.value)
        assertFalse(state.isDiscovering.value)
        assertTrue(state.connectedEndpoints.value.isEmpty())
    }

    @Test
    fun `validation error sets error status`() {
        state.onValidationError("请输入连接名")

        assertEquals("请输入连接名", state.lastError.value)
        assertEquals(NearbyUiState.Status.ERROR, state.status.value)
    }

    @Test
    fun `advertising start and stop updates status`() {
        state.onAdvertisingStarted()
        assertEquals(NearbyUiState.Status.ADVERTISING, state.status.value)

        state.onAdvertisingStopped()
        assertEquals(NearbyUiState.Status.IDLE, state.status.value)
    }

    @Test
    fun `connection initiated adds pending endpoint`() {
        val info = mockk<ConnectionInfo>()
        every { info.endpointName } returns "Peer-A"

        state.connectionLifecycleCallback.onConnectionInitiated("ep-1", info)

        assertTrue(state.pendingConnections.value.any {
            it.endpointId == "ep-1" && it.endpointName == "Peer-A"
        })
        assertEquals(NearbyUiState.Status.CONNECTING, state.status.value)
    }

    @Test
    fun `successful connection result adds connected endpoint`() {
        val info = mockk<ConnectionInfo>()
        every { info.endpointName } returns "Peer-A"
        state.connectionLifecycleCallback.onConnectionInitiated("ep-1", info)

        val result = mockk<ConnectionResolution>()
        val status = mockk<Status>()
        every { status.isSuccess } returns true
        every { result.status } returns status

        state.connectionLifecycleCallback.onConnectionResult("ep-1", result)

        assertTrue(state.connectedEndpoints.value.any { it.endpointId == "ep-1" })
        assertEquals(NearbyUiState.Status.ACTIVE, state.status.value)
    }

    @Test
    fun `outgoing request failure sets error`() {
        assertTrue(state.prepareOutgoingConnection("ep-2"))
        assertEquals(NearbyUiState.Status.CONNECTING, state.status.value)

        state.onOutgoingConnectionFailed("ep-2", RuntimeException("boom"))

        assertEquals(NearbyUiState.Status.ERROR, state.status.value)
        assertTrue(state.lastError.value?.contains("发起连接失败") == true)
    }

    @Test
    fun `operation error with ApiException contains status code`() {
        state.onOperationError("扫描失败", ApiException(Status(8500)))

        assertEquals(NearbyUiState.Status.ERROR, state.status.value)
        assertTrue(state.lastError.value?.contains("扫描失败") == true)
        assertTrue(state.lastError.value?.contains("8500") == true)
    }

    @Test
    fun `discovery start and stop clear discovered endpoints`() {
        val discoveredInfo = mockk<DiscoveredEndpointInfo>()
        every { discoveredInfo.endpointName } returns "Peer-Discovery"

        state.endpointDiscoveryCallback.onEndpointFound("ep-1", discoveredInfo)
        assertEquals(1, state.discoveredEndpoints.value.size)

        state.onDiscoveryStarted()
        assertEquals(NearbyUiState.Status.DISCOVERING, state.status.value)

        state.onDiscoveryStopped()
        assertTrue(state.discoveredEndpoints.value.isEmpty())
        assertEquals(NearbyUiState.Status.IDLE, state.status.value)
    }

    @Test
    fun `endpoint discovery updates existing endpoint and endpoint lost removes it`() {
        val first = mockk<DiscoveredEndpointInfo>()
        every { first.endpointName } returns "Peer-A"
        val second = mockk<DiscoveredEndpointInfo>()
        every { second.endpointName } returns "Peer-B"

        state.endpointDiscoveryCallback.onEndpointFound("ep-1", first)
        state.endpointDiscoveryCallback.onEndpointFound("ep-1", second)

        assertEquals(1, state.discoveredEndpoints.value.size)
        assertEquals("Peer-B", state.discoveredEndpoints.value.first().endpointName)

        state.endpointDiscoveryCallback.onEndpointLost("ep-1")
        assertTrue(state.discoveredEndpoints.value.isEmpty())
    }

    @Test
    fun `prepareOutgoingConnection prevents duplicate pending request`() {
        assertTrue(state.prepareOutgoingConnection("ep-dup"))
        assertFalse(state.prepareOutgoingConnection("ep-dup"))
        assertEquals(NearbyUiState.Status.CONNECTING, state.status.value)
    }

    @Test
    fun `failed connection result sets error and clears pending endpoint`() {
        val info = mockk<ConnectionInfo>()
        every { info.endpointName } returns "Peer-Fail"
        state.connectionLifecycleCallback.onConnectionInitiated("ep-fail", info)

        val result = mockk<ConnectionResolution>()
        val status = mockk<Status>()
        every { status.isSuccess } returns false
        every { status.statusCode } returns 8501
        every { result.status } returns status

        state.connectionLifecycleCallback.onConnectionResult("ep-fail", result)

        assertTrue(state.pendingConnections.value.none { it.endpointId == "ep-fail" })
        assertTrue(state.connectedEndpoints.value.none { it.endpointId == "ep-fail" })
        assertEquals(NearbyUiState.Status.ERROR, state.status.value)
        assertTrue(state.lastError.value?.contains("8501") == true)
    }

    @Test
    fun `disconnect removes connected endpoint and returns to idle`() {
        val info = mockk<ConnectionInfo>()
        every { info.endpointName } returns "Peer-A"
        state.connectionLifecycleCallback.onConnectionInitiated("ep-1", info)

        val result = mockk<ConnectionResolution>()
        val status = mockk<Status>()
        every { status.isSuccess } returns true
        every { result.status } returns status
        state.connectionLifecycleCallback.onConnectionResult("ep-1", result)
        assertEquals(NearbyUiState.Status.ACTIVE, state.status.value)

        state.connectionLifecycleCallback.onDisconnected("ep-1")

        assertTrue(state.connectedEndpoints.value.isEmpty())
        assertEquals(NearbyUiState.Status.IDLE, state.status.value)
    }

    @Test
    fun `bandwidth updates endpoint map and current quality with minimum value`() {
        val info = mockk<ConnectionInfo>()
        every { info.endpointName } returns "Peer-A"
        state.connectionLifecycleCallback.onConnectionInitiated("ep-1", info)
        state.connectionLifecycleCallback.onConnectionInitiated("ep-2", info)

        val successResult = mockk<ConnectionResolution>()
        val successStatus = mockk<Status>()
        every { successStatus.isSuccess } returns true
        every { successResult.status } returns successStatus
        state.connectionLifecycleCallback.onConnectionResult("ep-1", successResult)
        state.connectionLifecycleCallback.onConnectionResult("ep-2", successResult)

        val high = mockk<BandwidthInfo>()
        every { high.quality } returns BandwidthInfo.Quality.HIGH
        val low = mockk<BandwidthInfo>()
        every { low.quality } returns BandwidthInfo.Quality.LOW

        state.connectionLifecycleCallback.onBandwidthChanged("ep-1", high)
        state.connectionLifecycleCallback.onBandwidthChanged("ep-2", low)

        assertEquals(2, state.endpointBandwidth.value.size)
        assertEquals(BandwidthInfo.Quality.LOW, state.currentQuality.value)
        assertEquals(BandwidthInfo.Quality.LOW, state.connectedEndpoints.value.first {
            it.endpointId == "ep-2"
        }.bandwidthInfo?.quality)
    }

    @Test
    fun `set host and client names persists trimmed values`() {
        state.setHostRoomName("  Room-A  ")
        state.setClientConnectionName("  Client-A  ")

        assertEquals("Room-A", state.hostRoomName.value)
        assertEquals("Client-A", state.clientConnectionName.value)

        val restored = NearbyUiState(RuntimeEnvironment.getApplication())
        assertEquals("Room-A", restored.hostRoomName.value)
        assertEquals("Client-A", restored.clientConnectionName.value)
        restored.resetConnections()
    }

    @Test
    fun `prepareAcceptConnection returns false when endpoint is not pending`() {
        assertFalse(state.prepareAcceptConnection("missing"))
        assertEquals(NearbyUiState.Status.IDLE, state.status.value)
    }

    @Test
    fun `rejectPendingConnection removes pending endpoint and clears connecting state`() {
        val info = mockk<ConnectionInfo>()
        every { info.endpointName } returns "Peer-Pending"
        state.connectionLifecycleCallback.onConnectionInitiated("ep-pending", info)
        assertTrue(state.pendingConnections.value.any { it.endpointId == "ep-pending" })

        state.rejectPendingConnection("ep-pending")

        assertTrue(state.pendingConnections.value.none { it.endpointId == "ep-pending" })
        assertEquals(NearbyUiState.Status.IDLE, state.status.value)
    }
}

package com.hlju.funlinkbluetooth.ui

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
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
        mockkStatic(Nearby::class)
        every { Nearby.getConnectionsClient(any()) } returns connectionsClient
        state = NearbyUiState(RuntimeEnvironment.getApplication())
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
}

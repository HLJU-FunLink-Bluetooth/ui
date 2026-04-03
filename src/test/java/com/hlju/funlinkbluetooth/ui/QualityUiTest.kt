package com.hlju.funlinkbluetooth.ui

import com.google.android.gms.nearby.connection.BandwidthInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class QualityUiTest {

    @Test
    fun `qualityLabel returns 高 for high quality`() {
        assertEquals("高", qualityLabel(BandwidthInfo.Quality.HIGH))
    }

    @Test
    fun `qualityLabel returns 中 for medium quality`() {
        assertEquals("中", qualityLabel(BandwidthInfo.Quality.MEDIUM))
    }

    @Test
    fun `qualityLabel returns 低 for low quality`() {
        assertEquals("低", qualityLabel(BandwidthInfo.Quality.LOW))
    }

    @Test
    fun `qualityLabel returns 未知 for unknown quality`() {
        assertEquals("未知", qualityLabel(-1))
    }
}

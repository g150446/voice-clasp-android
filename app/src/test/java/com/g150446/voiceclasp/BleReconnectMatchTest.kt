package com.g150446.voiceclasp

import com.g150446.voiceclasp.BleManager.Companion.matchesReconnectTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleReconnectMatchTest {
    @Test
    fun `exact MAC matches`() {
        assertTrue(
            matchesReconnectTarget(
                resultAddress = "CF:78:E0:AC:05:04",
                resultName = "HarnessNode",
                targetAddress = "CF:78:E0:AC:05:04",
                preferredName = "HarnessNode",
            ),
        )
    }

    @Test
    fun `case insensitive MAC matches`() {
        assertTrue(
            matchesReconnectTarget(
                resultAddress = "cf:78:e0:ac:05:04",
                resultName = "HarnessNode",
                targetAddress = "CF:78:E0:AC:05:04",
                preferredName = "HarnessNode",
            ),
        )
    }

    @Test
    fun `RPA change still matches preferred HarnessNode name`() {
        assertTrue(
            matchesReconnectTarget(
                resultAddress = "AA:BB:CC:DD:EE:FF",
                resultName = "HarnessNode",
                targetAddress = "CF:78:E0:AC:05:04",
                preferredName = "HarnessNode",
            ),
        )
        assertTrue(
            matchesReconnectTarget(
                resultAddress = "AA:BB:CC:DD:EE:FF",
                resultName = "HarnessNode-Echo",
                targetAddress = "CF:78:E0:AC:05:04",
                preferredName = "HarnessNode",
            ),
        )
    }

    @Test
    fun `unrelated BLE device does not match`() {
        assertFalse(
            matchesReconnectTarget(
                resultAddress = "11:22:33:44:55:66",
                resultName = "Even G2",
                targetAddress = "CF:78:E0:AC:05:04",
                preferredName = "HarnessNode",
            ),
        )
    }

    @Test
    fun `null target never matches by name alone`() {
        assertFalse(
            matchesReconnectTarget(
                resultAddress = "AA:BB:CC:DD:EE:FF",
                resultName = "HarnessNode",
                targetAddress = null,
                preferredName = "HarnessNode",
            ),
        )
    }

    @Test
    fun `node renamed by firmware update still matches the old preferred name on a new MAC`() {
        // Same band, advertised name moved HarnessNode -> ClaspNode over an OTA.
        assertTrue(
            matchesReconnectTarget(
                resultAddress = "AA:BB:CC:DD:EE:FF",
                resultName = "ClaspNode",
                targetAddress = "CF:78:E0:AC:05:04",
                preferredName = "HarnessNode",
            ),
        )
        assertTrue(
            matchesReconnectTarget(
                resultAddress = "AA:BB:CC:DD:EE:FF",
                resultName = "HarnessNode",
                targetAddress = "CF:78:E0:AC:05:04",
                preferredName = "ClaspNode",
            ),
        )
    }

    @Test
    fun `exact MAC still wins whatever the advertised name is`() {
        assertTrue(
            matchesReconnectTarget(
                resultAddress = "CF:78:E0:AC:05:04",
                resultName = "ClaspNode",
                targetAddress = "CF:78:E0:AC:05:04",
                preferredName = "HarnessNode",
            ),
        )
    }

    @Test
    fun `node names accept both brands and their suffixed variants only`() {
        assertTrue(BleManager.isNodeName("ClaspNode"))
        assertTrue(BleManager.isNodeName("HarnessNode"))
        assertTrue(BleManager.isNodeName("ClaspNode-Plus2"))
        assertTrue(BleManager.isNodeName("HarnessNode-Echo"))
        assertFalse(BleManager.isNodeName("ClaspNodeX"))
        assertFalse(BleManager.isNodeName("Even G2"))
        assertFalse(BleManager.isNodeName(""))
    }
}

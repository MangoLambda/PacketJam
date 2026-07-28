package app.packetjam

import app.packetjam.model.BuiltInProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkProfileTest {
    @Test fun builtInsHaveStableIdsAndSeverityOrder() {
        val profiles = BuiltInProfiles.all
        assertEquals(profiles.size, profiles.map { it.id }.toSet().size)
        assertEquals(14, profiles.size)
        assertEquals(
            listOf(
                "Normal 4G", "Busy 4G", "Weak 4G", "Rural 4G", "Fading 4G",
                "Good 3G", "Typical 3G", "Slow 3G", "Fading 3G",
                "Good EDGE", "Normal EDGE", "Fading EDGE", "Fringe EDGE", "Offline",
            ),
            profiles.map { it.name },
        )
        assertEquals("normal4g", profiles.first().id)
        assertEquals("offline", profiles.last().id)
        assertEquals(12_000, BuiltInProfiles.normal4g.download.rateKbps)
        assertEquals(45, BuiltInProfiles.normal4g.latencyMs)
        assertEquals(256, BuiltInProfiles.normal4g.queuePackets)
        assertEquals(256, BuiltInProfiles.goodEdge.download.rateKbps)
        assertEquals(160, BuiltInProfiles.normalEdge.download.rateKbps)
        assertEquals(128, BuiltInProfiles.fadingEdge.download.rateKbps)
        assertEquals("Fringe EDGE", BuiltInProfiles.fringeEdge.name)
        assertEquals(96, BuiltInProfiles.fringeEdge.download.rateKbps)
        assertEquals(20f, BuiltInProfiles.fringeEdge.download.lossPercent)
        assertEquals(32, BuiltInProfiles.fringeEdge.upload.rateKbps)
        assertEquals(24f, BuiltInProfiles.fringeEdge.upload.lossPercent)
        assertTrue(BuiltInProfiles.fringeEdge.burst != null)
    }

    @Test fun offlineIsTheOnlyOfflineBuiltIn() {
        assertFalse(BuiltInProfiles.all.dropLast(1).any { it.offline })
        assertTrue(BuiltInProfiles.offline.offline)
        assertFalse(BuiltInProfiles.all.any { it.id == "almost_disconnected" })
        assertFalse(BuiltInProfiles.all.any { it.id == "dead_zone" })
        assertFalse(BuiltInProfiles.all.any { it.id == "one_bar" })
    }
}

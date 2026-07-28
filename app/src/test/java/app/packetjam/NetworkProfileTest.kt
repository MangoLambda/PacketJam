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
        assertEquals("stable_wifi", profiles.first().id)
        assertEquals("offline", profiles.last().id)
        assertEquals("Fading EDGE", BuiltInProfiles.fringeEdge.name)
        assertEquals(128, BuiltInProfiles.fringeEdge.download.rateKbps)
        assertEquals(15f, BuiltInProfiles.fringeEdge.download.lossPercent)
        assertEquals(48, BuiltInProfiles.fringeEdge.upload.rateKbps)
        assertEquals(18f, BuiltInProfiles.fringeEdge.upload.lossPercent)
        assertTrue(BuiltInProfiles.fringeEdge.burst != null)
    }

    @Test fun offlineIsTheOnlyOfflineBuiltIn() {
        assertFalse(BuiltInProfiles.all.dropLast(1).any { it.offline })
        assertTrue(BuiltInProfiles.offline.offline)
    }
}

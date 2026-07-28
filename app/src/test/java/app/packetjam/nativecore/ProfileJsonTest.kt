package app.packetjam.nativecore

import app.packetjam.model.BuiltInProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileJsonTest {
    @Test fun serializesBurstScheduleForNativeEngine() {
        val json = BuiltInProfiles.fading3g.toNativeJson()

        assertEquals(true, json.contains("\"burst\":{\"impairedSeconds\":12,\"healthySeconds\":3}"))
    }

    @Test fun omitsBurstForSteadyProfile() {
        val json = BuiltInProfiles.stableWifi.toNativeJson()
        assertFalse(json.contains("\"burst\""))
    }
}

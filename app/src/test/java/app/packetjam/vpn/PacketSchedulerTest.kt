package app.packetjam.vpn

import app.packetjam.model.DirectionLimits
import app.packetjam.model.NetworkProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketSchedulerTest {
    private fun profile(
        latency: Int = 0,
        rate: Int = 0,
        loss: Float = 0f,
        queue: Int = 10,
        offline: Boolean = false,
    ) = NetworkProfile(
        id = "test", name = "Test", latencyMs = latency, jitterMs = 0,
        download = DirectionLimits(rateKbps = rate, lossPercent = loss),
        upload = DirectionLimits(rateKbps = rate, lossPercent = loss),
        queuePackets = queue, offline = offline,
    )

    @Test fun latencyHoldsPacketUntilDue() {
        val scheduler = PacketScheduler(profile(latency = 100), seed = 1)
        assertTrue(scheduler.offer(byteArrayOf(1), TrafficDirection.UPLOAD, 0))
        assertTrue(scheduler.pollDue(99_000_000).isEmpty())
        assertEquals(1, scheduler.pollDue(100_000_000).size)
    }

    @Test fun offlineDropsEverything() {
        val scheduler = PacketScheduler(profile(offline = true), seed = 1)
        assertFalse(scheduler.offer(byteArrayOf(1), TrafficDirection.UPLOAD, 0))
        assertEquals(1, scheduler.counters.dropped)
    }

    @Test fun fullLossDropsEverything() {
        val scheduler = PacketScheduler(profile(loss = 100f), seed = 99)
        repeat(20) { scheduler.offer(byteArrayOf(1), TrafficDirection.DOWNLOAD, 0) }
        assertEquals(20, scheduler.counters.dropped)
        assertEquals(0, scheduler.size())
    }

    @Test fun queueOverflowIsCounted() {
        val scheduler = PacketScheduler(profile(latency = 100, queue = 1), seed = 1)
        assertTrue(scheduler.offer(byteArrayOf(1), TrafficDirection.UPLOAD, 0))
        assertFalse(scheduler.offer(byteArrayOf(2), TrafficDirection.UPLOAD, 0))
        assertEquals(1, scheduler.counters.queueOverflow)
    }

    @Test fun rateLimitSerializesPackets() {
        val scheduler = PacketScheduler(profile(rate = 8), seed = 1)
        scheduler.offer(ByteArray(1_000), TrafficDirection.UPLOAD, 0)
        assertTrue(scheduler.pollDue(999_999_999).isEmpty())
        assertEquals(1, scheduler.pollDue(1_000_000_000).size)
    }

    @Test fun healthyBurstWindowRetainsRateButSkipsLossAndLatency() {
        val burstProfile = NetworkProfile(
            id = "burst", name = "Burst", latencyMs = 100, jitterMs = 0,
            download = DirectionLimits(rateKbps = 8, lossPercent = 100f),
            upload = DirectionLimits(rateKbps = 8, lossPercent = 100f),
            queuePackets = 10,
            burst = app.packetjam.model.BurstSchedule(1, 1),
        )
        val scheduler = PacketScheduler(burstProfile, seed = 1)

        assertFalse(scheduler.offer(byteArrayOf(1), TrafficDirection.UPLOAD, 0))
        assertTrue(scheduler.offer(byteArrayOf(2), TrafficDirection.UPLOAD, 1_000_000_000))
        assertTrue(scheduler.pollDue(1_000_999_999).isEmpty())
        assertEquals(1, scheduler.pollDue(1_001_000_000).size)
    }
}

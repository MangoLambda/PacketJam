package app.packetjam.vpn

import app.packetjam.model.DirectionLimits
import app.packetjam.model.NetworkProfile
import java.util.PriorityQueue
import java.util.Random
import kotlin.math.max

enum class TrafficDirection { UPLOAD, DOWNLOAD }

data class ScheduledPacket(
    val bytes: ByteArray,
    val direction: TrafficDirection,
    val releaseAtNanos: Long,
)

data class ImpairmentCounters(
    var delayed: Long = 0,
    var dropped: Long = 0,
    var duplicated: Long = 0,
    var corrupted: Long = 0,
    var reordered: Long = 0,
    var queueOverflow: Long = 0,
)

/**
 * Deterministic packet-level impairment. The forwarding engine feeds complete IP packets here;
 * due packets are returned in release order. This class deliberately has no Android dependency.
 */
class PacketScheduler(
    initialProfile: NetworkProfile,
    val seed: Long,
) {
    private val random = Random(seed)
    private val queue = PriorityQueue<ScheduledPacket>(compareBy { it.releaseAtNanos })
    val counters = ImpairmentCounters()
    var profile: NetworkProfile = initialProfile

    private var uploadAvailableAt = 0L
    private var downloadAvailableAt = 0L
    private var burstStartNanos: Long? = null

    @Synchronized
    fun offer(bytes: ByteArray, direction: TrafficDirection, nowNanos: Long): Boolean {
        if (profile.offline) {
            counters.dropped++
            return false
        }
        if (queue.size >= profile.queuePackets) {
            counters.queueOverflow++
            counters.dropped++
            return false
        }

        val limits = limits(direction)
        if (burstStartNanos == null) burstStartNanos = nowNanos
        val healthy = isHealthy(nowNanos)
        if (!healthy && chance(limits.lossPercent)) {
            counters.dropped++
            return false
        }
        val packet = bytes.copyOf()
        if (!healthy && packet.isNotEmpty() && chance(limits.corruptPercent)) {
            val index = random.nextInt(packet.size)
            packet[index] = (packet[index].toInt() xor (1 shl random.nextInt(8))).toByte()
            counters.corrupted++
        }

        var release = nowNanos + if (healthy) 0 else delayNanos()
        release = max(release, rateLimitedRelease(direction, packet.size, limits.rateKbps, nowNanos))
        if (!healthy && chance(limits.reorderPercent)) {
            release += max(1, profile.jitterMs).toLong() * 2_000_000L
            counters.reordered++
        }
        if (release > nowNanos) counters.delayed++
        queue += ScheduledPacket(packet, direction, release)
        if (!healthy && chance(limits.duplicatePercent) && queue.size < profile.queuePackets) {
            queue += ScheduledPacket(packet.copyOf(), direction, release + 1_000_000L)
            counters.duplicated++
        }
        return true
    }

    @Synchronized
    fun pollDue(nowNanos: Long): List<ScheduledPacket> = buildList {
        while (queue.peek()?.releaseAtNanos?.let { it <= nowNanos } == true) add(queue.remove())
    }

    @Synchronized fun clear() = queue.clear()
    @Synchronized fun size(): Int = queue.size

    private fun limits(direction: TrafficDirection): DirectionLimits =
        if (direction == TrafficDirection.UPLOAD) profile.upload else profile.download

    private fun isHealthy(nowNanos: Long): Boolean {
        val burst = profile.burst ?: return false
        if (burst.impairedSeconds <= 0 || burst.healthySeconds <= 0) return false
        val elapsed = nowNanos - (burstStartNanos ?: nowNanos)
        val impairedNanos = burst.impairedSeconds.toLong() * 1_000_000_000L
        val healthyNanos = burst.healthySeconds.toLong() * 1_000_000_000L
        return elapsed % (impairedNanos + healthyNanos) >= impairedNanos
    }

    private fun chance(percent: Float): Boolean =
        percent > 0f && random.nextDouble() * 100.0 < percent

    private fun delayNanos(): Long {
        val jitter = if (profile.jitterMs == 0) 0
        else random.nextInt(profile.jitterMs * 2 + 1) - profile.jitterMs
        return max(0, profile.latencyMs + jitter).toLong() * 1_000_000L
    }

    private fun rateLimitedRelease(
        direction: TrafficDirection,
        sizeBytes: Int,
        rateKbps: Int,
        now: Long,
    ): Long {
        if (rateKbps <= 0) return now
        val serializationNanos = sizeBytes.toLong() * 8L * 1_000_000L / rateKbps
        val available = if (direction == TrafficDirection.UPLOAD) uploadAvailableAt else downloadAvailableAt
        val release = max(now, available) + serializationNanos
        if (direction == TrafficDirection.UPLOAD) uploadAvailableAt = release
        else downloadAvailableAt = release
        return release
    }
}

package app.packetjam.model

import kotlin.math.roundToInt

data class DirectionLimits(
    val rateKbps: Int = 0,
    val lossPercent: Float = 0f,
    val duplicatePercent: Float = 0f,
    val corruptPercent: Float = 0f,
    val reorderPercent: Float = 0f,
)

data class BurstSchedule(
    val impairedSeconds: Int,
    val healthySeconds: Int,
)

data class NetworkProfile(
    val id: String,
    val name: String,
    val latencyMs: Int,
    val jitterMs: Int,
    val download: DirectionLimits,
    val upload: DirectionLimits,
    val queuePackets: Int = 256,
    val offline: Boolean = false,
    val burst: BurstSchedule? = null,
) {
    fun summary(): String = when {
        offline -> "No connection"
        download.rateKbps == 0 -> "${latencyMs}ms · Unlimited"
        download.rateKbps >= 1_000 -> "${latencyMs}ms · ${formatMbps(download.rateKbps)} Mbps"
        else -> "${latencyMs}ms · ${download.rateKbps} Kbps"
    }

    companion object {
        private fun formatMbps(kbps: Int): String {
            val value = kbps / 1_000f
            return if (value == value.roundToInt().toFloat()) value.roundToInt().toString()
            else "%.1f".format(value)
        }
    }
}

object BuiltInProfiles {
    val weak4g = NetworkProfile(
        id = "weak4g", name = "Weak 4G", latencyMs = 280, jitterMs = 100,
        download = DirectionLimits(900, 8f, .5f, .1f, 3f),
        upload = DirectionLimits(320, 10f, .5f, .1f, 3f),
        queuePackets = 128,
    )
    val fading3g = NetworkProfile(
        id = "fading3g", name = "Fading 3G", latencyMs = 650, jitterMs = 240,
        download = DirectionLimits(256, 25f, 1f, .2f, 8f),
        upload = DirectionLimits(96, 28f, 1f, .2f, 8f),
        queuePackets = 96,
        burst = BurstSchedule(impairedSeconds = 12, healthySeconds = 3),
    )
    val fringeEdge = NetworkProfile(
        id = "fringe_edge", name = "Fringe EDGE", latencyMs = 900, jitterMs = 350,
        download = DirectionLimits(56, 35f, 1f, .3f, 10f),
        upload = DirectionLimits(24, 40f, 1f, .3f, 10f),
        queuePackets = 64,
    )
    val oneBar = NetworkProfile(
        id = "one_bar", name = "One bar", latencyMs = 1_200, jitterMs = 650,
        download = DirectionLimits(32, 55f, 2f, .5f, 12f),
        upload = DirectionLimits(12, 60f, 2f, .5f, 12f),
        queuePackets = 48,
    )
    val almostDisconnected = NetworkProfile(
        id = "almost_disconnected", name = "Almost disconnected",
        latencyMs = 2_500, jitterMs = 1_200,
        download = DirectionLimits(8, 85f, 3f, 1f, 15f),
        upload = DirectionLimits(4, 88f, 3f, 1f, 15f),
        queuePackets = 24,
        burst = BurstSchedule(impairedSeconds = 18, healthySeconds = 2),
    )
    val deadZone = NetworkProfile(
        id = "dead_zone", name = "Dead zone", latencyMs = 4_000, jitterMs = 1_800,
        download = DirectionLimits(1, 98f, 1f, 1f, 20f),
        upload = DirectionLimits(1, 98f, 1f, 1f, 20f),
        queuePackets = 8,
    )
    val offline = NetworkProfile(
        id = "offline", name = "Offline", latencyMs = 0, jitterMs = 0,
        download = DirectionLimits(), upload = DirectionLimits(), offline = true,
    )

    val all = listOf(
        weak4g, fading3g, fringeEdge, oneBar, almostDisconnected, deadZone, offline,
    )
}

enum class VpnStatus { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

data class TrafficStats(
    val connectedAtMs: Long = 0,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val uploadBytesPerSecond: Long = 0,
    val downloadBytesPerSecond: Long = 0,
    val delayed: Long = 0,
    val dropped: Long = 0,
    val duplicated: Long = 0,
    val corrupted: Long = 0,
    val reordered: Long = 0,
    val queueOverflow: Long = 0,
    val seed: Long = 0,
)

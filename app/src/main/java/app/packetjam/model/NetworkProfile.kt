package app.packetjam.model

import kotlin.math.roundToInt

data class DirectionLimits(
    val rateKbps: Int = 0,
    val lossPercent: Float = 0f,
    val duplicatePercent: Float = 0f,
    val corruptPercent: Float = 0f,
    val reorderPercent: Float = 0f,
)

/** Periodic radio fading: packets use the configured impairment during the first window. */
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
    val stableWifi = NetworkProfile(
        id = "stable_wifi", name = "Stable Wi-Fi", latencyMs = 25, jitterMs = 8,
        download = DirectionLimits(25_000, .2f, .05f, .01f, .5f),
        upload = DirectionLimits(10_000, .2f, .05f, .01f, .5f),
        queuePackets = 256,
    )
    val congestedWifi = NetworkProfile(
        id = "congested_wifi", name = "Congested Wi-Fi", latencyMs = 90, jitterMs = 35,
        download = DirectionLimits(8_000, 2f, .1f, .02f, 2f),
        upload = DirectionLimits(2_000, 3f, .1f, .02f, 2f),
        queuePackets = 192,
    )
    val weak4g = NetworkProfile(
        id = "weak4g", name = "Weak 4G", latencyMs = 180, jitterMs = 60,
        download = DirectionLimits(3_000, 4f, .2f, .05f, 2f),
        upload = DirectionLimits(1_000, 5f, .2f, .05f, 2f),
        queuePackets = 192,
    )
    val rural4g = NetworkProfile(
        id = "rural4g", name = "Rural 4G", latencyMs = 240, jitterMs = 100,
        download = DirectionLimits(1_500, 7f, .3f, .05f, 3f),
        upload = DirectionLimits(384, 9f, .3f, .05f, 3f),
        queuePackets = 160,
    )
    val slow3g = NetworkProfile(
        id = "slow3g", name = "Slow 3G", latencyMs = 450, jitterMs = 180,
        download = DirectionLimits(768, 12f, .5f, .1f, 4f),
        upload = DirectionLimits(256, 14f, .5f, .1f, 4f),
        queuePackets = 128,
        burst = BurstSchedule(impairedSeconds = 15, healthySeconds = 5),
    )
    val fading3g = NetworkProfile(
        id = "fading3g", name = "Fading 3G", latencyMs = 650, jitterMs = 240,
        download = DirectionLimits(256, 25f, 1f, .2f, 8f),
        upload = DirectionLimits(96, 28f, 1f, .2f, 8f),
        queuePackets = 96,
        burst = BurstSchedule(impairedSeconds = 12, healthySeconds = 3),
    )
    val fringeEdge = NetworkProfile(
        id = "fringe_edge", name = "Fading EDGE", latencyMs = 800, jitterMs = 250,
        download = DirectionLimits(128, 15f, .5f, .1f, 5f),
        upload = DirectionLimits(48, 18f, .5f, .1f, 5f),
        queuePackets = 96,
        burst = BurstSchedule(impairedSeconds = 8, healthySeconds = 4),
    )
    val oneBar = NetworkProfile(
        id = "one_bar", name = "One bar", latencyMs = 1_000, jitterMs = 500,
        download = DirectionLimits(64, 35f, 1f, .3f, 8f),
        upload = DirectionLimits(24, 40f, 1f, .3f, 8f),
        queuePackets = 64,
        burst = BurstSchedule(impairedSeconds = 12, healthySeconds = 3),
    )
    val almostDisconnected = NetworkProfile(
        id = "almost_disconnected", name = "Almost disconnected",
        latencyMs = 2_000, jitterMs = 900,
        download = DirectionLimits(16, 70f, 2f, .5f, 12f),
        upload = DirectionLimits(8, 75f, 2f, .5f, 12f),
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
        stableWifi, congestedWifi, weak4g, rural4g, slow3g, fading3g, fringeEdge,
        oneBar, almostDisconnected, deadZone, offline,
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

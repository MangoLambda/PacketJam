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
    val normal4g = NetworkProfile(
        id = "normal4g", name = "Normal 4G", latencyMs = 45, jitterMs = 15,
        download = DirectionLimits(12_000, .3f, .05f, .01f, .5f),
        upload = DirectionLimits(5_000, .4f, .05f, .01f, .5f),
        queuePackets = 256,
    )
    val busy4g = NetworkProfile(
        id = "busy4g", name = "Busy 4G", latencyMs = 80, jitterMs = 25,
        download = DirectionLimits(6_000, 1.5f, .1f, .02f, 1.5f),
        upload = DirectionLimits(2_000, 2f, .1f, .02f, 1.5f),
        queuePackets = 192,
    )
    val weak4g = NetworkProfile(
        id = "weak4g", name = "Weak 4G", latencyMs = 150, jitterMs = 50,
        download = DirectionLimits(3_000, 3f, .15f, .03f, 2f),
        upload = DirectionLimits(1_000, 4f, .15f, .03f, 2f),
        queuePackets = 192,
    )
    val rural4g = NetworkProfile(
        id = "rural4g", name = "Rural 4G", latencyMs = 220, jitterMs = 80,
        download = DirectionLimits(2_000, 5f, .2f, .05f, 3f),
        upload = DirectionLimits(512, 7f, .2f, .05f, 3f),
        queuePackets = 160,
    )
    val fading4g = NetworkProfile(
        id = "fading4g", name = "Fading 4G", latencyMs = 280, jitterMs = 120,
        download = DirectionLimits(1_500, 6f, .25f, .05f, 3f),
        upload = DirectionLimits(512, 8f, .25f, .05f, 3f),
        queuePackets = 160,
        burst = BurstSchedule(impairedSeconds = 20, healthySeconds = 5),
    )
    val good3g = NetworkProfile(
        id = "good3g", name = "Good 3G", latencyMs = 180, jitterMs = 60,
        download = DirectionLimits(1_800, 4f, .15f, .03f, 2f),
        upload = DirectionLimits(512, 6f, .15f, .03f, 2f),
        queuePackets = 160,
    )
    val typical3g = NetworkProfile(
        id = "typical3g", name = "Typical 3G", latencyMs = 300, jitterMs = 100,
        download = DirectionLimits(1_000, 7f, .25f, .05f, 3f),
        upload = DirectionLimits(384, 9f, .25f, .05f, 3f),
        queuePackets = 128,
    )
    val slow3g = NetworkProfile(
        id = "slow3g", name = "Slow 3G", latencyMs = 450, jitterMs = 160,
        download = DirectionLimits(768, 12f, .4f, .1f, 4f),
        upload = DirectionLimits(256, 14f, .4f, .1f, 4f),
        queuePackets = 128,
        burst = BurstSchedule(impairedSeconds = 20, healthySeconds = 5),
    )
    val fading3g = NetworkProfile(
        id = "fading3g", name = "Fading 3G", latencyMs = 650, jitterMs = 240,
        download = DirectionLimits(256, 18f, .6f, .15f, 6f),
        upload = DirectionLimits(96, 22f, .6f, .15f, 6f),
        queuePackets = 96,
        burst = BurstSchedule(impairedSeconds = 12, healthySeconds = 3),
    )
    val goodEdge = NetworkProfile(
        id = "good_edge", name = "Good EDGE", latencyMs = 550, jitterMs = 180,
        download = DirectionLimits(256, 8f, .3f, .05f, 3f),
        upload = DirectionLimits(96, 12f, .3f, .05f, 3f),
        queuePackets = 128,
    )
    val normalEdge = NetworkProfile(
        id = "normal_edge", name = "Normal EDGE", latencyMs = 700, jitterMs = 220,
        download = DirectionLimits(160, 12f, .4f, .08f, 4f),
        upload = DirectionLimits(64, 16f, .4f, .08f, 4f),
        queuePackets = 112,
    )
    val fadingEdge = NetworkProfile(
        id = "fading_edge", name = "Fading EDGE", latencyMs = 850, jitterMs = 300,
        download = DirectionLimits(128, 16f, .6f, .12f, 5f),
        upload = DirectionLimits(48, 20f, .6f, .12f, 5f),
        queuePackets = 96,
        burst = BurstSchedule(impairedSeconds = 10, healthySeconds = 4),
    )
    val fringeEdge = NetworkProfile(
        id = "fringe_edge", name = "Fringe EDGE", latencyMs = 950, jitterMs = 350,
        download = DirectionLimits(96, 20f, .8f, .2f, 7f),
        upload = DirectionLimits(32, 24f, .8f, .2f, 7f),
        queuePackets = 80,
        burst = BurstSchedule(impairedSeconds = 8, healthySeconds = 4),
    )
    val offline = NetworkProfile(
        id = "offline", name = "Offline", latencyMs = 0, jitterMs = 0,
        download = DirectionLimits(), upload = DirectionLimits(), offline = true,
    )

    val all = listOf(
        normal4g, busy4g, weak4g, rural4g, fading4g,
        good3g, typical3g, slow3g, fading3g,
        goodEdge, normalEdge, fadingEdge, fringeEdge, offline,
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

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
    val goodWifi = NetworkProfile(
        id = "wifi", name = "Good Wi‑Fi", latencyMs = 18, jitterMs = 4,
        download = DirectionLimits(50_000), upload = DirectionLimits(20_000),
    )
    val slow4g = NetworkProfile(
        id = "slow4g", name = "Slow 4G", latencyMs = 85, jitterMs = 25,
        download = DirectionLimits(4_000, .5f), upload = DirectionLimits(1_200, .5f),
    )
    val thirdGeneration = NetworkProfile(
        id = "3g", name = "3G", latencyMs = 220, jitterMs = 70,
        download = DirectionLimits(780, 2f), upload = DirectionLimits(330, 2f),
    )
    val edge = NetworkProfile(
        id = "edge", name = "EDGE", latencyMs = 650, jitterMs = 180,
        download = DirectionLimits(120, 5f), upload = DirectionLimits(60, 5f),
        queuePackets = 96,
    )
    val highLatency = NetworkProfile(
        id = "satellite", name = "High latency", latencyMs = 1_400, jitterMs = 300,
        download = DirectionLimits(2_000, 1f), upload = DirectionLimits(600, 1f),
    )
    val lossy = NetworkProfile(
        id = "lossy", name = "Lossy", latencyMs = 180, jitterMs = 120,
        download = DirectionLimits(1_500, 18f, 2f, .2f, 8f),
        upload = DirectionLimits(700, 12f, 2f, .2f, 8f),
    )
    val flaky = NetworkProfile(
        id = "flaky", name = "Flaky", latencyMs = 350, jitterMs = 250,
        download = DirectionLimits(900, 8f, 1f, .1f, 5f),
        upload = DirectionLimits(300, 8f, 1f, .1f, 5f),
        burst = BurstSchedule(impairedSeconds = 12, healthySeconds = 4),
    )
    val offline = NetworkProfile(
        id = "offline", name = "Offline", latencyMs = 0, jitterMs = 0,
        download = DirectionLimits(), upload = DirectionLimits(), offline = true,
    )

    val all = listOf(goodWifi, slow4g, thirdGeneration, edge, highLatency, lossy, flaky, offline)
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

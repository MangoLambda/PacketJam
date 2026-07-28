package app.packetjam.nativecore

import android.content.Context
import app.packetjam.model.DirectionLimits
import app.packetjam.model.NetworkProfile
import app.packetjam.model.TrafficStats
import app.packetjam.nativecore.packetjamtun.Engine
import app.packetjam.nativecore.packetjamtun.Listener
import app.packetjam.nativecore.packetjamtun.Packetjamtun
import app.packetjam.vpn.NativeTunEngine

/**
 * Android adapter for the ABI-specific Go/gVisor forwarding core packaged in
 * packetjam-tun.aar.
 */
class GvisorTunEngine(
    @Suppress("UNUSED_PARAMETER") context: Context,
    initialProfile: NetworkProfile,
    private val seed: Long,
) : NativeTunEngine {
    @Volatile private var profile = initialProfile
    @Volatile private var engine: Engine? = null
    @Volatile private var connectedAtMs = 0L

    override fun start(tunFileDescriptor: Int, onStats: (TrafficStats) -> Unit) {
        check(engine == null) { "The native forwarding engine is already running" }
        connectedAtMs = System.currentTimeMillis()
        engine = Packetjamtun.start(
            tunFileDescriptor.toLong(),
            profile.toNativeJson(),
            seed,
            object : Listener {
                override fun onStats(
                    uploadBytes: Long,
                    downloadBytes: Long,
                    uploadBps: Long,
                    downloadBps: Long,
                    delayed: Long,
                    dropped: Long,
                    duplicated: Long,
                    corrupted: Long,
                    reordered: Long,
                    queueOverflow: Long,
                ) {
                    onStats(
                        TrafficStats(
                            connectedAtMs = connectedAtMs,
                            uploadBytes = uploadBytes,
                            downloadBytes = downloadBytes,
                            uploadBytesPerSecond = uploadBps,
                            downloadBytesPerSecond = downloadBps,
                            delayed = delayed,
                            dropped = dropped,
                            duplicated = duplicated,
                            corrupted = corrupted,
                            reordered = reordered,
                            queueOverflow = queueOverflow,
                            seed = seed,
                        ),
                    )
                }

                override fun onError(message: String) = Unit
            },
        )
    }

    override fun updateProfile(profile: NetworkProfile) {
        this.profile = profile
        engine?.updateProfile(profile.toNativeJson())
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}

internal fun NetworkProfile.toNativeJson(): String {
    return buildString {
        append('{')
        append("\"latencyMs\":").append(latencyMs)
        append(",\"jitterMs\":").append(jitterMs)
        append(",\"queuePackets\":").append(queuePackets)
        append(",\"offline\":").append(offline)
        append(",\"upload\":").append(upload.toNativeJson())
        append(",\"download\":").append(download.toNativeJson())
        burst?.let {
            append(",\"burst\":{\"impairedSeconds\":")
                .append(it.impairedSeconds)
                .append(",\"healthySeconds\":")
                .append(it.healthySeconds)
                .append('}')
        }
        append('}')
    }
}

private fun DirectionLimits.toNativeJson(): String = buildString {
    append('{')
    append("\"rateKbps\":").append(rateKbps)
    append(",\"lossPercent\":").append(lossPercent)
    append(",\"duplicatePercent\":").append(duplicatePercent)
    append(",\"corruptPercent\":").append(corruptPercent)
    append(",\"reorderPercent\":").append(reorderPercent)
    append('}')
}

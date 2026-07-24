package app.packetjam.nativecore

import android.content.Context
import app.packetjam.model.DirectionLimits
import app.packetjam.model.NetworkProfile
import app.packetjam.model.TrafficStats
import app.packetjam.nativecore.packetjamtun.Engine
import app.packetjam.nativecore.packetjamtun.Listener
import app.packetjam.nativecore.packetjamtun.Packetjamtun
import app.packetjam.vpn.NativeTunEngine
import org.json.JSONObject

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

private fun NetworkProfile.toNativeJson(): String = JSONObject()
    .put("latencyMs", latencyMs)
    .put("jitterMs", jitterMs)
    .put("queuePackets", queuePackets)
    .put("offline", offline)
    .put("upload", upload.toNativeJson())
    .put("download", download.toNativeJson())
    .toString()

private fun DirectionLimits.toNativeJson(): JSONObject = JSONObject()
    .put("rateKbps", rateKbps)
    .put("lossPercent", lossPercent.toDouble())
    .put("duplicatePercent", duplicatePercent.toDouble())
    .put("corruptPercent", corruptPercent.toDouble())
    .put("reorderPercent", reorderPercent.toDouble())

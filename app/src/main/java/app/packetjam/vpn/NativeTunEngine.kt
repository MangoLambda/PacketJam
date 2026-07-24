package app.packetjam.vpn

import android.content.Context
import app.packetjam.model.NetworkProfile
import app.packetjam.model.TrafficStats

/**
 * Boundary for the packaged gVisor/tun2socks forwarding library.
 */
interface NativeTunEngine : AutoCloseable {
    fun start(tunFileDescriptor: Int, onStats: (TrafficStats) -> Unit)
    fun updateProfile(profile: NetworkProfile)

    companion object {
        fun createOrThrow(context: Context, profile: NetworkProfile, seed: Long): NativeTunEngine {
            val implementation = try {
                val type = Class.forName("app.packetjam.nativecore.GvisorTunEngine")
                val constructor = type.getConstructor(
                    Context::class.java,
                    NetworkProfile::class.java,
                    Long::class.javaPrimitiveType,
                )
                constructor.newInstance(context, profile, seed) as NativeTunEngine
            } catch (error: ReflectiveOperationException) {
                throw IllegalStateException(
                    "PacketJam’s native forwarding engine could not be loaded.",
                    error,
                )
            }
            return implementation
        }
    }
}

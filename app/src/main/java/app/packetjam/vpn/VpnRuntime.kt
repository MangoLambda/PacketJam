package app.packetjam.vpn

import app.packetjam.model.NetworkProfile
import app.packetjam.model.TrafficStats
import app.packetjam.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnRuntime {
    private val _status = MutableStateFlow(VpnStatus.STOPPED)
    private val _stats = MutableStateFlow(TrafficStats())
    private val _activeProfile = MutableStateFlow<NetworkProfile?>(null)
    private val _failure = MutableStateFlow<String?>(null)

    val status = _status.asStateFlow()
    val stats = _stats.asStateFlow()
    val activeProfile = _activeProfile.asStateFlow()
    val failure = _failure.asStateFlow()

    fun starting(profile: NetworkProfile) {
        _activeProfile.value = profile
        _failure.value = null
        _status.value = VpnStatus.STARTING
    }

    fun running(stats: TrafficStats) {
        _stats.value = stats
        _status.value = VpnStatus.RUNNING
    }

    fun updateStats(stats: TrafficStats) { _stats.value = stats }
    fun updateProfile(profile: NetworkProfile) { _activeProfile.value = profile }
    fun stopping() { _status.value = VpnStatus.STOPPING }

    fun stopped() {
        _status.value = VpnStatus.STOPPED
        _activeProfile.value = null
        _stats.value = TrafficStats()
    }

    fun failed(message: String) {
        _failure.value = message
        _status.value = VpnStatus.FAILED
    }
}

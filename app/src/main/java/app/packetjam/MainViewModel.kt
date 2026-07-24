package app.packetjam

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.packetjam.model.BuiltInProfiles
import app.packetjam.model.NetworkProfile
import app.packetjam.model.VpnStatus
import app.packetjam.vpn.PacketJamVpnService
import app.packetjam.vpn.VpnRuntime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val selected: NetworkProfile = BuiltInProfiles.fading3g,
    val active: NetworkProfile? = null,
    val status: app.packetjam.model.VpnStatus = app.packetjam.model.VpnStatus.STOPPED,
    val stats: app.packetjam.model.TrafficStats = app.packetjam.model.TrafficStats(),
    val failure: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PacketJamApplication

    val state = combine(
        app.profiles.selectedProfile,
        VpnRuntime.activeProfile,
        VpnRuntime.status,
        VpnRuntime.stats,
        VpnRuntime.failure,
    ) { selected, active, status, stats, failure ->
        MainUiState(selected, active, status, stats, failure)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun select(profile: NetworkProfile) = viewModelScope.launch {
        app.profiles.select(profile)
        if (VpnRuntime.status.value == VpnStatus.RUNNING) {
            app.startService(
                Intent(app, PacketJamVpnService::class.java)
                    .setAction(PacketJamVpnService.ACTION_UPDATE),
            )
        }
    }

    fun start() {
        ContextCompat.startForegroundService(
            app,
            Intent(app, PacketJamVpnService::class.java).setAction(PacketJamVpnService.ACTION_START),
        )
    }

    fun stop() {
        app.startService(
            Intent(app, PacketJamVpnService::class.java).setAction(PacketJamVpnService.ACTION_STOP),
        )
    }

    fun permissionIntent(): Intent? = VpnService.prepare(app)
}

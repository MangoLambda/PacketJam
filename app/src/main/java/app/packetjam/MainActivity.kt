package app.packetjam

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.packetjam.model.BuiltInProfiles
import app.packetjam.model.NetworkProfile
import app.packetjam.model.TrafficStats
import app.packetjam.model.VpnStatus
import java.util.Locale

private val Ink = Color(0xFF081015)
private val Panel = Color(0xFF111C22)
private val Mint = Color(0xFF8CF7C5)
private val Orange = Color(0xFFFFB86C)
private val Muted = Color(0xFF91A3AD)

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val vpnPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result -> if (result.resultCode == RESULT_OK) viewModel.start() }
            val notifications = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {}

            PacketJamTheme {
                Dashboard(
                    state = state,
                    onSelect = viewModel::select,
                    onToggle = {
                        if (state.status == VpnStatus.RUNNING || state.status == VpnStatus.STARTING) {
                            viewModel.stop()
                        } else {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            val intent = viewModel.permissionIntent()
                            if (intent == null) viewModel.start() else vpnPermission.launch(intent)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PacketJamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Mint, secondary = Orange, background = Ink, surface = Panel,
            onPrimary = Ink, onBackground = Color.White, onSurface = Color.White,
        ),
        content = content,
    )
}

@Composable
private fun Dashboard(
    state: MainUiState,
    onSelect: (NetworkProfile) -> Unit,
    onToggle: () -> Unit,
) {
    val running = state.status == VpnStatus.RUNNING
    Scaffold(containerColor = Ink) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset).padding(top = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(running)
            Spacer(Modifier.height(26.dp))
            PowerControl(state, onToggle)
            Spacer(Modifier.height(28.dp))
            ProfileStrip(state.selected, onSelect, enabled = !running)
            Spacer(Modifier.height(18.dp))
            StatsPanel(state.stats, state.selected)
            AnimatedVisibility(state.failure != null) {
                Text(
                    state.failure.orEmpty(), color = Color(0xFFFF8C8C), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "LOCAL VPN · NO PAYLOAD INSPECTION",
                color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun Header(running: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(Mint, RoundedCornerShape(12.dp)), Alignment.Center) {
            Icon(Icons.Rounded.Shield, null, tint = Ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("PACKETJAM", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text("NETWORK CHAOS LAB", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (running) Mint else Muted, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(if (running) "LIVE" else "IDLE", color = if (running) Mint else Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PowerControl(state: MainUiState, onToggle: () -> Unit) {
    val running = state.status == VpnStatus.RUNNING
    val busy = state.status == VpnStatus.STARTING || state.status == VpnStatus.STOPPING
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(148.dp).clickable(enabled = !busy, onClick = onToggle),
            shape = CircleShape,
            color = if (running) Mint else Panel,
            border = androidx.compose.foundation.BorderStroke(2.dp, if (running) Mint else Color(0xFF2A3A43)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.PowerSettingsNew, null,
                    tint = if (running) Ink else Mint, modifier = Modifier.size(54.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            when (state.status) {
                VpnStatus.RUNNING -> "SHAPING TRAFFIC"
                VpnStatus.STARTING -> "STARTING…"
                VpnStatus.STOPPING -> "STOPPING…"
                VpnStatus.FAILED -> "START FAILED"
                else -> "TAP TO JAM"
            },
            color = if (running) Mint else Muted, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp,
        )
    }
}

@Composable
private fun ProfileStrip(
    selected: NetworkProfile,
    onSelect: (NetworkProfile) -> Unit,
    enabled: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "CONDITION PROFILE", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            BuiltInProfiles.all.forEach { profile ->
                val active = selected.id == profile.id
                Card(
                    modifier = Modifier.width(128.dp).clickable(enabled) { onSelect(profile) },
                    colors = CardDefaults.cardColors(containerColor = if (active) Color(0xFF20352F) else Panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Mint else Color(0xFF213039)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(profile.name, color = if (active) Mint else Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(profile.summary(), color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsPanel(stats: TrafficStats, profile: NetworkProfile) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${profile.latencyMs} ± ${profile.jitterMs} ms", color = Orange, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(18.dp))
            Row {
                Rate(
                    Icons.Rounded.ArrowDownward,
                    "DOWN",
                    stats.downloadBytesPerSecond,
                    stats.downloadBytes,
                    "received",
                    profile.download.rateKbps,
                    Modifier.weight(1f),
                )
                Rate(
                    Icons.Rounded.ArrowUpward,
                    "UP",
                    stats.uploadBytesPerSecond,
                    stats.uploadBytes,
                    "sent",
                    profile.upload.rateKbps,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Counter("DROPPED", stats.dropped)
                Counter("DELAYED", stats.delayed)
                Counter("REORDERED", stats.reordered)
                Counter("CORRUPT", stats.corrupted)
            }
        }
    }
}

@Composable
private fun Rate(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bytesPerSecond: Long,
    totalBytes: Long,
    totalLabel: String,
    limit: Int,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Mint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(
                if (bytesPerSecond == 0L) (if (limit == 0) "∞" else "$limit kbps") else "${bytesPerSecond / 1024} KB/s",
                color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            )
            Text(
                "${formatBytes(totalBytes)} $totalLabel",
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB", "EB")
    var value = bytes.toDouble()
    var unitIndex = -1
    do {
        value /= 1024
        unitIndex++
    } while (value >= 1024 && unitIndex < units.lastIndex)
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

@Composable
private fun Counter(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 8.sp, letterSpacing = .7.sp)
    }
}

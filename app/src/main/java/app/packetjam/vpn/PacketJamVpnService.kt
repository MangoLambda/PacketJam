package app.packetjam.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import app.packetjam.MainActivity
import app.packetjam.PacketJamApplication
import app.packetjam.R
import app.packetjam.model.NetworkProfile
import app.packetjam.model.TrafficStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.SecureRandom

class PacketJamVpnService : VpnService() {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var sessionSeed = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTunnel()
            ACTION_UPDATE -> scope.launch {
                val profile = (application as PacketJamApplication).profiles.selectedProfile.first()
                activeEngine?.updateProfile(profile)
                VpnRuntime.updateProfile(profile)
            }
            ACTION_START, null -> scope.launch {
                val profile = (application as PacketJamApplication).profiles.selectedProfile.first()
                startTunnel(profile)
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(profile: NetworkProfile) {
        if (tun != null) return
        VpnRuntime.starting(profile)
        startForeground(NOTIFICATION_ID, notification(profile))
        sessionSeed = SecureRandom().nextLong()
        var startingEngine: NativeTunEngine? = null
        try {
            /*
             * Establish the virtual interface only after the forwarding core is ready. The
             * NativeTunEngine boundary intentionally owns packet forwarding; this prevents a
             * partially initialized build from silently black-holing all device traffic.
             */
            val engine = NativeTunEngine.createOrThrow(this, profile, sessionSeed)
            startingEngine = engine
            tun = Builder()
                .setSession("PacketJam · ${profile.name}")
                .setMtu(1500)
                .addAddress("10.73.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd73:706a:616d::1", 126)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("2606:4700:4700::1111")
                // The Go forwarder's direct TCP/UDP sockets originate in this
                // process and must use the underlying network, not this TUN.
                .addDisallowedApplication(packageName)
                .setBlocking(true)
                .establish() ?: error("Android did not establish the VPN interface")
            engine.start(tun!!.fd) { VpnRuntime.updateStats(it) }
            activeEngine = engine
            startingEngine = null
            VpnRuntime.running(TrafficStats(connectedAtMs = System.currentTimeMillis(), seed = sessionSeed))
        } catch (error: Throwable) {
            startingEngine?.close()
            tun?.close()
            tun = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            VpnRuntime.failed(error.message ?: "Unable to start the local forwarding engine")
            stopSelf()
        }
    }

    private fun stopTunnel() {
        VpnRuntime.stopping()
        activeEngine?.close()
        activeEngine = null
        tun?.close()
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        VpnRuntime.stopped()
        stopSelf()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        activeEngine?.close()
        tun?.close()
        scope.cancel()
        VpnRuntime.stopped()
        super.onDestroy()
    }

    private fun notification(profile: NetworkProfile) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_packetjam)
            .setContentTitle("PacketJam is active")
            .setContentText("${profile.name} · traffic stays on this device")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .addAction(
                0, "Stop",
                PendingIntent.getService(
                    this, 1, Intent(this, PacketJamVpnService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.vpn_channel_name), NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.vpn_channel_description) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "app.packetjam.START"
        const val ACTION_STOP = "app.packetjam.STOP"
        const val ACTION_UPDATE = "app.packetjam.UPDATE"
        private const val CHANNEL_ID = "packetjam_vpn"
        private const val NOTIFICATION_ID = 73
        private var activeEngine: NativeTunEngine? = null
    }
}

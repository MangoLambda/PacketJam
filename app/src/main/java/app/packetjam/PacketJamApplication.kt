package app.packetjam

import android.app.Application
import app.packetjam.data.ProfileRepository

class PacketJamApplication : Application() {
    val profiles by lazy { ProfileRepository(this) }
}

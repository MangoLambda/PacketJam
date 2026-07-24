package app.packetjam.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.packetjam.model.BuiltInProfiles
import app.packetjam.model.NetworkProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore("packetjam_profiles")

class ProfileRepository(private val context: Context) {
    private val selectedKey = stringPreferencesKey("selected_profile")

    val selectedProfile: Flow<NetworkProfile> = context.profileDataStore.data.map { preferences ->
        val id = preferences[selectedKey] ?: BuiltInProfiles.thirdGeneration.id
        BuiltInProfiles.all.firstOrNull { it.id == id } ?: BuiltInProfiles.thirdGeneration
    }

    suspend fun select(profile: NetworkProfile) {
        context.profileDataStore.edit { it[selectedKey] = profile.id }
    }
}

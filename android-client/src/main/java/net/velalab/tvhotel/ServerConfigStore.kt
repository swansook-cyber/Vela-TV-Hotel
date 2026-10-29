package net.velalab.tvhotel

import android.content.Context

/**
 * Stores only the non-secret HTTPS endpoint. Never put enrollment tokens here.
 * Credentials require Keystore-backed secure storage in a later milestone.
 */
class ServerConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences("hotel_server", Context.MODE_PRIVATE)

    fun active(): ServerEndpoint? =
        preferences.getString("active_url", null)?.let { raw ->
            ServerEndpoint(raw).takeIf { it.normalizedHttpsUrl() != null }
        }

    fun saveVerifiedEndpoint(endpoint: ServerEndpoint): Boolean {
        val normalized = endpoint.normalizedHttpsUrl() ?: return false
        return preferences.edit().putString("active_url", normalized).commit()
    }

    fun clearForPilotOnly() {
        preferences.edit().remove("active_url").apply()
    }
}

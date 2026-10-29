package net.velalab.tvhotel

import java.net.HttpURLConnection
import java.net.URL

/**
 * Small connectivity probe. Must be called on a worker thread.
 * This is NOT a migration validator or an enrollment implementation.
 */
class ManifestClient {
    fun probe(endpoint: ServerEndpoint): Boolean {
        val base = endpoint.normalizedHttpsUrl() ?: return false
        val url = URL("$base/healthz")
        val connection = (url.openConnection() as HttpURLConnection)
        return try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.responseCode == 200
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}

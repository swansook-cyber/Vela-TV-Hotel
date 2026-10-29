package net.velalab.tvhotel

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Explicit, worker-thread-only pilot sync. Caller supplies an enrolled device token.
 * Do not schedule until enrollment, secure token storage and UI are implemented.
 */
class MediaSyncCoordinator(private val context: Context) {
    enum class Status { UPDATED, OFFLINE_UNCHANGED, REJECTED }
    private val cache = VerifiedMediaCache(context)
    private val downloader = HttpsMediaDownloader()

    fun sync(endpoint: ServerEndpoint, deviceToken: String, mediaHost: String): Status {
        val base = endpoint.normalizedHttpsUrl() ?: return Status.REJECTED
        if (deviceToken.isBlank() || mediaHost.isBlank()) return Status.REJECTED
        val connection = (URL("$base/api/v1/device/manifest").openConnection()
            as HttpURLConnection)
        val manifest = try {
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $deviceToken")
            val response = connection.responseCode
            if (response == 401 || response == 403) return Status.REJECTED
            if (response != 200) return Status.OFFLINE_UNCHANGED
            if (connection.contentLengthLong > 1024 * 1024) return Status.REJECTED
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 1024 * 1024) return Status.REJECTED
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            return Status.OFFLINE_UNCHANGED
        } finally {
            connection.disconnect()
        }

        val media = manifest.optJSONArray("media")
        if (manifest.optInt("schema_version", -1) != 1 ||
            manifest.optString("revision").isBlank() ||
            manifest.optJSONObject("hotel")?.optString("name").isNullOrBlank() ||
            media == null || media.length() !in 1..30) {
            return Status.REJECTED
        }
        val directory = File(context.cacheDir, "hotel-download-${System.nanoTime()}")
        val downloaded = downloader.download(manifest, directory, mediaHost)
            ?: return Status.OFFLINE_UNCHANGED
        return try {
            if (cache.activate(downloaded.manifest, downloaded.directory)) {
                Status.UPDATED
            } else {
                Status.OFFLINE_UNCHANGED
            }
        } finally {
            downloaded.directory.deleteRecursively()
        }
    }
}

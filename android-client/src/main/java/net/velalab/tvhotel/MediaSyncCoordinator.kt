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
    var assignedRoom: String = ""
        private set
    private val cache = VerifiedMediaCache(context)
    private val downloader = HttpsMediaDownloader()

    private fun saveContent(content: JSONObject): Boolean {
        if (content.optInt("schema_version", -1) != 1 ||
            content.optJSONObject("hotel")?.optString("name") != "Ao Nang Fiore Resort" ||
            content.optJSONArray("quick_apps")?.length() != 5 ||
            listOf("resort", "explore", "guest").any {
                content.optJSONObject(it)?.optJSONArray("items") == null
            }) return false
        val target = File(context.filesDir, "published-content.json")
        val temp = File(context.filesDir, "published-content.tmp")
        return try {
            temp.writeText(content.toString())
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            true
        } catch (_: Exception) {
            temp.delete()
            false
        }
    }

    fun sync(endpoint: ServerEndpoint, deviceToken: String, mediaHost: String): Status {
        val base = endpoint.normalizedHttpsUrl() ?: return Status.REJECTED
        if (deviceToken.isBlank() || mediaHost.isBlank()) return Status.REJECTED
        val connection = try {
            URL("$base/api/v1/device/manifest").openConnection() as HttpURLConnection
        } catch (_: Exception) { return Status.OFFLINE_UNCHANGED }
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
            media == null || media.length() !in 0..30) {
            return Status.REJECTED
        }
        val content = manifest.optJSONObject("content") ?: return Status.REJECTED
        assignedRoom = manifest.optString("room", "").takeIf {
            it.matches(Regex("[A-Za-z0-9 -]{0,30}"))
        } ?: ""
        if (media.length() == 0) {
            return if (saveContent(content)) Status.UPDATED else Status.OFFLINE_UNCHANGED
        }
        val directory = File(context.cacheDir, "hotel-download-${System.nanoTime()}")
        val downloaded = downloader.download(manifest, directory, mediaHost, deviceToken)
            ?: return Status.OFFLINE_UNCHANGED
        return try {
            if (cache.activate(downloaded.manifest, downloaded.directory) && saveContent(content))
                Status.UPDATED else Status.OFFLINE_UNCHANGED
        } finally { downloaded.directory.deleteRecursively() }
    }
}

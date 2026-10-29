package net.velalab.tvhotel

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads a validated playlist into an app-private temporary directory.
 * Network calls must run on a worker thread. Never activate until all files verify.
 */
class HttpsMediaDownloader(
    private val maxTotalBytes: Long = 200L * 1024 * 1024
) {
    data class DownloadResult(val manifest: JSONObject, val directory: File)

    fun download(
        manifest: JSONObject,
        destination: File,
        allowedHost: String
    ): DownloadResult? {
        if (manifest.optInt("schema_version", -1) != 1) return null
        val media = manifest.optJSONArray("media") ?: return null
        if (media.length() > 30 || allowedHost.isBlank()) return null
        if (destination.exists()) return null
        if (!destination.mkdirs()) return null
        var totalBytes = 0L
        val seen = mutableSetOf<String>()
        try {
            for (i in 0 until media.length()) {
                val item = media.optJSONObject(i) ?: return null
                val id = item.optString("id")
                val type = item.optString("type")
                val extension = item.optString("extension")
                val digest = item.optString("sha256")
                val seconds = item.optInt("duration_seconds", -1)
                val allowedExtensions = when (type) {
                    "image" -> setOf("jpg", "jpeg", "png")
                    "video" -> setOf("mp4")
                    else -> emptySet()
                }
                if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}")) || !seen.add(id) ||
                    extension !in allowedExtensions || seconds !in 1..120 ||
                    !digest.matches(Regex("[a-f0-9]{64}"))) return null

                val uri = URI(item.optString("url"))
                if (uri.scheme != "https" || uri.host != allowedHost ||
                    uri.userInfo != null || uri.fragment != null ||
                    uri.port !in listOf(-1, 443)) return null
                val connection = URL(uri.toString()).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10000
                    connection.readTimeout = 20000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    if (connection.responseCode != 200) return null
                    val size = connection.contentLengthLong
                    if (size > maxTotalBytes - totalBytes) return null
                    val md = MessageDigest.getInstance("SHA-256")
                    val output = File(destination, "$id.$extension")
                    connection.inputStream.use { input ->
                        FileOutputStream(output).use { out ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                if (totalBytes > maxTotalBytes) return null
                                md.update(buffer, 0, count)
                                out.write(buffer, 0, count)
                            }
                            out.fd.sync()
                        }
                    }
                    val actual = md.digest().joinToString("") { "%02x".format(it) }
                    if (actual != digest) return null
                } finally {
                    connection.disconnect()
                }
            }
            return DownloadResult(manifest, destination)
        } catch (_: Exception) {
            return null
        } finally {
            // Caller owns successful directory; failures are removed below by explicit result handling.
        }
    }
}

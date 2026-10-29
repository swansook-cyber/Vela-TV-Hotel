package net.velalab.tvhotel

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Offline cache importer for already-downloaded media.
 * Network transport and authenticated enrollment are separate future components.
 */
class VerifiedMediaCache(context: Context) {
    private val root = File(context.filesDir, "hotel-media")
    private val active = File(root, "active")
    private val staging = File(root, "staging")
    private val previous = File(root, "previous")
    private val allowedExtensions = mapOf("image" to setOf("jpg", "jpeg", "png"),
        "video" to setOf("mp4"))
    private val maxBytes = 200L * 1024 * 1024

    data class Item(val id: String, val type: String, val sha256: String,
                    val extension: String, val durationSeconds: Int)

    /** Source files must already be downloaded into an app-private staging directory. */
    @Synchronized fun activate(manifest: JSONObject, source: File): Boolean {
        if (manifest.optInt("schema_version") != 1 || !source.isDirectory) return false
        val revision = manifest.optString("revision")
        if (revision.isBlank()) return false
        val media = manifest.optJSONArray("media") ?: return false
        // Refuse empty sets so a placeholder API cannot blank a functioning TV.
        if (media.length() !in 1..30) return false
        val items = mutableListOf<Item>()
        val seen = mutableSetOf<String>()
        for (i in 0 until media.length()) {
            val obj = media.optJSONObject(i) ?: return false
            val id = obj.optString("id")
            val type = obj.optString("type")
            val digest = obj.optString("sha256")
            val ext = obj.optString("extension").lowercase()
            val seconds = obj.optInt("duration_seconds", -1)
            if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}")) || !seen.add(id) ||
                ext !in (allowedExtensions[type] ?: emptySet()) ||
                !digest.matches(Regex("[a-f0-9]{64}")) ||
                seconds !in 1..120) return false
            items.add(Item(id, type, digest, ext, seconds))
        }
        if (!root.exists() && !root.mkdirs()) return false
        if (staging.exists()) staging.deleteRecursively()
        if (!staging.mkdirs()) return false
        var total = 0L
        try {
            for (item in items) {
                val filename = "${item.id}.${item.extension}"
                val input = File(source, filename)
                if (!input.isFile || input.canonicalFile.parentFile != source.canonicalFile) return false
                val output = File(staging, filename)
                val md = MessageDigest.getInstance("SHA-256")
                FileInputStream(input).use { stream ->
                    FileOutputStream(output).use { dest ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = stream.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) return false
                            md.update(buf, 0, n)
                            dest.write(buf, 0, n)
                        }
                        dest.fd.sync()
                    }
                }
                val actual = md.digest().joinToString("") { "%02x".format(it) }
                if (actual != item.sha256) return false
            }
            File(staging, "manifest.json").writeText(manifest.toString())
            if (previous.exists()) previous.deleteRecursively()
            if (active.exists() && !active.renameTo(previous)) return false
            if (!staging.renameTo(active)) {
                if (previous.exists()) previous.renameTo(active)
                return false
            }
            return true
        } catch (_: Exception) {
            return false
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    /**
     * Recover from interrupted active -> previous -> staging activation.
     * Only restore previous when no active directory exists.
     */
    @Synchronized fun recoverInterruptedActivation(): Boolean {
        if (active.isDirectory && File(active, "manifest.json").isFile) return true
        if (active.exists()) return false
        return previous.isDirectory &&
            File(previous, "manifest.json").isFile &&
            previous.renameTo(active)
    }

    private fun verifiedSlides(directory: File): List<MediaSlideshow.Slide>? {
        val manifestFile = File(directory, "manifest.json")
        if (!manifestFile.isFile) return null
        return try {
            val manifest = JSONObject(manifestFile.readText())
            if (manifest.optInt("schema_version", -1) != 1) return null
            val media = manifest.getJSONArray("media")
            if (media.length() !in 1..30) return null
            var total = 0L
            (0 until media.length()).map { i ->
                val item = media.getJSONObject(i)
                val id = item.getString("id")
                val ext = item.getString("extension")
                val digest = item.getString("sha256")
                if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}")) ||
                    ext !in setOf("jpg", "jpeg", "png", "mp4") ||
                    !digest.matches(Regex("[a-f0-9]{64}"))) return null
                val file = File(directory, "$id.$ext")
                if (!file.isFile || file.canonicalFile.parentFile != directory.canonicalFile) return null
                val hash = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { stream ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) return null
                        hash.update(buffer, 0, n)
                    }
                }
                if (hash.digest().joinToString("") { "%02x".format(it) } != digest) return null
                MediaSlideshow.Slide(file, ext == "mp4",
                    item.getLong("duration_seconds") * 1000L)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Call on a worker thread. A corrupted active set falls back to the prior verified set. */
    @Synchronized fun activeSlides(): List<MediaSlideshow.Slide> {
        recoverInterruptedActivation()
        return verifiedSlides(active) ?: verifiedSlides(previous) ?: emptyList()
    }
}

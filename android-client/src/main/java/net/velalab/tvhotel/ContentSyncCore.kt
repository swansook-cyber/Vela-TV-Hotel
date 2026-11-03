package net.velalab.tvhotel

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.time.Instant

enum class ContentSyncMode { OFFLINE_ONLY, AUTO_SYNC }
enum class ContentSource { BUNDLED, LEGACY_CACHE, ACTIVE, PREVIOUS }
enum class ContentSyncStatus { UPDATED, UNCHANGED, OFFLINE, REJECTED, INCOMPATIBLE, UNSUPPORTED }

data class ContentFile(
    val kind: String, val path: String, val downloadPath: String,
    val sha256: String, val size: Long, val durationSeconds: Int = 0
)
data class ContentManifest(val version: String, val publishedAt: String,
                           val files: List<ContentFile>)
data class ContentSnapshot(val content: JSONObject, val source: ContentSource,
                           val version: String? = null, val directory: File? = null,
                           val manifest: ContentManifest? = null)
data class ContentSyncResult(val status: ContentSyncStatus, val snapshot: ContentSnapshot)

/** Mirrors the published JSON Schemas without adding an Android runtime dependency. */
object ContentSyncContract {
    const val APP_VERSION_CODE = 15
    private val shaPattern = Regex("[0-9a-f]{64}")
    private val versionPattern = Regex("[1-9][0-9]*")
    private val mediaPattern = Regex("media/[A-Za-z0-9_-]{1,64}\\.(jpg|jpeg|png|mp4)")
    private val quickApps = listOf("Netflix", "Disney+", "YouTube", "Prime Video", "iQIYI")

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun validContent(bytes: ByteArray): JSONObject? {
        if (bytes.isEmpty() || bytes.size > 256 * 1024) return null
        val content = try { JSONObject(String(bytes, Charsets.UTF_8)) } catch (_: Exception) { return null }
        if (content.opt("schema_version") !is Int || content.optInt("schema_version") != 1 ||
            content.optJSONObject("hotel")?.optString("name") != "Ao Nang Fiore Resort") return null
        val welcome = content.optJSONObject("guest_welcome") ?: return null
        if (welcome.optString("mode") != "generic" || welcome.optString("value") != "") return null
        val apps = content.optJSONArray("quick_apps") ?: return null
        if (apps.length() != quickApps.size || quickApps.indices.any { apps.optString(it) != quickApps[it] })
            return null
        for (section in listOf("resort", "explore", "guest")) {
            val items = content.optJSONObject(section)?.optJSONArray("items") ?: return null
            if (items.length() > 40) return null
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: return null
                val keys = item.keys().asSequence().toSet()
                if (!keys.containsAll(listOf("title", "description", "verified")) ||
                    keys.any { it !in setOf("title", "description", "verified", "category") } ||
                    item.opt("title") !is String || item.opt("description") !is String) return null
                val title = item.optString("title")
                val description = item.optString("description")
                if (title.length !in 1..100 || description.length > 2000 ||
                    item.opt("verified") !is Boolean ||
                    (!item.getBoolean("verified") && !description.contains("TEST DATA"))) return null
            }
        }
        if (content.has("approved_content") && content.optJSONObject("approved_content") == null)
            return null
        return content
    }

    fun parseManifest(bytes: ByteArray, appVersionCode: Int = APP_VERSION_CODE): ContentManifest? {
        if (bytes.isEmpty() || bytes.size > 64 * 1024) return null
        val root = try { JSONObject(String(bytes, Charsets.UTF_8)) } catch (_: Exception) { return null }
        if (root.keys().asSequence().toSet() !=
            setOf("schemaVersion", "contentVersion", "publishedAt", "minAppVersionCode", "files")) return null
        if (root.opt("schemaVersion") !is Int || root.optInt("schemaVersion") != 1 ||
            root.opt("minAppVersionCode") !is Int ||
            root.optInt("minAppVersionCode") !in 15..appVersionCode) return null
        val version = root.optString("contentVersion")
        val publishedAt = root.optString("publishedAt")
        if (!versionPattern.matches(version) || !publishedAt.endsWith("Z") ||
            runCatching { Instant.parse(publishedAt) }.isFailure) return null
        val list = root.optJSONArray("files") ?: return null
        if (list.length() !in 1..31) return null
        val files = mutableListOf<ContentFile>()
        val paths = mutableSetOf<String>()
        var total = 0L
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: return null
            val kind = item.optString("kind")
            val expectedKeys = setOf("kind", "path", "downloadPath", "sha256", "size") +
                (if (kind == "content") emptySet() else setOf("durationSeconds"))
            if (item.keys().asSequence().toSet() != expectedKeys) return null
            val path = item.optString("path")
            val downloadPath = item.optString("downloadPath")
            val digest = item.optString("sha256")
            val sizeValue = item.opt("size") as? Number ?: return null
            val size = sizeValue.toLong()
            if (!shaPattern.matches(digest) || !paths.add(path) || size <= 0 ||
                sizeValue.toDouble() != size.toDouble()) return null
            val duration = if (kind == "content") 0 else item.optInt("durationSeconds", -1)
            if (kind == "content") {
                if (i != 0 || path != "content.json" || size > 256 * 1024 ||
                    downloadPath != "/api/v1/device/content/$version/$digest.json" ||
                    item.has("durationSeconds")) return null
            } else {
                val extension = path.substringAfterLast('.', "")
                if (!mediaPattern.matches(path) || size > 50L * 1024 * 1024 ||
                    kind != (if (extension == "mp4") "video" else "image") ||
                    duration !in 1..120 ||
                    downloadPath != "/api/v1/media/$digest.$extension") return null
            }
            total += size
            if (total > 200L * 1024 * 1024) return null
            files.add(ContentFile(kind, path, downloadPath, digest, size, duration))
        }
        return ContentManifest(version, publishedAt, files)
    }
}

/** File-backed, single-generation rollback. All files are verified before the pointer switch. */
class ContentPackageStore(
    private val root: File,
    private val bundled: ByteArray,
    private val legacy: File? = null,
    private val promote: (File, File) -> Boolean = { from, to -> from.renameTo(to) }
) {
    private val active = File(root, "active")
    private val previous = File(root, "previous")
    private val staging = File(root, "staging")

    private fun readPackage(directory: File, source: ContentSource): ContentSnapshot? {
        if (!directory.isDirectory) return null
        val manifestBytes = runCatching { File(directory, "manifest.json").readBytes() }.getOrNull()
            ?: return null
        val manifest = ContentSyncContract.parseManifest(manifestBytes) ?: return null
        for (item in manifest.files) {
            val file = File(directory, item.path)
            if (!file.isFile || !file.canonicalPath.startsWith(directory.canonicalPath + File.separator) ||
                file.length() != item.size ||
                ContentSyncContract.sha256(file) != item.sha256) return null
        }
        val content = ContentSyncContract.validContent(File(directory, "content.json").readBytes())
            ?: return null
        return ContentSnapshot(content, source, manifest.version, directory, manifest)
    }

    @Synchronized fun current(): ContentSnapshot {
        runCatching { readPackage(active, ContentSource.ACTIVE) }.getOrNull()?.let { return it }
        runCatching { readPackage(previous, ContentSource.PREVIOUS) }.getOrNull()?.let { return it }
        legacy?.takeIf { it.isFile }?.let { file ->
            runCatching { ContentSyncContract.validContent(file.readBytes()) }.getOrNull()
                ?.let { return ContentSnapshot(it, ContentSource.LEGACY_CACHE) }
        }
        val content = ContentSyncContract.validContent(bundled)
            ?: throw IllegalStateException("Invalid bundled hotel content")
        return ContentSnapshot(content, ContentSource.BUNDLED)
    }

    fun downloadDirectory(): File {
        if (!root.exists() && !root.mkdirs()) throw IOException("Content directory unavailable")
        val directory = File(root, "download-${System.nanoTime()}")
        if (!directory.mkdirs()) throw IOException("Download directory unavailable")
        return directory
    }

    @Synchronized fun activate(manifestBytes: ByteArray, downloaded: File): Boolean {
        val manifest = ContentSyncContract.parseManifest(manifestBytes) ?: return false
        if (!downloaded.isDirectory) return false
        for (item in manifest.files) {
            val file = File(downloaded, item.path)
            if (!file.isFile || !file.canonicalPath.startsWith(downloaded.canonicalPath + File.separator) ||
                file.length() != item.size || ContentSyncContract.sha256(file) != item.sha256)
                return false
        }
        if (ContentSyncContract.validContent(File(downloaded, "content.json").readBytes()) == null)
            return false
        if (!root.exists() && !root.mkdirs()) return false
        // An interrupted prior run may leave a corrupt active directory beside a
        // verified previous package. Restore that known-good generation first.
        if (active.exists() && runCatching { readPackage(active, ContentSource.ACTIVE) }.getOrNull() == null &&
            runCatching { readPackage(previous, ContentSource.PREVIOUS) }.getOrNull() != null) {
            if (!active.deleteRecursively() || !previous.renameTo(active)) return false
        }
        if (staging.exists() && !staging.deleteRecursively()) return false
        if (!staging.mkdirs()) return false
        try {
            for (item in manifest.files) {
                val output = File(staging, item.path)
                val parent = output.parentFile ?: return false
                if (parent != staging && !parent.isDirectory && !parent.mkdirs()) return false
                FileOutputStream(output).use { stream ->
                    FileInputStream(File(downloaded, item.path)).use { input -> input.copyTo(stream, 65536) }
                    stream.fd.sync()
                }
            }
            FileOutputStream(File(staging, "manifest.json")).use { stream ->
                stream.write(manifestBytes)
                stream.fd.sync()
            }
            if (runCatching { readPackage(staging, ContentSource.ACTIVE) }.getOrNull() == null) return false
            if (previous.exists() && !previous.deleteRecursively()) return false
            val hadActive = active.exists()
            if (hadActive && !active.renameTo(previous)) return false
            if (!promote(staging, active)) {
                if (hadActive) previous.renameTo(active)
                return false
            }
            if (runCatching { readPackage(active, ContentSource.ACTIVE) }.getOrNull() == null) {
                active.deleteRecursively()
                if (hadActive) previous.renameTo(active)
                return false
            }
            return true
        } catch (_: Exception) {
            return false
        } finally {
            if (!active.exists() && previous.isDirectory) previous.renameTo(active)
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    @Synchronized fun mediaSlides(): List<MediaSlideshow.Slide> {
        val snapshot = current()
        val directory = snapshot.directory ?: return emptyList()
        return snapshot.manifest?.files?.filter { it.kind != "content" }?.map { item ->
            MediaSlideshow.Slide(File(directory, item.path), item.kind == "video",
                item.durationSeconds * 1000L)
        } ?: emptyList()
    }
}

interface ContentFetcher { fun fetch(path: String, maxBytes: Long, output: OutputStream): Long }
class ContentHttpFailure(val status: Int) : IOException("Content HTTP $status")

/** Same-origin HTTPS transport; no redirects, query strings, fragments or credentials in URLs. */
class HttpsContentFetcher(private val endpoint: ServerEndpoint, private val deviceToken: String) : ContentFetcher {
    override fun fetch(path: String, maxBytes: Long, output: OutputStream): Long {
        val base = endpoint.normalizedHttpsUrl() ?: throw IOException("HTTPS endpoint required")
        if (deviceToken.isBlank() || !path.startsWith("/api/v1/") ||
            path.contains('?') || path.contains('#') || path.contains("..") ||
            path.any { it.code < 32 }) throw IOException("Invalid content path")
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Authorization", "Bearer $deviceToken")
            if (connection.responseCode != 200) throw ContentHttpFailure(connection.responseCode)
            if (connection.contentLengthLong > maxBytes) throw IOException("Content too large")
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw IOException("Content too large")
                    output.write(buffer, 0, count)
                }
                return total
            }
        } finally { connection.disconnect() }
    }
}

class UniversalContentResolver(private val store: ContentPackageStore,
                               private val fetcher: ContentFetcher?) {
    fun resolve(mode: ContentSyncMode): ContentSyncResult {
        val saved = store.current()
        if (mode == ContentSyncMode.OFFLINE_ONLY || fetcher == null)
            return ContentSyncResult(ContentSyncStatus.UNCHANGED, saved)
        val manifestBytes = try {
            val output = java.io.ByteArrayOutputStream()
            fetcher.fetch("/api/v1/device/content-manifest", 64 * 1024, output)
            output.toByteArray()
        }
            catch (error: ContentHttpFailure) {
                return ContentSyncResult(when (error.status) {
                    404 -> ContentSyncStatus.UNSUPPORTED
                    401, 403 -> ContentSyncStatus.REJECTED
                    else -> ContentSyncStatus.OFFLINE
                }, saved)
            } catch (_: SocketTimeoutException) {
                return ContentSyncResult(ContentSyncStatus.OFFLINE, saved)
            } catch (_: Exception) {
                return ContentSyncResult(ContentSyncStatus.OFFLINE, saved)
            }
        val manifest = ContentSyncContract.parseManifest(manifestBytes)
            ?: return ContentSyncResult(ContentSyncStatus.INCOMPATIBLE, saved)
        if (saved.version == manifest.version)
            return ContentSyncResult(ContentSyncStatus.UNCHANGED, saved)
        val downloaded = try { store.downloadDirectory() } catch (_: Exception) {
            return ContentSyncResult(ContentSyncStatus.OFFLINE, saved)
        }
        try {
            for (item in manifest.files) {
                val file = File(downloaded, item.path)
                val parent = file.parentFile ?: return ContentSyncResult(ContentSyncStatus.REJECTED, saved)
                if (parent != downloaded && !parent.isDirectory && !parent.mkdirs())
                    return ContentSyncResult(ContentSyncStatus.OFFLINE, saved)
                val count = FileOutputStream(file).use { output ->
                    val size = fetcher.fetch(item.downloadPath, item.size, output)
                    output.fd.sync()
                    size
                }
                if (count != item.size || file.length() != item.size ||
                    ContentSyncContract.sha256(file) != item.sha256)
                    return ContentSyncResult(ContentSyncStatus.REJECTED, saved)
            }
            if (ContentSyncContract.validContent(File(downloaded, "content.json").readBytes()) == null)
                return ContentSyncResult(ContentSyncStatus.REJECTED, saved)
            if (!store.activate(manifestBytes, downloaded))
                return ContentSyncResult(ContentSyncStatus.OFFLINE, store.current())
            return ContentSyncResult(ContentSyncStatus.UPDATED, store.current())
        } catch (error: ContentHttpFailure) {
            return ContentSyncResult(if (error.status in listOf(401, 403)) ContentSyncStatus.REJECTED
                else ContentSyncStatus.OFFLINE, saved)
        } catch (_: Exception) {
            return ContentSyncResult(ContentSyncStatus.OFFLINE, saved)
        } finally {
            downloaded.deleteRecursively()
        }
    }
}

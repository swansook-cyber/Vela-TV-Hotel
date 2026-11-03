package net.velalab.tvhotel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.nio.file.Files

class UniversalContentResolverTest {
    private lateinit var root: File
    private lateinit var bundled: ByteArray

    @Before fun setup() {
        root = Files.createTempDirectory("hotel-content-test").toFile()
        bundled = JSONObject().put("schema_version", 1)
            .put("hotel", JSONObject().put("name", "Ao Nang Fiore Resort"))
            .put("guest_welcome", JSONObject().put("mode", "generic").put("value", ""))
            .put("quick_apps", JSONArray(listOf("Netflix", "Disney+", "YouTube", "Prime Video", "iQIYI")))
            .put("resort", JSONObject().put("items", JSONArray()))
            .put("explore", JSONObject().put("items", JSONArray()))
            .put("guest", JSONObject().put("items", JSONArray())).toString().toByteArray()
    }

    @After fun cleanup() { root.deleteRecursively() }

    private fun payload(marker: String): ByteArray = JSONObject(String(bundled))
        .put("qa_marker", marker).toString().toByteArray()

    private fun menu(number: Int = 1) = JSONObject().put("menuVersion", "2026-11-04.1")
        .put("currency", "THB").put("contact", "4003")
        .put("categories", JSONArray().put(JSONObject().put("id", "appetizer")
            .put("title_en", "APPETIZER").put("items", JSONArray().put(JSONObject()
                .put("number", number).put("name_th", "อาหารไทย")
                .put("name_en", "Thai dish").put("price_thb", 120)))))

    @Test fun menuUpdatesThroughTheExistingAtomicContentPackage() {
        val store = ContentPackageStore(root, bundled)
        val updated = JSONObject(String(bundled)).put("room_service", menu()).toString().toByteArray()
        assertEquals(ContentSyncStatus.UPDATED, resolve(store, "1", updated).status)
        assertEquals("2026-11-04.1", store.current().content.getJSONObject("room_service").getString("menuVersion"))
        assertEquals(ContentSource.ACTIVE, ContentPackageStore(root, bundled).current().source)
    }

    @Test fun invalidMenuNeverReplacesLastKnownGood() {
        val store = ContentPackageStore(root, bundled)
        val valid = JSONObject(String(bundled)).put("room_service", menu()).toString().toByteArray()
        assertEquals(ContentSyncStatus.UPDATED, resolve(store, "1", valid).status)
        val bad = menu().put("categories", JSONArray().put(JSONObject().put("id", "appetizer")
            .put("title_en", "APPETIZER").put("items", JSONArray().put(JSONObject()
                .put("number", 1).put("name_th", "อาหารไทย").put("name_en", "Thai dish")
                .put("price_thb", 120)).put(JSONObject().put("number", 1)
                .put("name_th", "ซ้ำ").put("name_en", "Duplicate").put("price_thb", 140)))))
        val invalid = JSONObject(String(bundled)).put("room_service", bad).toString().toByteArray()
        assertEquals(ContentSyncStatus.REJECTED, resolve(store, "2", invalid).status)
        assertEquals("1", store.current().version)
    }

    private fun manifest(version: String, payload: ByteArray, schema: Int = 1,
                         minCode: Int = 15, digest: String = ContentSyncContract.sha256(payload)) =
        JSONObject().put("schemaVersion", schema).put("contentVersion", version)
            .put("publishedAt", "2026-11-03T10:00:00Z")
            .put("minAppVersionCode", minCode)
            .put("files", JSONArray().put(JSONObject().put("kind", "content")
                .put("path", "content.json")
                .put("downloadPath", "/api/v1/device/content/$version/$digest.json")
                .put("sha256", digest).put("size", payload.size)))
            .toString().toByteArray()

    private class FakeFetcher(private val manifest: ByteArray, private val payload: ByteArray,
                              private val fail: (String, OutputStream) -> Unit = { _, _ -> }) : ContentFetcher {
        var requests = 0
        override fun fetch(path: String, maxBytes: Long, output: OutputStream): Long {
            requests++
            fail(path, output)
            val bytes = if (path.endsWith("content-manifest")) manifest else payload
            output.write(bytes)
            return bytes.size.toLong()
        }
    }

    private fun resolve(store: ContentPackageStore, version: String, content: ByteArray,
                        manifestBytes: ByteArray = manifest(version, content)): ContentSyncResult =
        UniversalContentResolver(store, FakeFetcher(manifestBytes, content))
            .resolve(ContentSyncMode.AUTO_SYNC)

    @Test fun firstInstallOfflineUsesBundledBaseline() {
        val result = UniversalContentResolver(ContentPackageStore(root, bundled),
            FakeFetcher(byteArrayOf(), byteArrayOf())).resolve(ContentSyncMode.OFFLINE_ONLY)
        assertEquals(ContentSyncStatus.UNCHANGED, result.status)
        assertEquals(ContentSource.BUNDLED, result.snapshot.source)
    }

    @Test fun validManifestPassesCompatibilityAndDigestValidation() {
        val content = payload("new")
        val parsed = ContentSyncContract.parseManifest(manifest("1", content))
        assertNotNull(parsed)
        assertEquals(content.size.toLong(), parsed!!.files.single().size)
        assertEquals(ContentSyncContract.sha256(content), parsed.files.single().sha256)
    }

    @Test fun completeContentUpdateActivatesAtomically() {
        val store = ContentPackageStore(root, bundled)
        val result = resolve(store, "1", payload("new"))
        assertEquals(ContentSyncStatus.UPDATED, result.status)
        assertEquals(ContentSource.ACTIVE, store.current().source)
        assertEquals("new", store.current().content.getString("qa_marker"))
    }

    @Test fun contentAndMediaActivateAsOneVerifiedPackage() {
        val content = payload("with-media")
        val media = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 1, 2, 3)
        val mediaSha = ContentSyncContract.sha256(media)
        val descriptor = JSONObject().put("kind", "image").put("path", "media/lobby.png")
            .put("downloadPath", "/api/v1/media/$mediaSha.png")
            .put("sha256", mediaSha).put("size", media.size).put("durationSeconds", 5)
        val manifest = JSONObject(String(manifest("1", content)))
            .getJSONArray("files").put(descriptor)
        val manifestBytes = JSONObject(String(manifest("1", content)))
            .put("files", manifest).toString().toByteArray()
        val fetcher = object : ContentFetcher {
            override fun fetch(path: String, maxBytes: Long, output: OutputStream): Long {
                val bytes = when {
                    path.endsWith("content-manifest") -> manifestBytes
                    path.startsWith("/api/v1/media/") -> media
                    else -> content
                }
                output.write(bytes)
                return bytes.size.toLong()
            }
        }
        val store = ContentPackageStore(root, bundled)
        assertEquals(ContentSyncStatus.UPDATED,
            UniversalContentResolver(store, fetcher).resolve(ContentSyncMode.AUTO_SYNC).status)
        assertArrayEquals(media, store.mediaSlides().single().file.readBytes())
    }

    @Test fun invalidJsonKeepsBundledBaseline() {
        val store = ContentPackageStore(root, bundled)
        val result = resolve(store, "1", "{broken".toByteArray())
        assertEquals(ContentSyncStatus.REJECTED, result.status)
        assertEquals(ContentSource.BUNDLED, store.current().source)
    }

    @Test fun structurallyInvalidContentIsRejectedBeforeActivation() {
        val invalid = JSONObject(String(bundled))
            .getJSONObject("resort").getJSONArray("items")
            .put(JSONObject().put("title", "Incomplete").put("verified", true))
        val content = JSONObject(String(bundled))
            .put("resort", JSONObject().put("items", invalid)).toString().toByteArray()
        val store = ContentPackageStore(root, bundled)
        assertEquals(ContentSyncStatus.REJECTED, resolve(store, "1", content).status)
        assertEquals(ContentSource.BUNDLED, store.current().source)
    }

    @Test fun sha256MismatchKeepsBundledBaseline() {
        val content = payload("new")
        val store = ContentPackageStore(root, bundled)
        val result = resolve(store, "1", content, manifest("1", content, digest = "0".repeat(64)))
        assertEquals(ContentSyncStatus.REJECTED, result.status)
        assertEquals(ContentSource.BUNDLED, store.current().source)
    }

    @Test fun interruptedDownloadLeavesNoPartialActivation() {
        val content = payload("new")
        val store = ContentPackageStore(root, bundled)
        val fake = FakeFetcher(manifest("1", content), content) { path, output ->
            if (!path.endsWith("content-manifest")) {
                output.write(content.copyOfRange(0, 10))
                throw IOException("connection lost")
            }
        }
        assertEquals(ContentSyncStatus.OFFLINE,
            UniversalContentResolver(store, fake).resolve(ContentSyncMode.AUTO_SYNC).status)
        assertEquals(ContentSource.BUNDLED, store.current().source)
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith("download-") })
    }

    @Test fun serverTimeoutUsesLastKnownContent() {
        val store = ContentPackageStore(root, bundled)
        val fake = FakeFetcher(byteArrayOf(), byteArrayOf()) { _, _ -> throw SocketTimeoutException() }
        assertEquals(ContentSyncStatus.OFFLINE,
            UniversalContentResolver(store, fake).resolve(ContentSyncMode.AUTO_SYNC).status)
        assertEquals(ContentSource.BUNDLED, store.current().source)
    }

    @Test fun incompatibleSchemaAndMinimumAppCodeAreRejected() {
        val store = ContentPackageStore(root, bundled)
        val content = payload("new")
        for (bad in listOf(manifest("1", content, schema = 2),
                           manifest("1", content, minCode = 17))) {
            assertEquals(ContentSyncStatus.INCOMPATIBLE,
                resolve(store, "1", content, bad).status)
        }
        assertEquals(ContentSource.BUNDLED, store.current().source)
    }

    @Test fun failedPromotionRestoresPreviousActivePackage() {
        var promotions = 0
        val store = ContentPackageStore(root, bundled, promote = { from, to ->
            promotions++
            if (promotions == 2) false else from.renameTo(to)
        })
        assertEquals(ContentSyncStatus.UPDATED, resolve(store, "1", payload("first")).status)
        assertEquals(ContentSyncStatus.OFFLINE, resolve(store, "2", payload("second")).status)
        assertEquals("first", store.current().content.getString("qa_marker"))
        assertEquals(ContentSource.ACTIVE, store.current().source)
    }

    @Test fun offlineOnlyNeverCallsFetcher() {
        val fake = FakeFetcher(byteArrayOf(), byteArrayOf())
        UniversalContentResolver(ContentPackageStore(root, bundled), fake)
            .resolve(ContentSyncMode.OFFLINE_ONLY)
        assertEquals(0, fake.requests)
    }

    @Test fun newProcessReadsLastKnownGoodPackage() {
        assertEquals(ContentSyncStatus.UPDATED,
            resolve(ContentPackageStore(root, bundled), "1", payload("persisted")).status)
        val reloaded = ContentPackageStore(root, bundled).current()
        assertEquals(ContentSource.ACTIVE, reloaded.source)
        assertEquals("persisted", reloaded.content.getString("qa_marker"))
    }

    @Test fun acceptedV106BundledSeedRemainsValid() {
        val candidates = listOf(File("src/main/assets/hotel_content.json"),
            File("android-client/src/main/assets/hotel_content.json"))
        val seed = candidates.first { it.isFile }.readBytes()
        val snapshot = ContentPackageStore(root, seed).current()
        assertEquals(ContentSource.BUNDLED, snapshot.source)
        assertNotNull(snapshot.content.optJSONObject("approved_content"))
        assertEquals("Ao Nang Fiore Resort", snapshot.content.getJSONObject("hotel").getString("name"))
    }

    @Test fun corruptActivePackageFallsBackToVerifiedPrevious() {
        val store = ContentPackageStore(root, bundled)
        assertEquals(ContentSyncStatus.UPDATED, resolve(store, "1", payload("first")).status)
        assertEquals(ContentSyncStatus.UPDATED, resolve(store, "2", payload("second")).status)
        File(root, "active/content.json").writeText("corrupt")
        assertEquals("first", ContentPackageStore(root, bundled).current().content.getString("qa_marker"))
        assertEquals(ContentSource.PREVIOUS, store.current().source)
    }
}

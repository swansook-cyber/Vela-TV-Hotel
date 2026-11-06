package net.velalab.tvhotel

import android.content.res.AssetManager
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** Bundled promotional images are keyed by approved menu number, never category. */
class RoomServiceItemArtwork(private val assets: AssetManager) {
    private data class Entry(val asset: String, val bytes: Int, val sha256: String)

    private val entries: Map<String, Entry> by lazy {
        runCatching {
            val manifest = assets.open("room_service_items/index.json").use {
                JSONObject(String(it.readBytes(), Charsets.UTF_8))
            }
            val images = manifest.getJSONArray("images")
            buildMap {
                for (index in 0 until images.length()) {
                    val image = images.getJSONObject(index)
                    val number = image.getInt("number")
                    val key = itemKey(number)
                    val asset = image.getString("asset")
                    if (asset == "$key.webp" && number !in setOf(37, 38)) {
                        put(key, Entry(asset, image.getInt("bytes"), image.getString("sha256")))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun verifiedImage(key: String): ByteArray? {
        val entry = entries[key] ?: return null
        val data = runCatching {
            assets.open("room_service_items/${entry.asset}").use { it.readBytes() }
        }.getOrNull() ?: return null
        if (data.size != entry.bytes || data.size < 12 || !data.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) ||
            !data.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) return null
        val hash = MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return data.takeIf { hash.equals(entry.sha256, ignoreCase = true) }
    }

    companion object {
        fun itemKey(number: Int): String = String.format(Locale.US, "item_%03d", number)
    }
}

package net.velalab.tvhotel

/** Data-only model. Network transport and cache activation are not yet implemented. */
data class HotelBranding(val name: String)

enum class MediaKind { IMAGE, VIDEO }

data class PlaylistItem(
    val id: String,
    val kind: MediaKind,
    val url: String,
    val sha256: String,
    val durationSeconds: Int
) {
    fun isValid(): Boolean =
        id.isNotBlank() &&
        url.startsWith("https://") &&
        sha256.matches(Regex("[0-9a-f]{64}")) &&
        durationSeconds in 1..120
}

data class HotelPlaylist(
    val schemaVersion: Int,
    val revision: String,
    val hotel: HotelBranding,
    val media: List<PlaylistItem>
) {
    fun isValid(): Boolean =
        schemaVersion == 1 && revision.isNotBlank() &&
        hotel.name.isNotBlank() && media.size <= 30 &&
        media.all { it.isValid() } &&
        media.map { it.id }.distinct().size == media.size
}

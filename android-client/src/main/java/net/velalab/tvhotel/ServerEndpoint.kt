package net.velalab.tvhotel

import java.net.URI

/**
 * Pure configuration model; storage, admin authentication and migration UI are future work.
 * Do not use this as an SSRF defense on its own: network client must enforce DNS/IP and redirect policy.
 */
data class ServerEndpoint(val baseUrl: String) {
    fun normalizedHttpsUrl(): String? = try {
        val uri = URI(baseUrl.trim())
        if (uri.scheme?.lowercase() != "https" || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.query != null || uri.fragment != null ||
            uri.port !in listOf(-1, 443) || uri.path !in listOf("", "/")) null
        else "https://" + uri.host.lowercase()
    } catch (_: Exception) {
        null
    }
}

data class ServerMigrationPlan(
    val current: ServerEndpoint?,
    val candidate: ServerEndpoint,
    val verifiedManifestRevision: String? = null
) {
    val readyToActivate: Boolean
        get() = candidate.normalizedHttpsUrl() != null &&
            !verifiedManifestRevision.isNullOrBlank()
}

package net.velalab.tvhotel

import android.content.Context

/** Device-local staff preference. Auto Sync retains the V1.0.6 default behavior. */
class ContentSyncSettings(context: Context) {
    private val preferences = context.getSharedPreferences("hotel_content_sync", Context.MODE_PRIVATE)

    fun mode(): ContentSyncMode = if (preferences.getString("mode", "auto") == "offline")
        ContentSyncMode.OFFLINE_ONLY else ContentSyncMode.AUTO_SYNC

    fun setMode(mode: ContentSyncMode): Boolean =
        preferences.edit().putString("mode", if (mode == ContentSyncMode.OFFLINE_ONLY) "offline" else "auto")
            .commit()
}

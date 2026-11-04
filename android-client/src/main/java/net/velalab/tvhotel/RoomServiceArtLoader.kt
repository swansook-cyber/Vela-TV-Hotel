package net.velalab.tvhotel

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.LruCache
import android.widget.ImageView
import android.widget.ScrollView
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** Decode only visible menu artwork off the UI thread; keep a small sampled cache. */
class RoomServiceArtLoader(private val activity: Activity) {
    private val executor = Executors.newSingleThreadExecutor()
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val waiting = mutableMapOf<String, MutableList<WeakReference<ImageView>>>()
    private val pending = mutableSetOf<String>()

    fun loadWhenVisible(scroll: ScrollView, images: List<Pair<ImageView, String>>) {
        val check = {
            val visible = Rect()
            for ((image, key) in images) {
                if (image.getGlobalVisibleRect(visible) && visible.width() > 0 && visible.height() > 0)
                    request(image, key)
            }
        }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> check() }
        scroll.post { check() }
    }

    private fun request(image: ImageView, key: String) {
        image.tag = key
        cache.get(key)?.let { image.setImageBitmap(it); return }
        val listeners = waiting.getOrPut(key) { mutableListOf() }
        if (listeners.none { it.get() === image }) listeners.add(WeakReference(image))
        if (!pending.add(key)) return
        executor.execute {
            val bitmap = runCatching {
                val path = RoomServicePresentation.artPath(key) ?: return@runCatching null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = if (key == "hero") 1 else 2
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                activity.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
            activity.runOnUiThread {
                pending.remove(key)
                if (bitmap != null) cache.put(key, bitmap)
                waiting.remove(key)?.forEach { ref ->
                    ref.get()?.takeIf { it.isAttachedToWindow && it.tag == key }
                        ?.setImageBitmap(bitmap)
                }
            }
        }
    }

    fun close() { executor.shutdownNow(); cache.evictAll() }
}

package net.velalab.tvhotel

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.VideoView
import java.io.File

/**
 * Plays only already-downloaded local media files. Does not stream arbitrary remote URLs.
 * Caller must supply files from a verified, atomically activated playlist.
 */
class MediaSlideshow(private val activity: Activity, private val host: FrameLayout) {
    data class Slide(val file: File, val isVideo: Boolean, val durationMs: Long)
    private val handler = Handler(Looper.getMainLooper())
    private var slides: List<Slide> = emptyList()
    private var index = 0
    private var running = false
    private var player: VideoView? = null
    private val next = Runnable { showNext() }

    fun start(items: List<Slide>) {
        stop()
        slides = items.filter { it.file.isFile && it.durationMs in 1000..120000 }
        if (slides.isEmpty()) return
        running = true
        index = 0
        showNext()
    }

    private fun showNext() {
        if (!running || slides.isEmpty()) return
        player?.stopPlayback()
        player = null
        host.removeAllViews()
        val slide = slides[index++ % slides.size]
        if (slide.isVideo) {
            val video = VideoView(activity)
            player = video
            host.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            video.setOnPreparedListener { mp ->
                mp.setVolume(0f, 0f)
                mp.isLooping = false
                if (running) video.start()
            }
            video.setOnErrorListener { _, _, _ ->
                handler.removeCallbacks(next)
                if (running) handler.post(next)
                true
            }
            video.setVideoPath(slide.file.absolutePath)
        } else {
            val bitmap = BitmapFactory.decodeFile(slide.file.absolutePath)
            if (bitmap != null) {
                val image = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                }
                host.addView(image, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
        }
        handler.removeCallbacks(next)
        handler.postDelayed(next, slide.durationMs)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(next)
        player?.stopPlayback()
        player = null
        host.removeAllViews()
    }
}

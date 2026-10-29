package net.velalab.tvhotel

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import java.io.File
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * Offline guest welcome shell. Not a HOME replacement until real-TV QA.
 * Shows the original hotel logo from a bundled, byte-for-byte source PNG asset.
 */
class MainActivity : Activity() {
    private var slideshow: MediaSlideshow? = null
    private var slideshowHost: FrameLayout? = null
    private val appButtons = mutableListOf<Button>()
    private var selectedIndex = 0

    /** Explicit horizontal navigation for TV remotes; preserve system Home and Back. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && appButtons.isNotEmpty()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    focusApp((selectedIndex - 1).coerceAtLeast(0))
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    focusApp((selectedIndex + 1).coerceAtMost(appButtons.lastIndex))
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    val focused = (currentFocus as? Button)?.takeIf { it in appButtons }
                        ?: appButtons[selectedIndex]
                    focused.performClick()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun focusApp(index: Int) {
        if (index !in appButtons.indices) return
        selectedIndex = index
        appButtons[index].requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && appButtons.isNotEmpty() && appButtons.none { it.hasFocus() }) {
            appButtons[selectedIndex].post { focusApp(selectedIndex) }
        }
    }

    override fun onStart() {
        super.onStart()
        val slides = VerifiedMediaCache(this).activeSlides()
        if (slides.isNotEmpty()) slideshow?.start(slides)
    }

    override fun onStop() {
        slideshow?.stop()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(56, 48, 56, 48)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(9, 38, 31), Color.rgb(3, 12, 20))
            )
        }
        fun line(value: String, size: Float, bold: Boolean = false): TextView =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                if (bold) setTypeface(null, Typeface.BOLD)
            }
        // Preserve the source artwork aspect ratio. The ImageView never center-crops.
        val metrics = resources.displayMetrics
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.aonang_fiore_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Ao Nang Fiore Resort"
        }
        root.addView(logo, LinearLayout.LayoutParams(
            (metrics.widthPixels * 0.78f).toInt(),
            (metrics.heightPixels * 0.38f).toInt()
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = (12f * metrics.density).toInt()
        })
        root.addView(line("Welcome to Ao Nang Fiore Resort", 23f))
        val hint = line("Hotel TV Pilot  •  Offline welcome screen", 15f)
        hint.setTextColor(Color.LTGRAY)
        root.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 30 })

        val launchable = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString() }
            .take(6)

        val apps = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        appButtons.clear()
        launchable.forEach { app ->
            val target = app.activityInfo
            val button = Button(this).apply {
                text = app.loadLabel(packageManager).toString()
                isFocusable = true
                isFocusableInTouchMode = true
                setTextColor(Color.WHITE)
                backgroundTintList = null
                background = GradientDrawable().apply {
                    cornerRadius = 12f * resources.displayMetrics.density
                    setColor(Color.rgb(53, 69, 90))
                    setStroke((2f * resources.displayMetrics.density).toInt(), Color.LTGRAY)
                }
                setOnFocusChangeListener { view, hasFocus ->
                    (view.background as? GradientDrawable)?.apply {
                        setColor(if (hasFocus) Color.rgb(23, 112, 118) else Color.rgb(53, 69, 90))
                        setStroke(
                            ((if (hasFocus) 4f else 2f) * resources.displayMetrics.density).toInt(),
                            if (hasFocus) Color.WHITE else Color.LTGRAY
                        )
                    }
                }
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                            .setClassName(target.packageName, target.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(
                            this@MainActivity, "Unable to open app", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            appButtons.add(button)
            apps.addView(button)
        }
        root.addView(apps, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 36 })
        val container = FrameLayout(this)
        slideshowHost = FrameLayout(this)
        container.addView(slideshowHost, FrameLayout.LayoutParams(-1, -1))
        val overlay = View(this).apply { setBackgroundColor(Color.argb(170, 0, 15, 12)) }
        container.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        container.addView(root, FrameLayout.LayoutParams(-1, -1))
        slideshow = MediaSlideshow(this, slideshowHost!!)
        setContentView(container)
        if (appButtons.isNotEmpty()) {
            apps.post { focusApp(0) }
        }
    }
}

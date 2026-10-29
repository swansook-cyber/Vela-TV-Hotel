package net.velalab.tvhotel

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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

    /** Leave D-pad/OK/Back to Android native focus dispatch. */
    private fun styleButton(button: Button, selected: Boolean) {
        button.backgroundTintList = null
        button.background = GradientDrawable().apply {
            cornerRadius = 14f * resources.displayMetrics.density
            setColor(if (selected) Color.rgb(235, 170, 28) else Color.rgb(45, 58, 77))
            setStroke(
                (if (selected) 5f else 2f).times(resources.displayMetrics.density).toInt(),
                if (selected) Color.WHITE else Color.GRAY
            )
        }
        button.setTextColor(if (selected) Color.BLACK else Color.WHITE)
        button.scaleX = if (selected) 1.08f else 1f
        button.scaleY = if (selected) 1.08f else 1f
        button.invalidate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && appButtons.isNotEmpty() && appButtons.none { it.hasFocus() }) {
            appButtons[selectedIndex].post { appButtons[selectedIndex].requestFocus() }
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
            setPadding(40, 24, 40, 24)
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
            (metrics.widthPixels * 0.68f).toInt(),
            (metrics.heightPixels * 0.33f).toInt()
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = (12f * metrics.density).toInt()
        })
        root.addView(line("Welcome to Ao Nang Fiore Resort", 25f, true))
        val hint = line("Discover Ao Nang  ·  Relax and enjoy your stay", 16f)
        hint.setTextColor(Color.LTGRAY)
        root.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 10 })

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
                textSize = 13f
                isAllCaps = false
                gravity = Gravity.CENTER
                val appIcon = app.loadIcon(packageManager)
                val iconSize = (42f * resources.displayMetrics.density).toInt()
                appIcon.setBounds(0, 0, iconSize, iconSize)
                setCompoundDrawables(null, appIcon, null, null)
                compoundDrawablePadding = (10f * resources.displayMetrics.density).toInt()
                isFocusable = true
                isFocusableInTouchMode = true
                id = View.generateViewId()
                setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) selectedIndex = appButtons.indexOf(view).coerceAtLeast(0)
                    styleButton(this, hasFocus)
                }
                styleButton(this, false)
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
            apps.addView(button, LinearLayout.LayoutParams(
                (136f * resources.displayMetrics.density).toInt(),
                (112f * resources.displayMetrics.density).toInt()
            ).apply {
                marginStart = (6f * resources.displayMetrics.density).toInt()
                marginEnd = (6f * resources.displayMetrics.density).toInt()
            })
        }
        root.addView(apps, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 28 })
        root.addView(line("Use your remote to select an app", 13f).apply {
            setTextColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18 })
        val container = FrameLayout(this)
        slideshowHost = FrameLayout(this)
        container.addView(slideshowHost, FrameLayout.LayoutParams(-1, -1))
        val overlay = View(this).apply { setBackgroundColor(Color.argb(170, 0, 15, 12)) }
        container.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        container.addView(root, FrameLayout.LayoutParams(-1, -1))
        slideshow = MediaSlideshow(this, slideshowHost!!)
        setContentView(container)
        if (appButtons.isNotEmpty()) {
            apps.post { appButtons[0].requestFocus() }
        }
    }
}

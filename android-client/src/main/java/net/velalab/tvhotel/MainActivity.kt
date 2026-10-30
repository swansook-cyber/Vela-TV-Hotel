package net.velalab.tvhotel

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hotel-first offline guest guide. Remote uses native Android focus/OK navigation.
 * Guest copy is bundled in assets/hotel_content.json until an authenticated CMS is ready.
 */
class MainActivity : Activity() {
    private val navy = Color.rgb(8, 35, 36)
    private val gold = Color.rgb(235, 170, 28)
    private val cards = Color.rgb(30, 73, 74)
    private val sea = Color.rgb(22, 112, 115)
    private val muted = Color.rgb(178, 202, 205)
    private var screen: LinearLayout? = null
    private var activeScroll: ScrollView? = null
    private fun viewportHeightDp(): Int = (resources.displayMetrics.heightPixels / resources.displayMetrics.density).toInt()
    private fun compactTv(): Boolean = viewportHeightDp() < 650
    private fun fitHeight(normal: Int, compact: Int): Int = if (compactTv()) compact else normal
    private var lastHomeIndex = 0
    private var lastSectionIndex = 0
    private var lastQuickSlot = 0
    private var page = "home"
    private var content = JSONObject()
    private var syncStatus = "Offline guide"
    private var assignedRoom = ""
    private var slideshow: MediaSlideshow? = null
    private val credentials by lazy { DeviceCredentials(this) }
    private val internalPhotoSeedAvailable: Boolean by lazy {
        runCatching {
            val names = assets.list("fiore_internal")?.toSet().orEmpty()
            names.contains("hero.webp") && names.contains("resort_grid.webp")
        }.getOrDefault(false)
    }
    private fun internalPhoto(name: String): Bitmap? = runCatching {
        assets.open("fiore_internal/$name").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()
    private fun label(text: String, size: Float, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun style(button: Button, focused: Boolean) {
        button.backgroundTintList = null
        button.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (focused) Color.rgb(35, 83, 87) else cards)
            setStroke(dp(if (focused) 3 else 1), if (focused) Color.WHITE else Color.rgb(82, 126, 129))
        }
        button.setTextColor(Color.WHITE)
        button.scaleX = if (focused) 1.035f else 1f
        button.scaleY = if (focused) 1.035f else 1f
        button.invalidate()
    }

    private fun action(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 16f
            isAllCaps = false
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            setOnFocusChangeListener { _, focused -> style(this, focused) }
            style(this, false)
            setOnClickListener { onClick() }
        }

    private fun newPage(heroBackdrop: Boolean = false): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val safeInset = maxOf(48, (resources.displayMetrics.widthPixels /
                resources.displayMetrics.density / 20f).toInt())
            setPadding(dp(safeInset), dp(28), dp(safeInset), dp(30))
            clipChildren = false
            background = if (heroBackdrop) null else GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(9, 48, 50), Color.rgb(5, 18, 31))
            )
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            isSmoothScrollingEnabled = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(root)
        }
        if (heroBackdrop) {
            val frame = FrameLayout(this)
            frame.addView(ImageView(this).apply {
                val photo = if (internalPhotoSeedAvailable) internalPhoto("hero.webp") else null
                if (photo != null) setImageBitmap(photo)
                else setImageResource(R.drawable.fiore_concept_hero)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = if (photo != null) "Ao Nang Fiore Resort website photo, internal QA only"
                    else "Illustrative tropical resort backdrop; not a hotel photograph"
            }, FrameLayout.LayoutParams(-1, -1))
            frame.addView(View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.argb(235, 4, 20, 25), Color.argb(190, 4, 20, 25),
                        Color.argb(145, 4, 20, 25)))
            }, FrameLayout.LayoutParams(-1, -1))
            frame.addView(View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.argb(55, 5, 18, 27), Color.argb(235, 5, 18, 27)))
            }, FrameLayout.LayoutParams(-1, -1))
            frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
            setContentView(frame)
        } else setContentView(scroll)
        screen = root
        activeScroll = scroll
        return root
    }

    private fun addLogo(root: LinearLayout, home: Boolean) {
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.aonang_fiore_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ao Nang Fiore Resort"
        }
        root.addView(logo, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(if (home) 100 else fitHeight(62, 42))
        ))
    }

    private fun textLine(text: String, size: Float, bold: Boolean = false): TextView =
        label(text, size, bold).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }

    private fun panel(focused: Boolean, accent: Int = sea): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(if (focused) Color.rgb(35, 83, 87) else accent, Color.rgb(15, 42, 55)))
            .apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(if (focused) 3 else 1),
                    if (focused) Color.WHITE else Color.rgb(79, 125, 128))
            }

    private fun focusCard(view: View, accent: Int = sea) {
        view.background = panel(false, accent)
        view.setOnFocusChangeListener { _, focused ->
            view.background = panel(focused, accent)
            view.animate().scaleX(if (focused) 1.035f else 1f)
                .scaleY(if (focused) 1.035f else 1f).setDuration(110).start()
            view.elevation = dp(if (focused) 12 else 0).toFloat()
        }
    }

    private fun header(root: LinearLayout, title: String, onBack: () -> Unit): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = action("‹  Welcome", onBack).apply { textSize = 14f }
        bar.addView(back, LinearLayout.LayoutParams(dp(150), dp(46)))
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.aonang_fiore_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ao Nang Fiore Resort"
        }
        bar.addView(logo, LinearLayout.LayoutParams(0, dp(54), 1f))
        bar.addView(textLine(title, 24f, true), LinearLayout.LayoutParams(0, dp(54), 2f))
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(22) })
        return back
    }

    private fun sectionCard(title: String, subtitle: String, icon: Int, onClick: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(14), dp(12))
            isFocusable = true
            contentDescription = "$title. $subtitle"
            setOnClickListener { onClick() }
        }
        val glyph = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(Color.rgb(232, 198, 129))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        card.addView(glyph, LinearLayout.LayoutParams(dp(32), dp(32)).apply { bottomMargin = dp(12) })
        card.addView(textLine(title, 18f, true), LinearLayout.LayoutParams(-1, dp(29)))
        card.addView(textLine(subtitle, 12f).apply { setTextColor(muted); maxLines = 2 },
            LinearLayout.LayoutParams(-1, dp(30)))
        focusCard(card)
        return card
    }

    private fun section(id: String): JSONObject = content.optJSONObject(id) ?: JSONObject()
    private fun entries(id: String) = section(id).optJSONArray("items")

    private val defaultQuickApps = listOf(
        "com.netflix.ninja",
        "com.disney.disneyplus",
        "com.google.android.youtube.tv",
        "com.amazon.amazonvideo.livingroom",
        "com.iqiyi.i18n.tv"
    )
    private val defaultAppNames = listOf("Netflix", "Disney+", "YouTube", "Prime Video", "iQIYI")
    private val quickAppPrefs by lazy { getSharedPreferences("quick_apps_v1", MODE_PRIVATE) }

    private fun installedTvApps(): List<android.content.pm.ResolveInfo> =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(java.util.Locale.ROOT) }

    private fun quickAppPackage(slot: Int): String =
        quickAppPrefs.getString("slot_$slot", defaultQuickApps[slot]) ?: ""

    private fun launchApp(info: android.content.pm.ResolveInfo) {
        try {
            val target = info.activityInfo
            startActivity(Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setClassName(target.packageName, target.name))
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to open this app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shortcutStyle(view: LinearLayout, focused: Boolean, present: Boolean) {
        view.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (focused) intArrayOf(Color.rgb(39, 62, 66), Color.rgb(17, 33, 43))
            else intArrayOf(Color.rgb(23, 48, 54), Color.rgb(10, 29, 39))
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(if (focused) 3 else 1),
                if (focused) Color.rgb(255, 239, 206) else Color.rgb(98, 128, 131))
        }
        view.alpha = if (present || focused) 1f else 0.7f
        view.animate().scaleX(if (focused) 1.035f else 1f)
            .scaleY(if (focused) 1.035f else 1f).setDuration(110).start()
        view.elevation = dp(if (focused) 12 else 0).toFloat()
    }

    /** Prefer the TV application's wide banner, just like Vela TV Launcher.
     *  Some apps supply only a square icon, so retain a generous-sized icon fallback.
     */
    private fun quickAppArtwork(info: android.content.pm.ResolveInfo): Pair<android.graphics.drawable.Drawable, Boolean> {
        val banner = try {
            info.activityInfo.loadBanner(packageManager)
                ?: info.activityInfo.applicationInfo.loadBanner(packageManager)
        } catch (_: Exception) { null }
        val wide = banner != null && banner.intrinsicWidth > 0 && banner.intrinsicHeight > 0 &&
            banner.intrinsicWidth.toFloat() / banner.intrinsicHeight >= 1.4f
        return if (wide) Pair(banner!!, true)
        else Pair(info.loadIcon(packageManager), false)
    }

    /** Five editable local launcher-style shortcuts; real banner artwork where provided by installed apps. */
    private fun quickApps(root: LinearLayout) {
        val available = installedTvApps()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        repeat(5) { slot ->
            val pkg = quickAppPackage(slot)
            val found = available.firstOrNull { it.activityInfo.packageName == pkg }
            val title = found?.loadLabel(packageManager)?.toString()
                ?: if (pkg == defaultQuickApps[slot]) defaultAppNames[slot]
                   else if (pkg.isBlank()) "Empty slot" else "App unavailable"
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(7), dp(10), dp(5))
                isFocusable = true
                isFocusableInTouchMode = true
                clipChildren = false
                contentDescription = "$title, shortcut ${slot + 1}"
            }
            val art = found?.let { quickAppArtwork(it) }
            val image = ImageView(this).apply {
                if (art != null) setImageDrawable(art.first)
                else setImageResource(android.R.drawable.sym_def_app_icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = false
                alpha = if (found == null) 0.38f else 1f
                contentDescription = title
            }
            tile.addView(image, LinearLayout.LayoutParams(-1, dp(fitHeight(88, 65))))
            // Banner artwork carries its own app identity; keep a short label below.
            tile.addView(label(title, 12f, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, dp(21)))
            if (found == null) {
                tile.addView(label("Not installed", 10f), LinearLayout.LayoutParams(-1, dp(16)))
            }
            tile.setOnFocusChangeListener { _, focused ->
                shortcutStyle(tile, focused, found != null)
            }
            shortcutStyle(tile, false, found != null)
            tile.setOnClickListener {
                if (found != null) launchApp(found) else showQuickAppsEditor(slot)
            }
            tile.setOnLongClickListener { showQuickAppsEditor(slot); true }
            row.addView(tile, LinearLayout.LayoutParams(0, dp(fitHeight(139, 112)), 1f).apply {
                setMargins(dp(6), dp(6), dp(6), dp(6))
            })
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showHome() {
        slideshow?.stop()
        slideshow = null
        page = "home"
        val root = newPage(heroBackdrop = true)
        val hero = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.aonang_fiore_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ao Nang Fiore Resort"
        }
        hero.addView(logo, LinearLayout.LayoutParams(dp(300), dp(fitHeight(105, 84))))
        val greeting = content.optJSONObject("guest_welcome")
        val value = greeting?.optString("value", "")?.trim()?.take(50) ?: ""
        val welcome = when (greeting?.optString("mode", "generic")) {
            "hidden" -> ""
            "room" -> if (value.isNotBlank()) "Welcome, Room $value" else "Welcome, Guest"
            "name" -> if (value.isNotBlank()) "Welcome, $value" else "Welcome, Guest"
            else -> if (assignedRoom.isNotBlank()) "Welcome, Room $assignedRoom" else "Welcome, Guest"
        }
        val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(30), 0, 0, 0) }
        words.addView(textLine("WELCOME TO", 13f, true).apply { setTextColor(muted); letterSpacing = 0.16f })
        words.addView(textLine("Ao Nang Fiore Resort", 29f, true))
        if (welcome.isNotBlank()) words.addView(textLine(welcome, 16f).apply { setTextColor(muted) })
        hero.addView(words, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(hero, LinearLayout.LayoutParams(-1, dp(fitHeight(154, 108))).apply { bottomMargin = dp(16) })
        root.addView(textLine("DISCOVER YOUR STAY", 13f, true).apply { setTextColor(muted); letterSpacing = 0.13f },
            LinearLayout.LayoutParams(-1, dp(25)))
        val sections = listOf(
            Triple("entertainment", "Entertainment", "Streaming & TV"),
            Triple("resort", "Our Resort", "Rooms, dining & wellbeing"),
            Triple("explore", "Explore Krabi", "Discover the area"),
            Triple("guest", "Guest Information", "Your stay & services")
        )
        val icons = listOf(android.R.drawable.ic_media_play, android.R.drawable.ic_menu_myplaces,
            android.R.drawable.ic_menu_compass, android.R.drawable.ic_menu_info_details)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val cards = mutableListOf<View>()
        sections.forEachIndexed { index, (id, title, subtitle) ->
            val card = sectionCard(title, subtitle, icons[index]) { lastHomeIndex = index; showSection(id, title) }
            cards.add(card)
            row.addView(card, LinearLayout.LayoutParams(0, dp(fitHeight(165, 140)), 1f).apply {
                setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        val quickHeading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        quickHeading.addView(textLine("QUICK APPS", 13f, true).apply { setTextColor(muted); letterSpacing = 0.13f },
            LinearLayout.LayoutParams(0, dp(27), 1f))
        quickHeading.addView(textLine("Hold OK to change an app", 12f).apply {
            setTextColor(muted); gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(27), 1f))
        root.addView(quickHeading)
        quickApps(root)
        val imageNotice = if (internalPhotoSeedAvailable) "OFFICIAL WEBSITE PHOTOS · INTERNAL QA ONLY"
            else "ILLUSTRATIVE FALLBACK · NO HOTEL PHOTOS"
        root.addView(textLine("$syncStatus   ·   $imageNotice", 11f).apply {
            setTextColor(Color.rgb(192, 206, 198))
        }, LinearLayout.LayoutParams(-1, dp(28)).apply { topMargin = dp(5) })
        cards[3].setOnLongClickListener { showStaffTools(); true }
        cards.getOrNull(lastHomeIndex.coerceIn(0, 3))?.post { cards[lastHomeIndex.coerceIn(0, 3)].requestFocus() }
    }

    private fun showSection(id: String, title: String) {
        slideshow?.stop()
        slideshow = null
        page = id
        val root = newPage()
        val back = header(root, title) { showHome() }
        if (id == "entertainment") {
            root.addView(textLine("AVAILABLE ON THIS TV", 13f, true).apply { setTextColor(muted) })
            addAppCards(root)
            back.post { back.requestFocus() }
            return
        }
        val items = entries(id)
        if (items == null || items.length() == 0) {
            emptyState(root, "No information has been published yet.",
                "Resort content will appear after it is reviewed and published.")
            back.post { back.requestFocus() }
            return
        }
        if (id == "resort") {
            showResortContent(root, items)
            return
        }
        root.addView(textLine("EXPLORE", 13f, true).apply { setTextColor(muted) },
            LinearLayout.LayoutParams(-1, dp(34)))
        val cards = mutableListOf<View>()
        for (start in 0 until items.length() step 3) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            for (i in start until minOf(start + 3, items.length())) {
                val item = items.optJSONObject(i) ?: continue
                val name = item.optString("title", "Information")
                val details = if (item.optBoolean("verified", false)) item.optString("description", "")
                    else "Information pending • " + item.optString("description", "TEST DATA")
                val icon = when (id) {
                    "resort" -> android.R.drawable.ic_menu_myplaces
                    "explore" -> android.R.drawable.ic_menu_compass
                    else -> android.R.drawable.ic_menu_info_details
                }
                val card = sectionCard(name, details.take(85), icon) {
                    lastSectionIndex = i
                    showDetail(id, title, name, details)
                }
                cards.add(card)
                row.addView(card, LinearLayout.LayoutParams(0, dp(fitHeight(165, 142)), 1f).apply {
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                })
            }
            repeat(3 - row.childCount) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            root.addView(row)
        }
        cards.getOrNull(lastSectionIndex.coerceIn(0, cards.lastIndex))?.post {
            cards[lastSectionIndex.coerceIn(0, cards.lastIndex)].requestFocus()
        }
    }

    private val resortSeedPhotos: List<Bitmap> by lazy {
        val sheet = if (internalPhotoSeedAvailable) internalPhoto("resort_grid.webp")
            ?: BitmapFactory.decodeResource(resources, R.drawable.fiore_concept_resort_grid)
            else BitmapFactory.decodeResource(resources, R.drawable.fiore_concept_resort_grid)
        val halfWidth = sheet.width / 2
        val halfHeight = sheet.height / 2
        listOf(
            Bitmap.createBitmap(sheet, 0, 0, halfWidth, halfHeight),
            Bitmap.createBitmap(sheet, halfWidth, 0, halfWidth, halfHeight),
            Bitmap.createBitmap(sheet, 0, halfHeight, halfWidth, halfHeight),
            Bitmap.createBitmap(sheet, halfWidth, halfHeight, halfWidth, halfHeight)
        )
    }

    private fun resortPhotoCard(title: String, caption: String, photoIndex: Int,
                                onClick: () -> Unit): View {
        val frame = FrameLayout(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            contentDescription = "$title. ${if (internalPhotoSeedAvailable) "Ao Nang Fiore Resort website photo" else "Illustrative image"}. $caption"
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 49, 51))
                cornerRadius = dp(17).toFloat()
            }
            clipToOutline = true
            setOnClickListener { onClick() }
        }
        frame.addView(ImageView(this).apply {
            setImageBitmap(resortSeedPhotos[photoIndex])
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(225, 4, 22, 27)))
        }, FrameLayout.LayoutParams(-1, -1))
        frame.addView(textLine(if (internalPhotoSeedAvailable) "OFFICIAL SITE · QA ONLY" else "ILLUSTRATIVE IMAGE", 10f, true).apply {
            setPadding(dp(13), 0, 0, 0)
            letterSpacing = 0.11f
        }, FrameLayout.LayoutParams(-1, dp(28), Gravity.TOP))
        val captionBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), 0, dp(12), dp(12))
        }
        captionBox.addView(textLine(title, if (compactTv()) 16f else 19f, true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        captionBox.addView(textLine(caption, 12f).apply {
            setTextColor(Color.rgb(218, 226, 218))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        frame.addView(captionBox, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        fun outline(focused: Boolean) = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(17).toFloat()
            setStroke(dp(if (focused) 3 else 1),
                if (focused) Color.rgb(255, 239, 206) else Color.rgb(138, 161, 151))
        }
        frame.foreground = outline(false)
        frame.setOnFocusChangeListener { _, focused ->
            frame.foreground = outline(focused)
            frame.animate().scaleX(if (focused) 1.035f else 1f)
                .scaleY(if (focused) 1.035f else 1f).setDuration(110).start()
            frame.elevation = dp(if (focused) 12 else 0).toFloat()
        }
        return frame
    }

    private fun showResortContent(root: LinearLayout, items: org.json.JSONArray) {
        val aboutIndex = (0 until items.length()).firstOrNull {
            items.optJSONObject(it)?.optString("category")?.equals("Profile", true) == true
        }
        var aboutView: View? = null
        if (aboutIndex != null) {
            val about = items.optJSONObject(aboutIndex)
            val description = about?.optString("description", "TEST DATA — Resort information pending.")
                ?: "TEST DATA — Resort information pending."
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = panel(false, Color.rgb(24, 60, 58))
                setPadding(dp(24), dp(15), dp(24), dp(15))
                isFocusable = true
                contentDescription = "About the Resort. $description"
                setOnClickListener {
                    lastSectionIndex = aboutIndex
                    showDetail("resort", "Our Resort", "About the Resort", description)
                }
            }
            box.addView(textLine("ABOUT THE RESORT", 13f, true).apply {
                letterSpacing = 0.12f
                setTextColor(Color.rgb(236, 210, 158))
            })
            box.addView(textLine(description, 16f).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            focusCard(box, Color.rgb(24, 60, 58))
            root.addView(box, LinearLayout.LayoutParams(-1, dp(fitHeight(100, 82))).apply {
                bottomMargin = dp(15)
            })
            aboutView = box
        }
        root.addView(textLine(if (internalPhotoSeedAvailable) "EXPLORE OUR RESORT  ·  OFFICIAL SITE PHOTOS"
            else "EXPLORE OUR RESORT  ·  ILLUSTRATIVE PHOTOS", 13f, true).apply {
            setTextColor(muted)
            letterSpacing = 0.1f
        }, LinearLayout.LayoutParams(-1, dp(32)))
        val cardIndices = (0 until items.length()).filter { it != aboutIndex }
        val cards = mutableMapOf<Int, View>()
        cardIndices.chunked(4).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            group.forEach { index ->
                val item = items.optJSONObject(index) ?: return@forEach
                val title = item.optString("title", "Resort")
                val category = item.optString("category", "Resort")
                val details = if (item.optBoolean("verified", false)) item.optString("description", "")
                    else "Information pending • " + item.optString("description", "TEST DATA")
                val photo = when (category.lowercase(java.util.Locale.ROOT)) {
                    "rooms" -> 0
                    "dining" -> 1
                    "facilities" -> 2
                    "promotions" -> 3
                    else -> index % 4
                }
                val displayTitle = when (category.lowercase(java.util.Locale.ROOT)) {
                    "rooms" -> "Rooms & Amenities"
                    "dining" -> "Restaurants & Dining"
                    "facilities" -> "Spa, Pool & Activities"
                    "promotions" -> "Special Offers"
                    else -> title
                }
                val card = resortPhotoCard(displayTitle, category, photo) {
                    lastSectionIndex = index
                    showDetail("resort", "Our Resort", title, details)
                }
                cards[index] = card
                row.addView(card, LinearLayout.LayoutParams(0, dp(fitHeight(205, 158)), 1f).apply {
                    setMargins(dp(6), dp(7), dp(6), dp(7))
                })
            }
            repeat(4 - row.childCount) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            root.addView(row)
        }
        val target = if (lastSectionIndex == aboutIndex) aboutView
            else cards[lastSectionIndex] ?: cards.values.firstOrNull() ?: aboutView
        target?.let { focus -> focus.post { focus.requestFocus() } }
    }

    private fun showStaffTools() {
        page = "staff_tools"
        val root = newPage()
        header(root, "Staff Tools") { showHome() }
        root.addView(textLine("Hotel device controls", 24f, true),
            LinearLayout.LayoutParams(-1, dp(70)))
        root.addView(textLine("Use the remote to manage this TV. Resort content is managed in the secure CMS.", 16f).apply {
            setTextColor(muted)
        }, LinearLayout.LayoutParams(-1, dp(52)))
        val options = listOf(
            Pair("Edit Apps") { showQuickAppsEditor(lastQuickSlot) },
            Pair("Resort Media") { showMedia() },
            Pair("Connect / Sync") { showSetup() }
        )
        options.forEach { (title, callback) ->
            root.addView(action(title, callback), LinearLayout.LayoutParams(dp(340), dp(65)).apply {
                bottomMargin = dp(14)
            })
        }
        root.getChildAt(3)?.post { root.getChildAt(3)?.requestFocus() }
    }

    private fun emptyState(root: LinearLayout, title: String, subtitle: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = panel(false); setPadding(dp(35), dp(28), dp(35), dp(28))
        }
        box.addView(label(title, 22f, true))
        box.addView(label(subtitle, 15f).apply { setTextColor(muted) })
        root.addView(box, LinearLayout.LayoutParams(-1, dp(210)).apply { topMargin = dp(24) })
    }

    private fun showDetail(id: String, sectionTitle: String, title: String, description: String) {
        page = "detail:$id"
        val root = newPage()
        header(root, sectionTitle) { showSection(id, sectionTitle) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = panel(false)
            setPadding(dp(34), dp(28), dp(34), dp(30))
        }
        box.addView(textLine(title, 27f, true), LinearLayout.LayoutParams(-1, dp(54)))
        box.addView(textLine(description, 18f).apply { setTextColor(muted) })
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        val back = action("‹  Back to $sectionTitle") { showSection(id, sectionTitle) }
        root.addView(back, LinearLayout.LayoutParams(dp(260), dp(48)).apply { topMargin = dp(22) })
        back.post { back.requestFocus() }
    }

    private fun addAppCards(root: LinearLayout) {
        val apps = installedTvApps()
        if (apps.isEmpty()) {
            emptyState(root, "No TV apps installed", "Install a TV app to see it here.")
            return
        }
        apps.chunked(4).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            group.forEach { info ->
                row.addView(appCard(info.loadLabel(packageManager).toString(), info) { launchApp(info) },
                    LinearLayout.LayoutParams(0, dp(fitHeight(155, 130)), 1f).apply {
                        setMargins(dp(6), dp(6), dp(6), dp(6))
                    })
            }
            repeat(4 - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            root.addView(row)
        }
    }

    private fun appCard(name: String, info: android.content.pm.ResolveInfo?, onClick: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(5))
            isFocusable = true
            contentDescription = if (info == null) "$name, Not installed" else name
            setOnClickListener { onClick() }
        }
        val art = info?.let { quickAppArtwork(it) }
        val image = ImageView(this).apply {
            if (art != null) setImageDrawable(art.first) else setImageResource(android.R.drawable.sym_def_app_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = if (info == null) 0.35f else 1f
        }
        card.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        card.addView(label(name, 13f, true).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, dp(23)))
        if (info == null) card.addView(label("Not installed", 11f).apply { setTextColor(muted) },
            LinearLayout.LayoutParams(-1, dp(19)))
        focusCard(card, Color.rgb(28, 68, 75))
        return card
    }

    private fun showQuickAppsEditor(selectedSlot: Int = 0) {
        page = "quick_apps"
        lastQuickSlot = selectedSlot
        val root = newPage()
        header(root, "Customize Quick Apps") { showHome() }
        root.addView(textLine("Select a slot with the remote, then choose an installed TV app.", 16f),
            LinearLayout.LayoutParams(-1, dp(54)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        val slots = mutableListOf<View>()
        repeat(5) { slot ->
            val pkg = quickAppPackage(slot)
            val info = installedTvApps().firstOrNull { it.activityInfo.packageName == pkg }
            val name = info?.loadLabel(packageManager)?.toString()
                ?: if (pkg.isBlank()) "Empty slot" else defaultAppNames.getOrElse(slot) { "Unavailable" }
            val card = appCard(name, info) { showQuickAppPicker(slot) }
            slots.add(card)
            row.addView(card, LinearLayout.LayoutParams(0, dp(150), 1f).apply {
                setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        root.addView(row)
        val reset = action("Restore default five apps") {
            quickAppPrefs.edit().clear().apply()
            showQuickAppsEditor()
        }
        root.addView(reset, LinearLayout.LayoutParams(dp(260), dp(46)).apply { topMargin = dp(25) })
        slots[selectedSlot.coerceIn(0, 4)].post { slots[selectedSlot.coerceIn(0, 4)].requestFocus() }
    }

    private fun showQuickAppPicker(slot: Int) {
        page = "quick_picker:$slot"
        val root = newPage()
        header(root, "Choose app for slot " + (slot + 1)) { showQuickAppsEditor(slot) }
        val apps = installedTvApps()
        val used = (0 until 5).filter { it != slot }.map { quickAppPackage(it) }.toSet()
        if (apps.isEmpty()) emptyState(root, "No TV apps found", "Install an app to add it to Quick Apps.")
        apps.chunked(4).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            group.forEach { info ->
                val pkg = info.activityInfo.packageName
                val card = appCard(info.loadLabel(packageManager).toString(), info) {
                    if (pkg in used) Toast.makeText(this, "Already used in another slot", Toast.LENGTH_SHORT).show()
                    else {
                        quickAppPrefs.edit().putString("slot_$slot", pkg).apply()
                        showQuickAppsEditor(slot)
                    }
                }
                row.addView(card, LinearLayout.LayoutParams(0, dp(145), 1f).apply {
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                })
            }
            repeat(4 - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            root.addView(row)
        }
        root.addView(action("Clear this slot") {
            quickAppPrefs.edit().putString("slot_$slot", "").apply()
            showQuickAppsEditor(slot)
        }, LinearLayout.LayoutParams(dp(220), dp(46)).apply { topMargin = dp(16) })
    }

    @Deprecated("Legacy Back handling preserves native OK/D-pad")
    override fun onBackPressed() {
        if (page == "home") super.onBackPressed()
        else if (page == "staff_tools") showHome()
        else if (page == "media") showHome()
        else if (page.startsWith("quick_picker:")) showQuickAppsEditor(page.substringAfter(":").toIntOrNull() ?: 0)
        else if (page == "quick_apps") showHome()
        else if (page.startsWith("detail:")) {
            val id = page.substringAfter(":")
            val title = when (id) {
                "resort" -> "Our Resort"
                "explore" -> "Explore Krabi"
                "guest" -> "Guest Information"
                else -> "Entertainment"
            }
            showSection(id, title)
        } else showHome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = try {
            val published = File(filesDir, "published-content.json")
            if (published.isFile) JSONObject(published.readText())
            else JSONObject(assets.open("hotel_content.json").bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            JSONObject()
        }
        val last = getSharedPreferences("sync_status", MODE_PRIVATE).getLong("last_updated", 0)
        if (last > 0) syncStatus = "Last updated " +
            java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(last))
        showHome()
        syncInBackground()
    }

    override fun onStop() {
        slideshow?.stop()
        super.onStop()
    }

    private fun showMedia() {
        page = "media"
        val root = newPage()
        header(root, "Resort Media") { showHome() }
        root.addView(textLine("Checking saved media…", 17f).apply { setTextColor(muted) })
        Thread {
            val slides = VerifiedMediaCache(this).activeSlides()
            runOnUiThread {
                if (page != "media") return@runOnUiThread
                val view = newPage()
                val back = header(view, "Resort Media") { showHome() }
                if (slides.isEmpty()) {
                    emptyState(view, "No verified media yet",
                        "Approved resort photos and videos will appear here after sync.")
                } else {
                    val host = FrameLayout(this)
                    view.addView(host, LinearLayout.LayoutParams(-1, dp(350)))
                    slideshow = MediaSlideshow(this, host).also { it.start(slides) }
                }
                back.requestFocus()
            }
        }.start()
    }

    private fun syncInBackground() {
        val endpoint = ServerConfigStore(this).active() ?: return
        val token = credentials.token() ?: return
        Thread {
            val host = java.net.URI(endpoint.normalizedHttpsUrl()).host
            val coordinator = MediaSyncCoordinator(this)
            val result = coordinator.sync(endpoint, token, host)
            runOnUiThread {
                assignedRoom = if (result == MediaSyncCoordinator.Status.UPDATED)
                    coordinator.assignedRoom else ""
                syncStatus = when (result) {
                    MediaSyncCoordinator.Status.UPDATED -> {
                        content = try { JSONObject(File(filesDir, "published-content.json").readText()) }
                        catch (_: Exception) { content }
                        getSharedPreferences("sync_status", MODE_PRIVATE).edit()
                            .putLong("last_updated", System.currentTimeMillis()).apply()
                        "Updated " + java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date())
                    }
                    MediaSyncCoordinator.Status.REJECTED -> "Sync rejected • check enrollment"
                    else -> "Offline • showing saved guide"
                }
                if (page == "home") showHome()
            }
        }.start()
    }

    private fun showSetup() {
        page = "setup"
        val root = newPage()
        header(root, "TV connection") { showHome() }
        root.addView(textLine("Enter the HTTPS server and one-time code from the admin CMS.", 16f),
            LinearLayout.LayoutParams(-1, dp(48)))
        val server = EditText(this).apply {
            hint = "https://hotel-staging.example.com"
            setSingleLine(true)
            setText(ServerConfigStore(this@MainActivity).active()?.baseUrl ?: "")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        val code = EditText(this).apply {
            hint = "One-time enrollment code"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        root.addView(server, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })
        root.addView(code, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(16) })
        val connect = action("Enroll this TV") {
            val endpoint = ServerEndpoint(server.text.toString())
            val base = endpoint.normalizedHttpsUrl()
            val enrollmentCode = code.text.toString().trim()
            if (base == null || enrollmentCode.isBlank()) {
                Toast.makeText(this, "Valid HTTPS URL and code required", Toast.LENGTH_LONG).show()
            } else {
                Thread {
                    val result = enroll(base, enrollmentCode)
                    runOnUiThread {
                        code.text.clear()
                        if (result != null && credentials.save(result.first, result.second) &&
                            ServerConfigStore(this).saveVerifiedEndpoint(endpoint)) {
                            syncStatus = "Enrolled • syncing"
                            showHome()
                            syncInBackground()
                        } else Toast.makeText(this, "Enrollment failed", Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
        }
        root.addView(connect, LinearLayout.LayoutParams(dp(280), dp(58)))
        server.requestFocus()
    }

    private fun enroll(base: String, code: String): Pair<String, String>? {
        val connection = try {
            URL("$base/api/v1/device/enroll").openConnection() as HttpURLConnection
        } catch (_: Exception) { return null }
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(JSONObject().put("code", code).toString().toByteArray())
            }
            if (connection.responseCode != 200 || connection.contentLengthLong > 4096) null
            else {
                val data = connection.inputStream.use { it.readBytes() }
                if (data.size > 4096) null else JSONObject(String(data)).let {
                    Pair(it.getString("device_id"), it.getString("token"))
                }
            }
        } catch (_: Exception) { null }
        finally { connection.disconnect() }
    }
}

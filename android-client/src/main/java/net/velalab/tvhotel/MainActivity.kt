package net.velalab.tvhotel

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
    private var screen: LinearLayout? = null
    private var lastHomeIndex = 0
    private var page = "home"
    private var content = JSONObject()
    private var syncStatus = "Offline guide"
    private var assignedRoom = ""
    private var slideshow: MediaSlideshow? = null
    private val credentials by lazy { DeviceCredentials(this) }

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
            setColor(if (focused) gold else cards)
            setStroke(dp(if (focused) 4 else 2), if (focused) Color.WHITE else Color.GRAY)
        }
        button.setTextColor(if (focused) Color.BLACK else Color.WHITE)
        button.scaleX = if (focused) 1.055f else 1f
        button.scaleY = if (focused) 1.055f else 1f
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

    private fun newPage(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(8), dp(22), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(11, 46, 46), Color.rgb(6, 22, 37))
            )
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(root)
        }
        setContentView(scroll)
        screen = root
        return root
    }

    private fun addLogo(root: LinearLayout, home: Boolean) {
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.aonang_fiore_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ao Nang Fiore Resort"
        }
        root.addView(logo, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(if (home) 148 else 80)
        ))
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
                .setClassName(target.packageName, target.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to open this app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shortcutStyle(view: LinearLayout, focused: Boolean, present: Boolean) {
        view.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (focused) intArrayOf(Color.rgb(254, 193, 58), gold)
            else intArrayOf(Color.rgb(31, 73, 75), Color.rgb(13, 43, 55))
        ).apply {
            cornerRadius = dp(17).toFloat()
            setStroke(dp(if (focused) 4 else 2), if (focused) Color.WHITE else Color.rgb(115, 151, 153))
        }
        view.alpha = if (present || focused) 1f else 0.65f
        view.scaleX = if (focused) 1.04f else 1f
        view.scaleY = if (focused) 1.04f else 1f
        (0 until view.childCount).map { view.getChildAt(it) }
            .filterIsInstance<TextView>().forEach {
                it.setTextColor(if (focused) Color.BLACK else Color.WHITE)
            }
    }

    /** Five launcher-like shortcuts. Configuration is local per TV, never committed with guest data. */
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
                setPadding(dp(6), dp(7), dp(6), dp(5))
                isFocusable = true
                isFocusableInTouchMode = true
                contentDescription = "$title, shortcut ${slot + 1}"
            }
            val iconView = ImageView(this).apply {
                if (found != null) setImageDrawable(found.loadIcon(packageManager))
                else setImageResource(android.R.drawable.sym_def_app_icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                alpha = if (found == null) 0.5f else 1f
                contentDescription = title
            }
            tile.addView(iconView, LinearLayout.LayoutParams(-1, dp(52)))
            tile.addView(label(title, 13f, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, dp(23)))
            if (found == null) {
                tile.addView(label("Not installed", 10f), LinearLayout.LayoutParams(-1, dp(17)))
            }
            tile.setOnFocusChangeListener { _, focused ->
                shortcutStyle(tile, focused, found != null)
            }
            shortcutStyle(tile, false, found != null)
            tile.setOnClickListener {
                if (found != null) launchApp(found) else showQuickAppsEditor(slot)
            }
            tile.setOnLongClickListener { showQuickAppsEditor(slot); true }
            row.addView(tile, LinearLayout.LayoutParams(0, dp(111), 1f).apply {
                setMargins(dp(4), dp(5), dp(4), dp(5))
            })
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showQuickAppsEditor(selectedSlot: Int = 0) {
        page = "quick_apps"
        val root = newPage()
        root.addView(label("Customize Quick Apps", 26f, true))
        root.addView(label("Choose a shortcut slot, then select an installed TV app.", 15f))
        val slots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(5) { slot ->
            val pkg = quickAppPackage(slot)
            val info = installedTvApps().firstOrNull { it.activityInfo.packageName == pkg }
            val name = info?.loadLabel(packageManager)?.toString() ?: if (pkg.isBlank()) "Empty" else defaultAppNames.getOrElse(slot) { "Missing" }
            val button = action("${slot + 1}\n$name") { showQuickAppPicker(slot) }.apply { textSize = 12f }
            slots.addView(button, LinearLayout.LayoutParams(0, dp(90), 1f).apply {
                setMargins(dp(4), dp(8), dp(4), dp(8))
            })
            if (slot == selectedSlot) button.post { button.requestFocus() }
        }
        root.addView(slots, LinearLayout.LayoutParams(-1, -2))
        val reset = action("Restore default five apps") {
            quickAppPrefs.edit().clear().apply()
            showQuickAppsEditor()
        }
        root.addView(reset, LinearLayout.LayoutParams(dp(340), dp(55)))
        val back = action("Back to Welcome") { showHome() }
        root.addView(back, LinearLayout.LayoutParams(dp(300), dp(55)))
    }

    private fun showQuickAppPicker(slot: Int) {
        page = "quick_picker"
        val root = newPage()
        root.addView(label("Choose app for slot ${slot + 1}", 25f, true))
        root.addView(label("Only installed TV apps are listed. Other slots will not change.", 15f))
        val apps = installedTvApps()
        val usedByOtherSlots = (0 until 5).filter { it != slot }.map { quickAppPackage(it) }.toSet()
        apps.forEach { info ->
            val pkg = info.activityInfo.packageName
            val name = info.loadLabel(packageManager).toString()
            val button = action(if (pkg in usedByOtherSlots) "$name (already selected)" else name) {
                if (pkg in usedByOtherSlots) {
                    Toast.makeText(this, "Already used in another slot", Toast.LENGTH_SHORT).show()
                } else {
                    quickAppPrefs.edit().putString("slot_$slot", pkg).apply()
                    showQuickAppsEditor(slot)
                }
            }
            val icon = info.loadIcon(packageManager).apply { setBounds(0, 0, dp(40), dp(40)) }
            button.setCompoundDrawables(icon, null, null, null)
            button.compoundDrawablePadding = dp(18)
            root.addView(button, LinearLayout.LayoutParams(-1, dp(65)).apply {
                setMargins(dp(8), dp(4), dp(8), dp(4))
            })
        }
        if (apps.isEmpty()) root.addView(label("No installed TV apps found.", 17f))
        val clear = action("Clear this slot") {
            quickAppPrefs.edit().putString("slot_$slot", "").apply()
            showQuickAppsEditor(slot)
        }
        root.addView(clear, LinearLayout.LayoutParams(dp(300), dp(55)))
        val back = action("Back to shortcuts") { showQuickAppsEditor(slot) }
        root.addView(back, LinearLayout.LayoutParams(dp(300), dp(55)))
        back.post { back.requestFocus() }
    }

    private fun addAppButtons(root: LinearLayout, limit: Int = 8, compact: Boolean = false) {
        val available = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString() }.take(limit)
        if (available.isEmpty()) {
            root.addView(label("No streaming apps are installed on this device.", 17f))
            return
        }
        available.chunked(4).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            group.forEach { info ->
                val target = info.activityInfo
                val button = action(info.loadLabel(packageManager).toString()) {
                    try {
                        startActivity(Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                            .setClassName(target.packageName, target.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        Toast.makeText(this, "Unable to open this app", Toast.LENGTH_SHORT).show()
                    }
                }
                button.textSize = if (compact) 12f else 16f
                val icon = info.loadIcon(packageManager)
                val iconSize = dp(if (compact) 30 else 40)
                icon.setBounds(0, 0, iconSize, iconSize)
                button.setCompoundDrawables(null, icon, null, null)
                button.compoundDrawablePadding = dp(if (compact) 3 else 8)
                row.addView(button, LinearLayout.LayoutParams(0, dp(if (compact) 74 else 108), 1f).apply {
                    val gap = dp(if (compact) 4 else 7)
                    setMargins(gap, gap, gap, gap)
                })
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun showHome() {
        slideshow?.stop()
        slideshow = null
        page = "home"
        val root = newPage()
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(16, 67, 66), Color.rgb(5, 32, 46))
            ).apply { cornerRadius = dp(24).toFloat() }
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.aonang_fiore_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ao Nang Fiore Resort"
        }
        hero.addView(logo, LinearLayout.LayoutParams(0, dp(126), 1f))
        val greetingBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        greetingBlock.addView(label("WELCOME TO", 14f, true))
        greetingBlock.addView(label("Ao Nang Fiore Resort", 25f, true))
        hero.addView(greetingBlock, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })
        val greeting = content.optJSONObject("guest_welcome")
        val mode = greeting?.optString("mode", "generic") ?: "generic"
        val value = greeting?.optString("value", "")?.trim()?.take(50) ?: ""
        val welcomeLine = when (mode) {
            "hidden" -> ""
            "room" -> if (value.isNotBlank()) "Welcome, Room $value" else "Welcome, Guest"
            "name" -> if (value.isNotBlank()) "Welcome, $value" else "Welcome, Guest"
            else -> if (assignedRoom.isNotBlank()) "Welcome, Room $assignedRoom" else "Welcome, Guest"
        }
        if (welcomeLine.isNotEmpty()) {
            root.addView(label(welcomeLine, 18f), LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }

        // Hotel-first grid with D-pad-accessible icon tiles, titles and subtitles.
        val sections = listOf(
            Triple("entertainment", "Entertainment", "Streaming & TV"),
            Triple("resort", "Our Resort", "Dining & facilities"),
            Triple("explore", "Explore Krabi", "Discover the area"),
            Triple("guest", "Guest Information", "Your stay & services")
        )
        val buttons = mutableListOf<Button>()
        sections.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            pair.forEach { (id, title, subtitle) ->
                val button = action("$title\n$subtitle") { showSection(id, title) }.apply {
                    textSize = 18f
                    setLineSpacing(dp(2).toFloat(), 1f)
                    val iconRes = when (id) {
                        "entertainment" -> android.R.drawable.ic_media_play
                        "resort" -> android.R.drawable.ic_menu_myplaces
                        "explore" -> android.R.drawable.ic_menu_compass
                        else -> android.R.drawable.ic_menu_info_details
                    }
                    getDrawable(iconRes)?.let { icon ->
                        icon.mutate()
                        icon.setTint(Color.WHITE)
                        icon.setBounds(0, 0, dp(26), dp(26))
                        setCompoundDrawables(null, icon, null, null)
                        compoundDrawablePadding = dp(5)
                    }
                }
                buttons.add(button)
                row.addView(button, LinearLayout.LayoutParams(0, dp(90), 1f).apply {
                    setMargins(dp(7), dp(5), dp(7), dp(5))
                })
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(label("QUICK APPS", 15f, true), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(13)
            bottomMargin = dp(2)
        })
        quickApps(root)
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        footer.addView(label(syncStatus, 13f), LinearLayout.LayoutParams(0, dp(42), 1f))
        val customize = action("Edit Apps") { showQuickAppsEditor() }.apply { textSize = 13f }
        footer.addView(customize, LinearLayout.LayoutParams(dp(125), dp(44)).apply { rightMargin = dp(6) })
        val media = action("Resort Media") { showMedia() }.apply { textSize = 13f }
        footer.addView(media, LinearLayout.LayoutParams(dp(135), dp(44)).apply {
            rightMargin = dp(8)
        })
        val settings = action("Connect / Sync") { showSetup() }.apply { textSize = 13f }
        footer.addView(settings, LinearLayout.LayoutParams(dp(165), dp(44)))
        root.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        buttons.forEachIndexed { index, btn ->
            btn.setOnFocusChangeListener { _, focused ->
                style(btn, focused)
                if (focused) lastHomeIndex = index
            }
        }
        buttons.getOrNull(lastHomeIndex.coerceIn(0, buttons.lastIndex))?.let {
            it.post { it.requestFocus() }
        }
    }

    private fun showSection(id: String, title: String) {
        slideshow?.stop()
        slideshow = null
        page = id
        val root = newPage()
        addLogo(root, false)
        root.addView(label(title, 27f, true))
        val back = action("← Back to Welcome") { showHome() }
        root.addView(back, LinearLayout.LayoutParams(dp(250), dp(52)).apply {
            topMargin = dp(12)
            bottomMargin = dp(16)
        })
        if (id == "entertainment") {
            root.addView(label("Available Apps", 19f, true))
            addAppButtons(root, 12)
            back.post { back.requestFocus() }
            return
        }
        val items = entries(id)
        if (items == null || items.length() == 0) {
            root.addView(label("No information has been published yet.", 18f))
        } else {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val name = item.optString("title", "Information")
                val details = if (item.optBoolean("verified", false))
                    item.optString("description", "") else
                    "Information pending • " + item.optString("description", "TEST DATA")
                val button = action(name) { showDetail(id, title, name, details) }
                root.addView(button, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(64)
                ).apply { setMargins(dp(10), dp(6), dp(10), dp(6)) })
            }
        }
        back.post { back.requestFocus() }
    }

    private fun showDetail(id: String, sectionTitle: String, title: String, description: String) {
        page = "detail:$id"
        val root = newPage()
        addLogo(root, false)
        root.addView(label(title, 25f, true))
        val info = label(description, 18f).apply {
            gravity = Gravity.START
            setPadding(dp(18), dp(22), dp(18), dp(22))
        }
        root.addView(info, LinearLayout.LayoutParams(-1, -2))
        val back = action("← Back to $sectionTitle") { showSection(id, sectionTitle) }
        root.addView(back, LinearLayout.LayoutParams(dp(300), dp(64)))
        back.post { back.requestFocus() }
    }

    @Deprecated("Legacy Back handling preserves native OK/D-pad")
    override fun onBackPressed() {
        if (page == "home") super.onBackPressed()
        else if (page == "media") showHome()
        else if (page == "quick_picker") showQuickAppsEditor()
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
        root.addView(label("Resort Media", 27f, true))
        root.addView(label("Checking saved media…", 17f))
        Thread {
            val slides = VerifiedMediaCache(this).activeSlides()
            runOnUiThread {
                if (page != "media") return@runOnUiThread
                val view = newPage()
                view.addView(label("Resort Media", 27f, true))
                val back = action("Back to Welcome") { showHome() }
                view.addView(back, LinearLayout.LayoutParams(dp(250), dp(54)))
                if (slides.isEmpty()) {
                    view.addView(label("No verified media has been published yet.", 18f))
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
        root.addView(label("TV connection", 27f, true))
        root.addView(label("Enter the HTTPS server and one-time code from the admin CMS.", 16f))
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
        root.addView(server, LinearLayout.LayoutParams(-1, dp(58)))
        root.addView(code, LinearLayout.LayoutParams(-1, dp(58)))
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
        val back = action("Back to Welcome") { showHome() }
        root.addView(back, LinearLayout.LayoutParams(dp(280), dp(54)))
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

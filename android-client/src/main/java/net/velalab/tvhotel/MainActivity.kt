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

    /** A fixed, admin-configurable list of five shortcuts. Missing apps never get substituted. */
    private fun quickApps(root: LinearLayout) {
        val requested = listOf("Netflix", "Disney+", "YouTube", "Prime Video", "iQIYI")
        val available = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val packages = mapOf(
            "Netflix" to setOf("com.netflix.ninja"),
            "Disney+" to setOf("com.disney.disneyplus"),
            "YouTube" to setOf("com.google.android.youtube.tv"),
            "Prime Video" to setOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
            "iQIYI" to setOf("com.iqiyi.i18n.tv", "com.qiyi.video.tv")
        )
        requested.forEach { name ->
            val found = available.firstOrNull { candidate ->
                candidate.activityInfo.packageName in (packages[name] ?: emptySet())
            }
            val button = action(if (found == null) "$name\nNot Installed" else name) {
                val target = found?.activityInfo
                if (target != null) {
                    try {
                        startActivity(Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                            .setClassName(target.packageName, target.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        Toast.makeText(this, "Unable to open $name", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            button.textSize = 13f
            if (found != null) {
                val icon = found.loadIcon(packageManager)
                icon.setBounds(0, 0, dp(36), dp(36))
                button.setCompoundDrawables(null, icon, null, null)
                button.compoundDrawablePadding = dp(3)
            } else {
                button.isEnabled = false
                button.alpha = 0.65f
                button.isFocusable = false
                button.isFocusableInTouchMode = false
            }
            row.addView(button, LinearLayout.LayoutParams(0, dp(86), 1f).apply {
                setMargins(dp(3), dp(4), dp(3), dp(4))
            })
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
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
        val media = action("Resort Media") { showMedia() }.apply { textSize = 13f }
        footer.addView(media, LinearLayout.LayoutParams(dp(150), dp(44)).apply {
            rightMargin = dp(8)
        })
        val settings = action("Connect / Sync") { showSetup() }.apply { textSize = 13f }
        footer.addView(settings, LinearLayout.LayoutParams(dp(190), dp(44)))
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

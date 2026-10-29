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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/**
 * Hotel-first offline guest guide. Remote uses native Android focus/OK navigation.
 * Guest copy is bundled in assets/hotel_content.json until an authenticated CMS is ready.
 */
class MainActivity : Activity() {
    private val navy = Color.rgb(7, 28, 42)
    private val gold = Color.rgb(235, 170, 28)
    private val cards = Color.rgb(42, 63, 76)
    private var screen: LinearLayout? = null
    private var lastHomeIndex = 0
    private var page = "home"
    private var content = JSONObject()

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
            setPadding(dp(28), dp(18), dp(28), dp(18))
            setBackgroundColor(navy)
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
            ViewGroup.LayoutParams.MATCH_PARENT, dp(if (home) 155 else 95)
        ))
    }

    private fun section(id: String): JSONObject = content.optJSONObject(id) ?: JSONObject()
    private fun entries(id: String) = section(id).optJSONArray("items")

    private fun addAppButtons(root: LinearLayout, limit: Int = 8) {
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
                val icon = info.loadIcon(packageManager)
                icon.setBounds(0, 0, dp(40), dp(40))
                button.setCompoundDrawables(null, icon, null, null)
                button.compoundDrawablePadding = dp(8)
                row.addView(button, LinearLayout.LayoutParams(0, dp(108), 1f).apply {
                    setMargins(dp(7), dp(7), dp(7), dp(7))
                })
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun showHome() {
        page = "home"
        val root = newPage()
        addLogo(root, true)
        root.addView(label("Welcome to Ao Nang Fiore Resort", 24f, true))
        root.addView(label("Discover your stay", 16f), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(20)
        })
        val sections = listOf(
            "entertainment" to "Entertainment",
            "resort" to "Our Resort",
            "explore" to "Explore Krabi",
            "guest" to "Guest Information"
        )
        val buttons = mutableListOf<Button>()
        sections.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (id, title) ->
                val button = action(title) { showSection(id, title) }
                buttons.add(button)
                row.addView(button, LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                    setMargins(dp(8), dp(7), dp(8), dp(7))
                })
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(label("Quick Apps", 18f, true), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(18)
        })
        addAppButtons(root, 4)
        buttons.getOrNull(lastHomeIndex.coerceIn(0, buttons.lastIndex))?.let {
            it.post { it.requestFocus() }
        }
        buttons.forEachIndexed { index, btn ->
            val previous = btn.onFocusChangeListener
            btn.setOnFocusChangeListener { v, focused ->
                style(btn, focused)
                if (focused) lastHomeIndex = index
            }
        }
    }

    private fun showSection(id: String, title: String) {
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
                val details = item.optString("description", "")
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
            JSONObject(assets.open("hotel_content.json").bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            JSONObject()
        }
        showHome()
    }
}

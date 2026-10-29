package net.velalab.tvhotel

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Offline-first visual shell for one-device pilot.
 * Deliberately does NOT claim HOME until guest-TV behavior is tested.
 * Media playback, room enrollment and admin controls are future milestones.
 */
class MainActivity : Activity() {
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
        root.addView(line("AO NANG FIORE RESORT", 37f, true))
        root.addView(line("Welcome to your peaceful escape in Krabi", 23f))
        val hint = line("Hotel TV Pilot  •  Offline welcome screen", 15f)
        hint.setTextColor(Color.LTGRAY)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 36 }
        root.addView(hint, params)
        setContentView(root)
    }
}

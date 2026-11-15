package ru.mirear

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 10, 20)
    private val panel = Color.rgb(19, 22, 38)
    private val neon = Color.rgb(0, 235, 255)
    private val purple = Color.rgb(183, 76, 255)
    private val muted = Color.rgb(155, 165, 190)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(22), dp(28), dp(22), dp(24))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(text("MI REAR DISPLAY", 13f, neon, true))
        content.addView(text("MiRear", 36f, Color.WHITE, true).apply {
            setPadding(0, dp(8), 0, 0)
        })
        content.addView(text("SECOND SCREEN CONTROL CENTER", 11f, muted, true))
        content.addView(card("DEVICE STATUS", "Xiaomi Mi 11 Ultra\nAndroid API ${Build.VERSION.SDK_INT}\nModel: ${Build.MODEL}"))
        content.addView(card("MIRROR MODE", "Not connected yet\n\nThe display-routing backend is being validated for your MIUI/HyperOS firmware."))
        content.addView(card("ACCESS MODES", "STANDARD  •  No root\nEXTENDED  •  Optional Shizuku\n\nRoot access is not required for the initial setup."))
        val note = text("BUILD 0.1.0  •  FOUNDATION", 11f, purple, true)
        note.gravity = Gravity.CENTER
        note.setPadding(0, dp(22), 0, dp(10))
        content.addView(note)
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    private fun card(title: String, body: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            setBackgroundColor(panel)
        }
        box.addView(text(title, 12f, neon, true))
        box.addView(text(body, 15f, Color.WHITE, false).apply {
            setPadding(0, dp(10), 0, 0)
        })
        val params = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(18)
        }
        box.layoutParams = params
        return box
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

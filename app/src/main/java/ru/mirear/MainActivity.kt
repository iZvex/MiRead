package ru.mirear

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 10, 20)
    private val panel = Color.rgb(19, 22, 38)
    private val neon = Color.rgb(0, 235, 255)
    private val purple = Color.rgb(183, 76, 255)
    private val muted = Color.rgb(155, 165, 190)
    private lateinit var displayReport: TextView
    private lateinit var displayManager: DisplayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        setContentView(buildScreen())
        scanDisplays()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(22), dp(28), dp(22), dp(24))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        content.addView(text("MI REAR DISPLAY", 13f, neon, true))
        content.addView(text("MiRear", 36f, Color.WHITE, true).apply {
            setPadding(0, dp(8), 0, 0)
        })
        content.addView(text("SECOND SCREEN CONTROL CENTER", 11f, muted, true))
        content.addView(card("DEVICE STATUS", "Xiaomi Mi 11 Ultra\nAndroid API ${Build.VERSION.SDK_INT}\nModel: ${Build.MODEL}\nBuild: ${Build.DISPLAY}"))
        content.addView(card("MIRROR MODE", "Not connected yet\n\nThe display-routing backend is being validated for your MIUI/HyperOS firmware."))
        content.addView(card("DISPLAY DIAGNOSTICS", "Checking Android display service…").also {
            val inner = it as LinearLayout
            displayReport = text("Scanning available displays…", 14f, Color.WHITE, false)
            displayReport.setPadding(0, dp(10), 0, 0)
            inner.addView(displayReport)
            val scanButton = Button(this).apply {
                text = "SCAN DISPLAYS"
                setTextColor(neon)
                setBackgroundColor(Color.rgb(31, 36, 58))
                setOnClickListener { scanDisplays() }
            }
            inner.addView(scanButton, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            })
        })
        content.addView(card("ACCESS MODES", "STANDARD  •  No root\nEXTENDED  •  Optional Shizuku\n\nRoot access is not required for the initial setup."))
        val note = text("BUILD 0.2.0  •  DISPLAY DIAGNOSTICS", 11f, purple, true)
        note.gravity = Gravity.CENTER
        note.setPadding(0, dp(22), 0, dp(10))
        content.addView(note)
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    private fun scanDisplays() {
        val displays = displayManager.displays
        val report = StringBuilder()
        report.append("Android reports ${displays.size} display(s).\n")
        report.append("This list includes only displays exposed to ordinary apps.\n\n")
        displays.forEachIndexed { index, display ->
            val mode = display.mode
            val state = when (display.state) {
                Display.STATE_ON -> "ON"
                Display.STATE_OFF -> "OFF"
                Display.STATE_DOZE -> "DOZE"
                Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
                Display.STATE_UNKNOWN -> "UNKNOWN"
                else -> display.state.toString()
            }
            report.append("#${index + 1}  ${display.name}\n")
            report.append("ID: ${display.displayId}  •  State: ${state}\n")
            report.append("Mode: ${mode.physicalWidth} × ${mode.physicalHeight} @ ${mode.refreshRate} Hz\n")
            report.append("Rotation: ${display.rotation}  •  Flags: 0x${display.flags.toString(16)}\n\n")
        }
        if (displays.isEmpty()) report.append("No public displays were returned by DisplayManager.")
        displayReport.text = report.toString()
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
        box.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) }
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

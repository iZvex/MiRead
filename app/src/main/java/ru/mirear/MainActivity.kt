package ru.mirear

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.reflect.Method

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 10, 20)
    private val panel = Color.rgb(19, 22, 38)
    private val neon = Color.rgb(0, 235, 255)
    private val purple = Color.rgb(183, 76, 255)
    private val muted = Color.rgb(155, 165, 190)
    private lateinit var displayReport: TextView
    private lateinit var actionReport: TextView
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
        content.addView(card("REAR DISPLAY TEST", "This test uses Xiaomi's rear-screen power command observed in the open-source Mirror2RearUltra project, then asks Android to open a test window on the detected secondary display. No root is requested."))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            setBackgroundColor(panel)
        }
        actions.addView(text("ACTIONS", 12f, neon, true))
        actionReport = text("Ready. First scan the available displays.", 14f, Color.WHITE, false)
        actionReport.setPadding(0, dp(10), 0, dp(10))
        actions.addView(actionReport)
        actions.addView(actionButton("ACTIVATE + OPEN TEST WINDOW") { activateAndOpenRearTest() })
        actions.addView(actionButton("TURN REAR DISPLAY OFF") { turnRearDisplayOff() })
        actions.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) }
        content.addView(actions)

        content.addView(card("DISPLAY DIAGNOSTICS", "Android display service"))
        val diag = content.getChildAt(content.childCount - 1) as LinearLayout
        displayReport = text("Scanning available displays…", 14f, Color.WHITE, false)
        displayReport.setPadding(0, dp(10), 0, 0)
        diag.addView(displayReport)
        diag.addView(actionButton("SCAN DISPLAYS") { scanDisplays() })

        content.addView(card("ACCESS MODES", "STANDARD  •  No root\nEXTENDED  •  Optional Shizuku\n\nThis build tests the public secondary-display launch path and Xiaomi's device-specific power Binder command."))
        val note = text("BUILD 0.3.0  •  REAR DISPLAY TEST", 11f, purple, true)
        note.gravity = Gravity.CENTER
        note.setPadding(0, dp(22), 0, dp(10))
        content.addView(note)
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(neon)
        setBackgroundColor(Color.rgb(31, 36, 58))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    }

    private fun activateAndOpenRearTest() {
        val secondary = findSecondaryDisplay()
        if (secondary == null) {
            actionReport.text = "No secondary display is currently available. Tap SCAN DISPLAYS and retry."
            return
        }
        val powerResult = setXiaomiRearDisplayPower(true)
        scanDisplays()
        try {
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = secondary.displayId
            }
            val intent = Intent(this, RearTestActivity::class.java)
            startActivity(intent, options.toBundle())
            actionReport.text = "Test window launch requested for display ID ${secondary.displayId}. Xiaomi power command: $powerResult. Check the rear panel."
        } catch (e: Exception) {
            actionReport.text = "Power command: $powerResult\nAndroid refused secondary-display launch: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun turnRearDisplayOff() {
        val result = setXiaomiRearDisplayPower(false)
        actionReport.text = "Rear-display OFF command sent. Binder result: $result"
        scanDisplays()
    }

    private fun findSecondaryDisplay(): Display? {
        val displays = displayManager.displays
        return displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.mode.physicalWidth <= 300 && it.mode.physicalHeight <= 500 }
            ?: displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    // Device-specific transaction codes taken from the inspected Mirror2RearUltra source.
    // This is best-effort: Xiaomi firmware may reject the transaction or require privileged access.
    private fun setXiaomiRearDisplayPower(enable: Boolean): String {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("android.os.IPowerManager")
            val code: Int
            if (enable) {
                code = 16777210
                data.writeStrongBinder(Binder())
                data.writeLong(SystemClock.uptimeMillis())
                data.writeInt(1)
                data.writeString("CAMERA_CALL")
            } else {
                code = 16777211
                data.writeLong(SystemClock.uptimeMillis())
                data.writeString("CAMERA_CALL")
            }
            val serviceClass = Class.forName("android.os.ServiceManager")
            val method: Method = serviceClass.getMethod("getService", String::class.java)
            val service = method.invoke(null, "power") as? android.os.IBinder
                ?: return "Power service unavailable"
            val accepted = service.transact(code, data, reply, android.os.IBinder.FLAG_ONEWAY)
            if (accepted) "Binder accepted transaction" else "Binder did not handle transaction"
        } catch (e: Exception) {
            "Unavailable: ${e.javaClass.simpleName}: ${e.message ?: "no details"}"
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun scanDisplays() {
        val displays = displayManager.displays
        val report = StringBuilder()
        report.append("Android reports ${displays.size} display(s).\n")
        report.append("Only displays exposed to ordinary apps are listed.\n\n")
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
            report.append("ID: ${display.displayId}  •  State: $state\n")
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

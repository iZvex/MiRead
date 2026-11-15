package ru.mirear

import android.app.*
import android.content.*
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.*

class MainActivity : Activity() {
    private val activityInstanceId = java.util.UUID.randomUUID().toString().take(8)
    private val dm by lazy { getSystemService(DISPLAY_SERVICE) as DisplayManager }
    private val pm by lazy { getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    private lateinit var report: TextView
    private lateinit var status: TextView
    private var frameListener: Window.OnFrameMetricsAvailableListener? = null
    private var frameSamples = 0
    private var jankyFrames = 0
    private var maxFrameMs = 0.0
    private var pendingCaptureCheck = false
    private val mirrorServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? ScreenMirrorService.LocalBinder)?.service()
            val active = service?.isCaptureActive() == true
            Diagnostics.log(this@MainActivity, "MAIN_CAPTURE_SERVICE_CHECK active=$active")
            try { unbindService(this) } catch (_: Exception) {}
            if (pendingCaptureCheck) {
                pendingCaptureCheck = false
                if (active) {
                    status.text = "Existing mirror session found. Reopening rear display…"
                    openRear(null, null)
                } else requestCapturePermission()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Diagnostics.log(this@MainActivity, "MAIN_CAPTURE_SERVICE_DISCONNECTED")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.log(this, "MAIN_ON_CREATE id=$activityInstanceId taskId=$taskId savedState=${savedInstanceState != null}")
        window.statusBarColor = Color.rgb(8, 10, 20)
        window.navigationBarColor = Color.BLACK
        val root = LinearLayout(this).apply {
            orientation = 1
            setPadding(22, 28, 22, 24)
            setBackgroundColor(Color.rgb(8, 10, 20))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = 1 }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(typeface, 1)
                setPadding(0, 8, 0, 8)
            }
        fun button(value: String, action: () -> Unit) =
            Button(this).apply {
                text = value
                setTextColor(Color.CYAN)
                setOnClickListener { action() }
            }

        content.addView(label("MI REAR DISPLAY", 13f, Color.CYAN, true))
        content.addView(label("MiRear", 36f, Color.WHITE, true))
        content.addView(label("SCREEN MIRRORING • 0.7.8 LIFECYCLE DIAGNOSTICS", 12f, 0xffb74cff.toInt(), true))
        status = label("Ready. Turn on the rear display and start mirroring.", 15f, Color.WHITE)
        content.addView(status)
        content.addView(button("START SCREEN MIRRORING") { startCapture() })
        content.addView(button("STOP MIRRORING") {
            stopService(Intent(this, ScreenMirrorService::class.java))
            status.text = "Stop requested."
            Diagnostics.log(this, "USER_REQUESTED_STOP")
        })
        content.addView(button("OPEN REAR TEST") { openRear(null, null) })
        content.addView(label("DISPLAY DIAGNOSTICS", 13f, Color.CYAN, true))
        report = label("", 14f, Color.WHITE)
        content.addView(report)
        content.addView(button("SCAN DISPLAYS") { scan() })
        content.addView(button("EXPORT DIAGNOSTICS") { shareDiagnostics() })
        content.addView(button("CLEAR DIAGNOSTICS") {
            Diagnostics.clear(this)
            status.text = "Diagnostics cleared."
        })
        content.addView(label(
            "For a useful test: clear diagnostics, start mirroring, use the phone normally for 30–60 seconds, stop mirroring, then export the log.",
            13f, 0xff9ba5be.toInt()
        ))
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
        scan()
        installFrameDiagnostics()
        Diagnostics.log(this, "APP_STARTED sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER} ${Build.MODEL} version=0.7.8")
    }


    override fun onStart() { super.onStart(); Diagnostics.log(this, "MAIN_ON_START id=$activityInstanceId finishing=$isFinishing changingConfig=$isChangingConfigurations") }
    override fun onResume() { super.onResume(); Diagnostics.log(this, "MAIN_ON_RESUME id=$activityInstanceId displayId=${window.decorView.display?.displayId}") }
    override fun onPause() { Diagnostics.log(this, "MAIN_ON_PAUSE id=$activityInstanceId finishing=$isFinishing changingConfig=$isChangingConfigurations"); super.onPause() }
    override fun onStop() { Diagnostics.log(this, "MAIN_ON_STOP id=$activityInstanceId finishing=$isFinishing changingConfig=$isChangingConfigurations"); super.onStop() }
    override fun onRestart() { super.onRestart(); Diagnostics.log(this, "MAIN_ON_RESTART id=$activityInstanceId") }
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); Diagnostics.log(this, "MAIN_ON_NEW_INTENT id=$activityInstanceId flags=${intent?.flags}") }

    private fun rear() = dm.displays.firstOrNull {
        it.displayId != 0 && it.mode.physicalWidth <= 300 && it.mode.physicalHeight <= 500
    } ?: dm.displays.firstOrNull { it.displayId != 0 }

    private fun startCapture() {
        if (rear() == null) {
            status.text = "Rear display not found. Scan displays."
            return
        }
        Diagnostics.log(this, "USER_REQUESTED_CAPTURE")
        @Suppress("DEPRECATION")
        startActivityForResult(pm.createScreenCaptureIntent(), 77)
    }

    @Deprecated("Screen capture consent callback")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 77) return
        if (resultCode != RESULT_OK || data == null) {
            status.text = "Screen capture permission denied."
            Diagnostics.log(this, "CAPTURE_PERMISSION_DENIED resultCode=$resultCode")
            return
        }
        Diagnostics.log(this, "CAPTURE_PERMISSION_GRANTED")
        openRear(resultCode, data)
    }

    private fun openRear(code: Int?, data: Intent?) {
        val display = rear() ?: run {
            status.text = "Secondary display not found."
            return
        }
        try {
            // Existing Xiaomi rear-display wake-up request.
            val parcel = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                parcel.writeInterfaceToken("android.os.IPowerManager")
                parcel.writeStrongBinder(Binder())
                parcel.writeLong(SystemClock.uptimeMillis())
                parcel.writeInt(1)
                parcel.writeString("CAMERA_CALL")
                val serviceManager = Class.forName("android.os.ServiceManager")
                val service = serviceManager.getMethod("getService", String::class.java)
                    .invoke(null, "power") as? IBinder
                service?.transact(16777210, parcel, reply, IBinder.FLAG_ONEWAY)
            } catch (_: Exception) {
            } finally {
                parcel.recycle()
                reply.recycle()
            }

            val intent = Intent(this, RearTestActivity::class.java)
            if (code != null && data != null) {
                intent.putExtra(ScreenMirrorService.EXTRA_CAPTURE_CODE, code)
                intent.putExtra(ScreenMirrorService.EXTRA_CAPTURE_DATA, data)
            }
            startActivity(intent, ActivityOptions.makeBasic()
                .apply { launchDisplayId = display.displayId }.toBundle())
            status.text = if (code == null) "Rear test opened on display ${display.displayId}"
                else "Mirror requested on display ${display.displayId}"
            Diagnostics.log(this, "LAUNCH_REAR_ACTIVITY displayId=${display.displayId} captureRequested=${code != null}")
        } catch (error: Exception) {
            status.text = "Launch failed: ${error.javaClass.simpleName}: ${error.message}"
            Diagnostics.log(this, "LAUNCH_REAR_EXCEPTION ${error.javaClass.name}: ${error.message}")
        }
    }

    private fun scan() {
        report.text = dm.displays.joinToString("\n\n") { display ->
            "Display ID ${display.displayId}: ${display.name}\n" +
                "${display.mode.physicalWidth}×${display.mode.physicalHeight} • ${display.refreshRate} Hz • ${display.state}"
        }
        dm.displays.forEach { display ->
            Diagnostics.log(this,
                "DISPLAY id=${display.displayId} name=${display.name} mode=${display.mode.physicalWidth}x${display.mode.physicalHeight} " +
                    "refreshRate=${display.refreshRate} supportedModes=${display.supportedModes.joinToString { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate}" }}")
        }
    }

    private fun installFrameDiagnostics() {
        if (Build.VERSION.SDK_INT < 24) return
        frameListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val totalMs = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0
            val refresh = window.decorView.display?.refreshRate?.takeIf { it > 1f } ?: 60f
            val frameBudgetMs = 1000.0 / refresh
            frameSamples++
            if (totalMs > frameBudgetMs * 1.5) jankyFrames++
            if (totalMs > maxFrameMs) maxFrameMs = totalMs
            if (frameSamples >= 120) {
                Diagnostics.log(this,
                    "MAIN_WINDOW_FRAMES samples=$frameSamples slowFrames=$jankyFrames refreshRateHz=$refresh " +
                        "frameBudgetMs=$frameBudgetMs maxFrameMs=$maxFrameMs")
                frameSamples = 0
                jankyFrames = 0
                maxFrameMs = 0.0
            }
        }
        frameListener?.let { listener ->
            window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
        }
    }

    private fun shareDiagnostics() {
        val header = "MiRear diagnostics | device=${Build.MANUFACTURER} ${Build.MODEL} | Android=${Build.VERSION.RELEASE} SDK=${Build.VERSION.SDK_INT}\n"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MiRear performance diagnostics")
            putExtra(Intent.EXTRA_TEXT, header + Diagnostics.read(this@MainActivity))
        }
        startActivity(Intent.createChooser(send, "Send MiRear diagnostics"))
    }

    override fun onDestroy() {
        Diagnostics.log(this, "MAIN_ON_DESTROY id=$activityInstanceId finishing=$isFinishing changingConfig=$isChangingConfigurations")
        frameListener?.let { window.removeOnFrameMetricsAvailableListener(it) }
        super.onDestroy()
    }
}

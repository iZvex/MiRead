package ru.mirear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Direct SurfaceView output to avoid the extra TextureView composition step.
 * The buffer dimensions are taken from the rear display's physical mode.
 */
class RearTestActivity : Activity(), SurfaceHolder.Callback {
    private var serviceStarted = false
    private var captureData: Intent? = null
    private var captureCode = 0
    private lateinit var message: TextView
    private lateinit var surfaceView: SurfaceView
    private var receiverRegistered = false
    private var mirrorService: ScreenMirrorService? = null
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? ScreenMirrorService.LocalBinder ?: return
            mirrorService = local.service()
            Diagnostics.log(this@RearTestActivity, "SERVICE_BOUND activity=$activityInstanceId active=${mirrorService?.isCaptureActive()}")
            if (::surfaceView.isInitialized && surfaceView.holder.surface.isValid) mirrorService?.attachSurface(surfaceView.holder.surface)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Diagnostics.log(this@RearTestActivity, "SERVICE_DISCONNECTED activity=$activityInstanceId")
            mirrorService = null
        }
    }
    private var mirrorWidth = DEFAULT_WIDTH
    private var mirrorHeight = DEFAULT_HEIGHT

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ScreenMirrorService.ACTION_STATUS) return
            val state = intent.getStringExtra(ScreenMirrorService.EXTRA_STATE) ?: "INFO"
            val detail = intent.getStringExtra(ScreenMirrorService.EXTRA_DETAIL) ?: ""
            message.text = "MiRear\n\n$state\n\n$detail"
            message.setTextColor(if (state == "ERROR") Color.RED else Color.CYAN)
            message.visibility = if (state == "RUNNING") android.view.View.GONE
                else android.view.View.VISIBLE
        }
    }

    private val activityInstanceId = java.util.UUID.randomUUID().toString().take(8)
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { Diagnostics.log(this@RearTestActivity, "DISPLAY_ADDED activity=$activityInstanceId displayId=$displayId") }
        override fun onDisplayRemoved(displayId: Int) { Diagnostics.log(this@RearTestActivity, "DISPLAY_REMOVED activity=$activityInstanceId displayId=$displayId") }
        override fun onDisplayChanged(displayId: Int) {
            val manager = getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val d = manager.getDisplay(displayId)
            Diagnostics.log(this@RearTestActivity, "DISPLAY_CHANGED activity=$activityInstanceId displayId=$displayId mode=${d?.mode} refresh=${d?.refreshRate} state=${d?.state}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.log(this, "ACTIVITY_ON_CREATE id=$activityInstanceId taskId=$taskId savedState=${savedInstanceState != null} flags=0x${Integer.toHexString(intent.flags)}")
        captureCode = intent.getIntExtra(ScreenMirrorService.EXTRA_CAPTURE_CODE, 0)
        @Suppress("DEPRECATION")
        captureData = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(ScreenMirrorService.EXTRA_CAPTURE_DATA, Intent::class.java)
        else intent.getParcelableExtra(ScreenMirrorService.EXTRA_CAPTURE_DATA)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        resolveRearResolution()
        Diagnostics.log(this, "REAR_ACTIVITY_CREATED rearResolution=${mirrorWidth}x${mirrorHeight} displayId=${window.decorView.display?.displayId} mode=${window.decorView.display?.mode}")

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surfaceView = SurfaceView(this).apply {
            holder.addCallback(this@RearTestActivity)
            holder.setFixedSize(mirrorWidth, mirrorHeight)
        }
        root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))

        message = TextView(this).apply {
            text = if (captureData == null) "MiRear\n\nREAR DISPLAY TEST"
                else "MiRear\n\nPREPARING MIRROR…"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.CYAN)
            setBackgroundColor(0x99000000.toInt())
        }
        root.addView(message, FrameLayout.LayoutParams(-1, -1))

        val filter = IntentFilter(ScreenMirrorService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
        receiverRegistered = true
        setContentView(root)
        (getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager).registerDisplayListener(displayListener, android.os.Handler(mainLooper))
    }

    override fun onStart() {
        super.onStart()
        Diagnostics.log(this, "ACTIVITY_ON_START id=$activityInstanceId finishing=$isFinishing changingConfig=$isChangingConfigurations")
        bindMirrorService()
    }
    override fun onResume() { super.onResume(); Diagnostics.log(this, "ACTIVITY_ON_RESUME id=$activityInstanceId displayId=${window.decorView.display?.displayId} serviceStarted=$serviceStarted surfaceValid=${::surfaceView.isInitialized && surfaceView.holder.surface.isValid}") }
    override fun onPause() { Diagnostics.log(this, "ACTIVITY_ON_PAUSE id=$activityInstanceId finishing=$isFinishing changingConfig=$isChangingConfigurations"); super.onPause() }
    override fun onRestart() { super.onRestart(); Diagnostics.log(this, "ACTIVITY_ON_RESTART id=$activityInstanceId") }
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); Diagnostics.log(this, "ACTIVITY_ON_NEW_INTENT id=$activityInstanceId flags=${intent?.flags}") }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); Diagnostics.log(this, "ACTIVITY_CONFIGURATION_CHANGED id=$activityInstanceId orientation=${newConfig.orientation} densityDpi=${newConfig.densityDpi} screen=${newConfig.screenWidthDp}x${newConfig.screenHeightDp}") }

    private fun resolveRearResolution() {
        val display = window.decorView.display
        val mode = display?.mode
        val w = mode?.physicalWidth ?: 0
        val h = mode?.physicalHeight ?: 0
        if (w > 0 && h > 0) {
            mirrorWidth = w
            mirrorHeight = h
        }
        Diagnostics.log(this, "REAR_DISPLAY_MODE physical=${mirrorWidth}x${mirrorHeight} densityDpi=${resources.displayMetrics.densityDpi} refreshRate=${display?.refreshRate}")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Diagnostics.log(this, "SURFACE_CREATED activity=$activityInstanceId valid=${holder.surface.isValid} view=${surfaceView.width}x${surfaceView.height} buffer=${mirrorWidth}x${mirrorHeight}")
        holder.setFixedSize(mirrorWidth, mirrorHeight)
        if (captureData != null) startMirror() else mirrorService?.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Diagnostics.log(this, "SURFACE_CHANGED activity=$activityInstanceId format=$format view=${width}x${height} buffer=${mirrorWidth}x${mirrorHeight}")
        if (captureData != null && !serviceStarted) startMirror() else if (holder.surface.isValid) mirrorService?.attachSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Diagnostics.log(this, "SURFACE_DESTROYED activity=$activityInstanceId finishing=$isFinishing changingConfig=$isChangingConfigurations")
        mirrorService?.detachSurface(holder.surface)
    }

    private fun bindMirrorService() {
        if (serviceBound) return
        try {
            serviceBound = bindService(Intent(this, ScreenMirrorService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
            Diagnostics.log(this, "SERVICE_BIND_REQUEST result=$serviceBound")
        } catch (error: Exception) {
            Diagnostics.log(this, "SERVICE_BIND_EXCEPTION ${error.javaClass.name}: ${error.message}")
        }
    }

    private fun startMirror() {
        if (serviceStarted || captureData == null) return

        val display = window.decorView.display
        val metrics = android.util.DisplayMetrics()
        display?.getRealMetrics(metrics)
        val densityDpi = metrics.densityDpi.takeIf { it > 0 }
            ?: resources.displayMetrics.densityDpi

        val serviceIntent = Intent(this, ScreenMirrorService::class.java).apply {
            putExtra(ScreenMirrorService.EXTRA_CAPTURE_CODE, captureCode)
            putExtra(ScreenMirrorService.EXTRA_CAPTURE_DATA, captureData)
            putExtra(ScreenMirrorService.EXTRA_WIDTH, mirrorWidth)
            putExtra(ScreenMirrorService.EXTRA_HEIGHT, mirrorHeight)
            putExtra(ScreenMirrorService.EXTRA_DENSITY_DPI, densityDpi)
        }

        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            serviceStarted = true
            bindMirrorService()
            message.text = "MiRear\n\nSTARTING MIRROR…\n\n${mirrorWidth} × ${mirrorHeight}"
            message.visibility = android.view.View.VISIBLE
        } catch (error: Exception) {
            Diagnostics.log(this, "START_MIRROR_EXCEPTION ${error.javaClass.name}: ${error.message}")
            Diagnostics.log(this, android.util.Log.getStackTraceString(error))
            message.text = "MIRROR ERROR\n\n${error.javaClass.simpleName}: ${error.message}"
            message.setTextColor(Color.RED)
            message.visibility = android.view.View.VISIBLE
        }
    }

    override fun onStop() {
        Diagnostics.log(this, "ACTIVITY_ON_STOP id=$activityInstanceId finishing=$isFinishing changingConfig=$isChangingConfigurations")
        if (serviceBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
            serviceBound = false
            mirrorService = null
        }
        super.onStop()
    }

    override fun onDestroy() {
        Diagnostics.log(this, "ACTIVITY_ON_DESTROY id=$activityInstanceId finishing=$isFinishing changingConfig=$isChangingConfigurations serviceStarted=$serviceStarted")
        (getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager).unregisterDisplayListener(displayListener)
        if (receiverRegistered) unregisterReceiver(statusReceiver)
        Diagnostics.log(this, "ACTIVITY_DESTROY_NO_SERVICE_STOP id=$activityInstanceId")
        super.onDestroy()
    }

    companion object {
        private const val DEFAULT_WIDTH = 126
        private const val DEFAULT_HEIGHT = 294
    }
}

package ru.mirear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureCode = intent.getIntExtra(ScreenMirrorService.EXTRA_CAPTURE_CODE, 0)
        @Suppress("DEPRECATION")
        captureData = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(ScreenMirrorService.EXTRA_CAPTURE_DATA, Intent::class.java)
        else intent.getParcelableExtra(ScreenMirrorService.EXTRA_CAPTURE_DATA)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        resolveRearResolution()

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
    }

    private fun resolveRearResolution() {
        val display = window.decorView.display
        val mode = display?.mode
        val w = mode?.physicalWidth ?: 0
        val h = mode?.physicalHeight ?: 0
        if (w > 0 && h > 0) {
            mirrorWidth = w
            mirrorHeight = h
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        holder.setFixedSize(mirrorWidth, mirrorHeight)
        startMirror(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!serviceStarted) startMirror(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopMirrorService()
    }

    private fun startMirror(surface: Surface) {
        if (serviceStarted || captureData == null || !surface.isValid) return

        val display = window.decorView.display
        val metrics = android.util.DisplayMetrics()
        display?.getRealMetrics(metrics)
        val densityDpi = metrics.densityDpi.takeIf { it > 0 }
            ?: resources.displayMetrics.densityDpi

        val serviceIntent = Intent(this, ScreenMirrorService::class.java).apply {
            putExtra(ScreenMirrorService.EXTRA_CAPTURE_CODE, captureCode)
            putExtra(ScreenMirrorService.EXTRA_CAPTURE_DATA, captureData)
            putExtra(ScreenMirrorService.EXTRA_SURFACE, surface)
            putExtra(ScreenMirrorService.EXTRA_WIDTH, mirrorWidth)
            putExtra(ScreenMirrorService.EXTRA_HEIGHT, mirrorHeight)
            putExtra(ScreenMirrorService.EXTRA_DENSITY_DPI, densityDpi)
        }

        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            serviceStarted = true
            message.text = "MiRear\n\nSTARTING MIRROR…\n\n${mirrorWidth} × ${mirrorHeight}"
            message.visibility = android.view.View.VISIBLE
        } catch (error: Exception) {
            message.text = "MIRROR ERROR\n\n${error.javaClass.simpleName}: ${error.message}"
            message.setTextColor(Color.RED)
            message.visibility = android.view.View.VISIBLE
        }
    }

    private fun stopMirrorService() {
        if (!serviceStarted) return
        stopService(Intent(this, ScreenMirrorService::class.java))
        serviceStarted = false
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(statusReceiver)
        stopMirrorService()
        super.onDestroy()
    }

    companion object {
        private const val DEFAULT_WIDTH = 126
        private const val DEFAULT_HEIGHT = 294
    }
}

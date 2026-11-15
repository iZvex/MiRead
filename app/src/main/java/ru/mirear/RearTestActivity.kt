package ru.mirear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView

class RearTestActivity : Activity(), SurfaceHolder.Callback {
    private var serviceStarted = false
    private var captureData: Intent? = null
    private var captureCode = 0
    private lateinit var message: TextView
    private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ScreenMirrorService.ACTION_STATUS) return
            val state = intent.getStringExtra(ScreenMirrorService.EXTRA_STATE) ?: "INFO"
            val detail = intent.getStringExtra(ScreenMirrorService.EXTRA_DETAIL) ?: ""
            message.text = "MiRear\n\n$state\n\n$detail"
            message.setTextColor(if (state == "ERROR") Color.RED else Color.CYAN)
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
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val surface = SurfaceView(this)
        surface.holder.addCallback(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        message = TextView(this).apply {
            text = if (captureData == null) "MiRear\n\nREAR DISPLAY TEST" else "MiRear\n\nPREPARING MIRROR…"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.CYAN)
            setBackgroundColor(0x99000000.toInt())
        }
        root.addView(message, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        val filter = IntentFilter(ScreenMirrorService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(statusReceiver, filter)
        receiverRegistered = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
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
        val rearDisplay = window.decorView.display
        val mode = rearDisplay?.mode
        val width = mode?.physicalWidth?.takeIf { it > 0 } ?: window.decorView.width.coerceAtLeast(1)
        val height = mode?.physicalHeight?.takeIf { it > 0 } ?: window.decorView.height.coerceAtLeast(1)
        val rearMetrics = DisplayMetrics()
        rearDisplay?.getRealMetrics(rearMetrics)
        val densityDpi = rearMetrics.densityDpi.takeIf { it > 0 } ?: resources.displayMetrics.densityDpi
        holderSizeHint(surface, width, height)
        val serviceIntent = Intent(this, ScreenMirrorService::class.java).apply {
            putExtra(ScreenMirrorService.EXTRA_CAPTURE_CODE, captureCode)
            putExtra(ScreenMirrorService.EXTRA_CAPTURE_DATA, captureData)
            putExtra(ScreenMirrorService.EXTRA_SURFACE, surface)
            putExtra(ScreenMirrorService.EXTRA_WIDTH, width)
            putExtra(ScreenMirrorService.EXTRA_HEIGHT, height)
            putExtra(ScreenMirrorService.EXTRA_DENSITY_DPI, densityDpi)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            serviceStarted = true
            message.text = "MiRear\n\nSTARTING MIRROR…\n\n$width × $height"
        } catch (error: Exception) {
            message.text = "MIRROR ERROR\n\n${error.javaClass.simpleName}: ${error.message}"
            message.setTextColor(Color.RED)
        }
    }

    private fun holderSizeHint(surface: Surface, width: Int, height: Int) {
        try {
            surface.setFrameRate(30f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        } catch (_: Exception) { }
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
}

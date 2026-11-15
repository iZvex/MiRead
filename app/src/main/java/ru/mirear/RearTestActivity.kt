package ru.mirear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Renders the MediaProjection virtual display through a TextureView.
 *
 * This follows the output-surface architecture used by the original
 * Mirror2RearUltra project. The virtual display renders into the
 * Surface backed by this TextureView's SurfaceTexture.
 */
class RearTestActivity : Activity(), TextureView.SurfaceTextureListener {
    private var serviceStarted = false
    private var captureData: Intent? = null
    private var captureCode = 0
    private lateinit var message: TextView
    private lateinit var textureView: TextureView
    private var receiverRegistered = false
    private var mirrorSurface: Surface? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ScreenMirrorService.ACTION_STATUS) return
            val state = intent.getStringExtra(ScreenMirrorService.EXTRA_STATE) ?: "INFO"
            val detail = intent.getStringExtra(ScreenMirrorService.EXTRA_DETAIL) ?: ""
            message.text = "MiRear\n\n$state\n\n$detail"
            message.setTextColor(if (state == "ERROR") Color.RED else Color.CYAN)

            // Do not keep a full-screen translucent overlay above the mirror.
            // It would add another composed layer while mirroring is active.
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

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        textureView = TextureView(this).apply {
            surfaceTextureListener = this@RearTestActivity
        }
        root.addView(textureView, FrameLayout.LayoutParams(-1, -1))

        message = TextView(this).apply {
            text = if (captureData == null) "MiRear\n\nREAR DISPLAY TEST"
                else "MiRear\n\nPREPARING MIRROR…"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.CYAN)
            setBackgroundColor(0x99000000.toInt())
        }
        root.addView(message, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        val filter = IntentFilter(ScreenMirrorService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        // The original Mi 11 Ultra implementation uses a square 294 px
        // virtual display and lets the rear panel crop/scale the texture.
        surfaceTexture.setDefaultBufferSize(VIRTUAL_WIDTH, VIRTUAL_HEIGHT)
        mirrorSurface = Surface(surfaceTexture)
        startMirror(mirrorSurface!!)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        // Keep the virtual display buffer fixed; only the view is resized.
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        stopMirrorService()
        mirrorSurface?.release()
        mirrorSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun startMirror(surface: Surface) {
        if (serviceStarted || captureData == null || !surface.isValid) return

        val rearDisplay = window.decorView.display
        val rearMetrics = android.util.DisplayMetrics()
        rearDisplay?.getRealMetrics(rearMetrics)
        val densityDpi = rearMetrics.densityDpi.takeIf { it > 0 }
            ?: resources.displayMetrics.densityDpi

        val serviceIntent = Intent(this, ScreenMirrorService::class.java).apply {
            putExtra(ScreenMirrorService.EXTRA_CAPTURE_CODE, captureCode)
            putExtra(ScreenMirrorService.EXTRA_CAPTURE_DATA, captureData)
            putExtra(ScreenMirrorService.EXTRA_SURFACE, surface)
            putExtra(ScreenMirrorService.EXTRA_WIDTH, VIRTUAL_WIDTH)
            putExtra(ScreenMirrorService.EXTRA_HEIGHT, VIRTUAL_HEIGHT)
            putExtra(ScreenMirrorService.EXTRA_DENSITY_DPI, densityDpi)
        }

        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            serviceStarted = true
            message.text = "MiRear\n\nSTARTING MIRROR…\n\n${VIRTUAL_WIDTH} × ${VIRTUAL_HEIGHT}"
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
        mirrorSurface?.release()
        mirrorSurface = null
        super.onDestroy()
    }

    companion object {
        private const val VIRTUAL_WIDTH = 294
        private const val VIRTUAL_HEIGHT = 294
    }
}

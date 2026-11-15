package ru.mirear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Surface

class ScreenMirrorService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        @Suppress("DEPRECATION")
        val captureData = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_CAPTURE_DATA, Intent::class.java)
        else intent.getParcelableExtra(EXTRA_CAPTURE_DATA)

        @Suppress("DEPRECATION")
        val surface = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_SURFACE, Surface::class.java)
        else intent.getParcelableExtra(EXTRA_SURFACE)

        val resultCode = intent.getIntExtra(EXTRA_CAPTURE_CODE, 0)
        val width = intent.getIntExtra(EXTRA_WIDTH, 126).coerceAtLeast(1)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 294).coerceAtLeast(1)
        if (captureData == null || surface == null || !surface.isValid) {
            report("ERROR", "Capture data or rear display surface is invalid.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startAsForeground()
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, captureData)
            val activeProjection = projection ?: error("Android did not create MediaProjection")

            activeProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    report("STOPPED", "Android stopped screen capture.")
                    releaseAll()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    report("RUNNING", "Captured content resized to $width × $height.")
                }
            }, Handler(Looper.getMainLooper()))

            virtualDisplay = activeProjection.createVirtualDisplay(
                "MiRear-Mirror",
                width,
                height,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                Handler(Looper.getMainLooper())
            ) ?: error("Android failed to create the mirror virtual display")

            report("RUNNING", "Mirroring active: $width × $height.")
        } catch (error: Exception) {
            report("ERROR", "${error.javaClass.simpleName}: ${error.message ?: "Unknown error"}")
            releaseAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val channelId = "mirear_capture"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "MiRear mirroring", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            78,
            Intent(this, ScreenMirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("MiRear screen mirroring")
            .setContentText("The main screen is being mirrored to the rear display.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(77, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(77, notification)
        }
    }

    private fun report(state: String, detail: String) {
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_DETAIL, detail)
        )
    }

    private fun releaseAll() {
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null
    }

    override fun onDestroy() {
        releaseAll()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "ru.mirear.action.STOP_MIRROR"
        const val ACTION_STATUS = "ru.mirear.action.MIRROR_STATUS"
        const val EXTRA_CAPTURE_CODE = "capture_code"
        const val EXTRA_CAPTURE_DATA = "capture_data"
        const val EXTRA_SURFACE = "out_surface"
        const val EXTRA_WIDTH = "out_width"
        const val EXTRA_HEIGHT = "out_height"
        const val EXTRA_STATE = "state"
        const val EXTRA_DETAIL = "detail"
    }
}

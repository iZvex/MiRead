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
import android.os.Binder
import android.os.Looper
import android.view.Surface

class ScreenMirrorService : Service() {
    private val serviceInstanceId = java.util.UUID.randomUUID().toString().take(8)
    private val binder = LocalBinder()
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var currentSurface: Surface? = null

    inner class LocalBinder : Binder() {
        fun service(): ScreenMirrorService = this@ScreenMirrorService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun attachSurface(surface: Surface) {
        if (!surface.isValid) {
            Diagnostics.log(this, "SURFACE_ATTACH_REJECTED invalid=true")
            return
        }
        currentSurface = surface
        val display = virtualDisplay
        if (display == null) {
            Diagnostics.log(this, "SURFACE_ATTACH_WAITING_FOR_VIRTUAL_DISPLAY")
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            surface.setFrameRate(60f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }
        display.setSurface(surface)
        Diagnostics.log(this, "SURFACE_ATTACHED valid=true virtualDisplayActive=true")
        report("RUNNING", "Mirroring active.")
    }

    fun detachSurface(surface: Surface) {
        if (currentSurface !== surface) {
            Diagnostics.log(this, "SURFACE_DETACH_IGNORED surfaceMismatch=true")
            return
        }
        currentSurface = null
        virtualDisplay?.setSurface(null)
        Diagnostics.log(this, "SURFACE_DETACHED virtualDisplayKept=${virtualDisplay != null} projectionKept=${projection != null}")
    }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.log(this, "SERVICE_ON_CREATE id=$serviceInstanceId")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Diagnostics.log(this, "SERVICE_ON_START_COMMAND id=$serviceInstanceId startId=$startId action=${intent?.action} flags=$flags hasCaptureData=${intent?.hasExtra(EXTRA_CAPTURE_DATA)}")
        if (intent?.action == ACTION_STOP) {
            Diagnostics.log(this, "SERVICE_STOP_REQUEST source=explicit_action")
            releaseAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null) {
            Diagnostics.log(this, "SERVICE_START_REJECTED null_intent")
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null && virtualDisplay != null) {
            Diagnostics.log(this, "SERVICE_START_IGNORED alreadyRunning=true")
            return START_NOT_STICKY
        }

        @Suppress("DEPRECATION")
        val captureData = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_CAPTURE_DATA, Intent::class.java)
        else intent.getParcelableExtra(EXTRA_CAPTURE_DATA)
        val resultCode = intent.getIntExtra(EXTRA_CAPTURE_CODE, 0)
        val width = intent.getIntExtra(EXTRA_WIDTH, 126).coerceAtLeast(1)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 294).coerceAtLeast(1)
        val densityDpi = intent.getIntExtra(EXTRA_DENSITY_DPI, 160).coerceAtLeast(1)
        if (captureData == null) {
            report("ERROR", "Screen capture data is missing.")
            Diagnostics.log(this, "SERVICE_START_REJECTED missing_capture_data")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            Diagnostics.log(this, "SERVICE_START request: width=$width height=$height densityDpi=$densityDpi sdk=${Build.VERSION.SDK_INT}")
            startAsForeground()
            Diagnostics.log(this, "FOREGROUND_STARTED type=MEDIA_PROJECTION")
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, captureData)
            val activeProjection = projection ?: error("Android did not create MediaProjection")
            Diagnostics.log(this, "MEDIAPROJECTION_CREATED resultCode=$resultCode")

            activeProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Diagnostics.log(this@ScreenMirrorService, "MEDIAPROJECTION_CALLBACK_STOP")
                    report("STOPPED", "Android stopped screen capture.")
                    releaseAll()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                override fun onCapturedContentResize(width: Int, height: Int) {
                    Diagnostics.log(this@ScreenMirrorService, "CAPTURE_CONTENT_RESIZE width=$width height=$height")
                    report("RUNNING", "Captured content resized to $width × $height.")
                }
            }, Handler(Looper.getMainLooper()))

            virtualDisplay = activeProjection.createVirtualDisplay(
                "MiRear-Mirror",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null,
                null,
                Handler(Looper.getMainLooper())
            ) ?: error("Android failed to create the mirror virtual display")

            Diagnostics.log(this, "VIRTUAL_DISPLAY_CREATED name=MiRear-Mirror width=$width height=$height densityDpi=$densityDpi surface=null")
            report("RUNNING", "Capture active. Waiting for rear surface.")
        } catch (error: Exception) {
            Diagnostics.log(this, "SERVICE_EXCEPTION ${error.javaClass.name}: ${error.message}")
            Diagnostics.log(this, android.util.Log.getStackTraceString(error))
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
            manager.createNotificationChannel(NotificationChannel(channelId, "MiRear mirroring", NotificationManager.IMPORTANCE_LOW))
        }
        val stopIntent = PendingIntent.getService(
            this, 78, Intent(this, ScreenMirrorService::class.java).setAction(ACTION_STOP),
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
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName)
            .putExtra(EXTRA_STATE, state).putExtra(EXTRA_DETAIL, detail))
    }

    private fun releaseAll() {
        Diagnostics.log(this, "RELEASE_ALL virtualDisplay=${virtualDisplay != null} projection=${projection != null} surface=${currentSurface != null}")
        virtualDisplay?.setSurface(null)
        virtualDisplay?.release()
        virtualDisplay = null
        currentSurface = null
        projection?.stop()
        projection = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Diagnostics.log(this, "SERVICE_ON_TASK_REMOVED id=$serviceInstanceId")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Diagnostics.log(this, "SERVICE_ON_DESTROY id=$serviceInstanceId")
        releaseAll()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "ru.mirear.action.STOP_MIRROR"
        const val ACTION_STATUS = "ru.mirear.action.MIRROR_STATUS"
        const val EXTRA_CAPTURE_CODE = "capture_code"
        const val EXTRA_CAPTURE_DATA = "capture_data"
        const val EXTRA_WIDTH = "out_width"
        const val EXTRA_HEIGHT = "out_height"
        const val EXTRA_DENSITY_DPI = "out_density_dpi"
        const val EXTRA_STATE = "state"
        const val EXTRA_DETAIL = "detail"
    }
}

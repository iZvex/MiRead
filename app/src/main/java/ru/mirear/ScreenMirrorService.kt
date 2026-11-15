package ru.mirear
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.*
import android.media.projection.*
import android.os.*
import android.view.Surface
class ScreenMirrorService:Service(){
 private var projection:MediaProjection?=null
 private var vd:VirtualDisplay?=null
 override fun onBind(i:Intent?):IBinder?=null
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
  if(i==null){stopSelf();return START_NOT_STICKY}
  @Suppress("DEPRECATION")
  val data=if(Build.VERSION.SDK_INT>=33)i.getParcelableExtra("capture_data",Intent::class.java)else i.getParcelableExtra("capture_data")
  @Suppress("DEPRECATION")
  val surface=if(Build.VERSION.SDK_INT>=33)i.getParcelableExtra("out_surface",Surface::class.java)else i.getParcelableExtra("out_surface")
  val code=i.getIntExtra("capture_code",0);val w=i.getIntExtra("out_width",126).coerceAtLeast(1);val h=i.getIntExtra("out_height",294).coerceAtLeast(1)
  if(data==null||surface==null||!surface.isValid){stopSelf();return START_NOT_STICKY}
  val channel="mirear_capture"
  if(Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(channel,"MiRear mirroring",NotificationManager.IMPORTANCE_LOW))
  val n=Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("MiRear screen mirroring").setContentText("Screen is being shared to the rear display").setOngoing(true).build()
  if(Build.VERSION.SDK_INT>=29)startForeground(77,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)else startForeground(77,n)
  try{
   val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   projection=mgr.getMediaProjection(code,data)
   projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){releaseAll();stopSelf()}},Handler(Looper.getMainLooper()))
   vd=projection?.createVirtualDisplay("MiRear-Rear",w,h,resources.displayMetrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,Handler(Looper.getMainLooper()))
   if(vd==null)stopSelf()
  }catch(e:Exception){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
  return START_NOT_STICKY
 }
 private fun releaseAll(){vd?.release();vd=null;projection?.stop();projection=null}
 override fun onDestroy(){releaseAll();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
}

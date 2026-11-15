package ru.mirear
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.*
class RearTestActivity:Activity(),SurfaceHolder.Callback{
 private var started=false
 private var resultData:Intent?=null
 private var resultCode=0
 private lateinit var message:TextView
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  resultCode=intent.getIntExtra("capture_code",0)
  @Suppress("DEPRECATION")
  resultData=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra("capture_data",Intent::class.java) else intent.getParcelableExtra("capture_data")
  window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK
  val root=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
  val surface=SurfaceView(this);surface.holder.addCallback(this);root.addView(surface,FrameLayout.LayoutParams(-1,-1))
  message=TextView(this).apply{text="MiRear\n\nREAR DISPLAY READY";textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.CYAN);setBackgroundColor(0x99000000.toInt())}
  root.addView(message,FrameLayout.LayoutParams(-1,-1));setContentView(root)
 }
 override fun surfaceCreated(h:SurfaceHolder){startMirror(h.surface)}
 override fun surfaceChanged(h:SurfaceHolder,f:Int,w:Int,hg:Int){if(!started)startMirror(h.surface)}
 override fun surfaceDestroyed(h:SurfaceHolder){stopService(Intent(this,ScreenMirrorService::class.java));started=false}
 private fun startMirror(s:Surface){if(started||resultData==null||!s.isValid)return
  val w=window.decorView.width.coerceAtLeast(1);val h=window.decorView.height.coerceAtLeast(1)
  val i=Intent(this,ScreenMirrorService::class.java).apply{putExtra("capture_code",resultCode);putExtra("capture_data",resultData);putExtra("out_surface",s);putExtra("out_width",w);putExtra("out_height",h)}
  try{if(Build.VERSION.SDK_INT>=26)startForegroundService(i)else startService(i);started=true;message.text="MiRear\n\nSTARTING MIRROR…"}catch(e:Exception){message.text="MIRROR ERROR\n${e.javaClass.simpleName}: ${e.message}"}
 }
}

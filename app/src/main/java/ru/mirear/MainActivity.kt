package ru.mirear
import android.app.*
import android.content.*
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.*
import java.lang.reflect.Method

class MainActivity: Activity() {
 private val dm by lazy { getSystemService(DISPLAY_SERVICE) as DisplayManager }
 private val pm by lazy { getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
 private lateinit var report: TextView
 private lateinit var status: TextView
 private var frameListener: Window.OnFrameMetricsAvailableListener? = null
 private var frameSamples = 0
 private var jankyFrames = 0
 private var maxFrameMs = 0.0
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState); window.statusBarColor=Color.rgb(8,10,20); window.navigationBarColor=Color.BLACK
  val root=LinearLayout(this).apply { orientation=1; setPadding(22,28,22,24); setBackgroundColor(Color.rgb(8,10,20)) }
  val scroll=ScrollView(this); val c=LinearLayout(this).apply { orientation=1 }
  fun label(s:String,size:Float,color:Int,bold:Boolean=false)=TextView(this).apply { text=s; textSize=size; setTextColor(color); if(bold)setTypeface(typeface,1); setPadding(0,8,0,8) }
  fun button(s:String, f:()->Unit)=Button(this).apply { text=s; setTextColor(Color.CYAN); setOnClickListener{f()} }
  c.addView(label("MI REAR DISPLAY",13f,Color.CYAN,true)); c.addView(label("MiRear",36f,Color.WHITE,true))
  c.addView(label("SCREEN MIRRORING • 0.7.1",12f,0xffb74cff.toInt(),true))
  status=label("Ready. Turn on the rear display and start mirroring.",15f,Color.WHITE)
  c.addView(status)
  c.addView(button("START SCREEN MIRRORING"){ startCapture() })
  c.addView(button("STOP MIRRORING"){ stopService(Intent(this,ScreenMirrorService::class.java)); status.text="Stop requested." })
  c.addView(button("OPEN REAR TEST"){ openRear(null,null) })
  c.addView(label("DISPLAY DIAGNOSTICS",13f,Color.CYAN,true))
  report=label("",14f,Color.WHITE); c.addView(report)
  c.addView(button("SCAN DISPLAYS"){scan()})
  c.addView(button("EXPORT DIAGNOSTICS"){shareDiagnostics()})
  c.addView(button("CLEAR DIAGNOSTICS"){Diagnostics.clear(this); status.text="Diagnostics cleared."})
  c.addView(label("Android will ask permission to capture the screen. A persistent system notification appears during mirroring.",13f,0xff9ba5be.toInt()))
  scroll.addView(c); root.addView(scroll); setContentView(root); scan()
  installFrameDiagnostics()
  Diagnostics.log(this, "APP_STARTED sdk=${Build.VERSION.SDK_INT} model=${Build.MANUFACTURER} ${Build.MODEL} appVersion=0.7.6")
 }
 private fun rear()=dm.displays.firstOrNull{it.displayId!=0 && it.mode.physicalWidth<=300 && it.mode.physicalHeight<=500} ?: dm.displays.firstOrNull{it.displayId!=0}
 private fun startCapture(){ if(rear()==null){status.text="Rear display not found. Scan displays.";return}; @Suppress("DEPRECATION") startActivityForResult(pm.createScreenCaptureIntent(),77) }
 @Deprecated("Screen capture consent callback")
 @Suppress("DEPRECATION")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=77)return;if(resultCode!=RESULT_OK||data==null){status.text="Screen capture permission denied.";return};openRear(resultCode,data)}
 private fun openRear(code:Int?,data:Intent?){
  val d=rear() ?: run{status.text="Secondary display not found.";return}
  try{
   val p=Parcel.obtain(); val reply=Parcel.obtain()
   try { p.writeInterfaceToken("android.os.IPowerManager");p.writeStrongBinder(Binder());p.writeLong(SystemClock.uptimeMillis());p.writeInt(1);p.writeString("CAMERA_CALL");val cl=Class.forName("android.os.ServiceManager");val svc=cl.getMethod("getService",String::class.java).invoke(null,"power") as? IBinder;svc?.transact(16777210,p,reply,IBinder.FLAG_ONEWAY) }catch(_:Exception){}finally{p.recycle();reply.recycle()}
   val i=Intent(this,RearTestActivity::class.java)
   if(code!=null&&data!=null){i.putExtra("capture_code",code);i.putExtra("capture_data",data)}
   startActivity(i,ActivityOptions.makeBasic().apply{launchDisplayId=d.displayId}.toBundle())
   status.text=if(code==null)"Rear test opened on display ${d.displayId}" else "Mirror requested on display ${d.displayId}"
  }catch(e:Exception){status.text="Launch failed: ${e.javaClass.simpleName}: ${e.message}"}
 }
 private fun scan(){report.text=dm.displays.joinToString("\n\n"){ "Display ID ${it.displayId}: ${it.name}\n${it.mode.physicalWidth}×${it.mode.physicalHeight} • ${it.state}" }}
}

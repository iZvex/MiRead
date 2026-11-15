package ru.mirear

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class RearTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.setBackgroundColor(Color.BLACK)
        val view = TextView(this).apply {
            text = "MiRear\n\nREAR DISPLAY LINK OK\n\n0.3.0"
            textSize = 18f
            setTextColor(Color.rgb(0, 235, 255))
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }
        setContentView(view)
    }
}

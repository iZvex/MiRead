package ru.mirear

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Diagnostics {
    private const val FILE_NAME = "mirear_diagnostics.log"
    private const val MAX_BYTES = 256 * 1024

    @Synchronized
    fun log(context: Context, message: String) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists() && file.length() > MAX_BYTES) {
                val old = file.readText()
                file.writeText(old.takeLast(MAX_BYTES / 2))
            }
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            file.appendText("$time | ${android.os.SystemClock.elapsedRealtime()}ms | $message\n")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun read(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else "No diagnostics collected yet."
    }

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).writeText("")
    }
}

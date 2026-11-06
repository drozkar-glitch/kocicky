package cz.mares.kocicky

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

actual object PlatformManager {

    private const val PREFS_NAME = "terezciny_kocicky_prefs"
    private const val KEY_STATE_JSON = "saved_cats_state_v1"
    private const val KEY_FAMILY_CODE = "saved_family_code_v1"
    private const val FIREBASE_BASE = "https://mares-games-default-rtdb.europe-west1.firebasedatabase.app"

    private var appCtx: Context? = null
    private var pendingPhotoReceiver: ((String?) -> Unit)? = null
    var photoLauncherAction: (() -> Unit)? = null

    fun initAndroid(context: Context) {
        appCtx = context.applicationContext
    }

    fun deliverPickedPhoto(base64: String?) {
        val cb = pendingPhotoReceiver
        pendingPhotoReceiver = null
        cb?.invoke(base64)
    }

    actual fun loadLocalData(): String? {
        val ctx = appCtx ?: return null
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_STATE_JSON, null)
    }

    actual fun saveLocalData(json: String) {
        val ctx = appCtx ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STATE_JSON, json).apply()
    }

    actual fun loadFamilyCode(): String {
        val ctx = appCtx ?: return "terezka"
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FAMILY_CODE, "terezka") ?: "terezka"
    }

    actual fun saveFamilyCode(code: String) {
        val ctx = appCtx ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FAMILY_CODE, code).apply()
    }

    actual fun syncToFirebase(
        familyCode: String,
        jsonPayload: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val safeCode = URLEncoder.encode(familyCode.ifBlank { "terezka" }, "UTF-8")
                val endpoint = URL("$FIREBASE_BASE/kocicky/$safeCode.json")
                val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonPayload)
                    writer.flush()
                }
                val status = conn.responseCode
                conn.disconnect()
                if (status in 200..299) {
                    mainHandler.post { onResult(true, "OK") }
                } else {
                    mainHandler.post { onResult(false, "HTTP $status") }
                }
            } catch (e: Exception) {
                mainHandler.post { onResult(false, "Offline") }
            }
        }.start()
    }

    actual fun fetchFromFirebase(
        familyCode: String,
        onResult: (Boolean, String?, String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val safeCode = URLEncoder.encode(familyCode.ifBlank { "terezka" }, "UTF-8")
                val endpoint = URL("$FIREBASE_BASE/kocicky/$safeCode.json")
                val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val status = conn.responseCode
                if (status in 200..299) {
                    val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                        it.readText()
                    }
                    conn.disconnect()
                    mainHandler.post { onResult(true, text, "OK") }
                } else {
                    conn.disconnect()
                    mainHandler.post { onResult(false, null, "HTTP $status") }
                }
            } catch (e: Exception) {
                mainHandler.post { onResult(false, null, "Offline") }
            }
        }.start()
    }

    actual fun pickPhotoFromGallery(onPhotoSelected: (String?) -> Unit) {
        pendingPhotoReceiver = onPhotoSelected
        val launcher = photoLauncherAction
        if (launcher != null) {
            launcher.invoke()
        } else {
            onPhotoSelected(null)
        }
    }

    actual fun decodeBase64ToBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        return try {
            val rawBytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            bmp?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    actual fun scheduleMedicationNotification(
        id: String,
        catName: String,
        medName: String,
        dosage: String,
        timeHhMm: String
    ) {
        val ctx = appCtx ?: return
        try {
            val parts = timeHhMm.split(":")
            val hour = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(0, 23) ?: 8
            val minute = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(0, 59) ?: 0

            val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val reqCode = id.hashCode()

            val intent = Intent(ctx, NotificationReceiver::class.java).apply {
                putExtra("KOCICKY_TITLE", "💊 Lék pro kočičku: $catName")
                putExtra("KOCICKY_MESSAGE", "$medName – $dosage (čas $timeHhMm)")
                putExtra("KOCICKY_ID", reqCode)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pending = PendingIntent.getBroadcast(ctx, reqCode, intent, flags)

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pending
            )
        } catch (_: Exception) {
        }
    }

    actual fun cancelMedicationNotification(id: String) {
        val ctx = appCtx ?: return
        try {
            val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, NotificationReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pending = PendingIntent.getBroadcast(ctx, id.hashCode(), intent, flags)
            alarmManager.cancel(pending)
        } catch (_: Exception) {
        }
    }

    actual fun sendImmediateTestNotification(title: String, message: String) {
        val ctx = appCtx ?: return
        NotificationReceiver.showCatNotification(
            context = ctx,
            notifId = (System.currentTimeMillis() % 10000).toInt(),
            title = title,
            message = message
        )
    }

    actual fun currentDateString(): String {
        val cal = Calendar.getInstance()
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)
        return "$d. $m. $y"
    }

    actual fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}

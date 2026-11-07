package cz.mares.kocicky

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

actual object PlatformManager {
    private const val PREFS_NAME = "terezciny_kocicky_prefs"
    private const val KEY_STATE_JSON = "cat_state_json"
    private const val KEY_FAMILY_CODE = "family_code"
    private const val KEY_FAMILY_PIN = "family_pin"
    const val CHANNEL_ID = "kocicky_meds_channel"

    private const val FIREBASE_BASE_URL =
        "https://mares-games-default-rtdb.europe-west1.firebasedatabase.app"

    private var appContext: Context? = null
    var photoLauncherAction: (() -> Unit)? = null
    var pendingPhotoReceiver: ((String?) -> Unit)? = null

    fun initAndroid(context: Context) {
        appContext = context.applicationContext
        createNotificationChannel(context.applicationContext)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Moji mazlíčci – Krmení, léky a péče",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Upozornění na krmení, podání léků a péči o mazlíčky"
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    actual fun currentTimeMillis(): Long = System.currentTimeMillis()

    actual fun currentDateString(): String {
        val cal = Calendar.getInstance()
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)
        return "$d. $m. $y"
    }

    actual fun loadLocalData(): String? {
        val ctx = appContext ?: return null
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_STATE_JSON, null)
    }

    actual fun saveLocalData(json: String) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STATE_JSON, json).apply()
    }

    actual fun loadFamilyCode(): String {
        val ctx = appContext ?: return ""
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FAMILY_CODE, "") ?: ""
    }

    actual fun saveFamilyCode(code: String) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FAMILY_CODE, code).apply()
    }

    actual fun loadFamilyPin(): String {
        val ctx = appContext ?: return ""
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FAMILY_PIN, "") ?: ""
    }

    actual fun saveFamilyPin(pin: String) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FAMILY_PIN, pin).apply()
    }

    private fun buildFirebaseUrl(familyCode: String, familyPin: String): String {
        val cleanCode = familyCode.trim().lowercase().replace(" ", "_")
        val cleanPin = familyPin.trim().replace(" ", "_")
        val encodedKey = URLEncoder.encode("${cleanCode}_${cleanPin}", "UTF-8")
        return "$FIREBASE_BASE_URL/high_scores/kocicky_cloud/$encodedKey/payload.json"
    }

    actual fun syncToFirebase(
        familyCode: String,
        familyPin: String,
        jsonData: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        if (familyCode.isBlank() || familyPin.isBlank()) {
            mainHandler.post { onResult(false, "Chybí účet nebo PIN") }
            return
        }
        Thread {
            try {
                val url = URL(buildFirebaseUrl(familyCode, familyPin))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonData)
                    writer.flush()
                }
                val code = conn.responseCode
                conn.disconnect()
                mainHandler.post {
                    if (code in 200..299) {
                        onResult(true, "OK")
                    } else {
                        onResult(false, "HTTP $code")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { onResult(false, e.message ?: "Chyba sítě") }
            }
        }.start()
    }

    actual fun fetchFromFirebase(
        familyCode: String,
        familyPin: String,
        onResult: (Boolean, String?, String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        if (familyCode.isBlank() || familyPin.isBlank()) {
            mainHandler.post { onResult(false, null, "Chybí účet nebo PIN") }
            return
        }
        Thread {
            try {
                val url = URL(buildFirebaseUrl(familyCode, familyPin))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                        it.readText()
                    }
                    conn.disconnect()
                    mainHandler.post { onResult(true, body, "OK") }
                } else {
                    conn.disconnect()
                    mainHandler.post { onResult(false, null, "HTTP $code") }
                }
            } catch (e: Exception) {
                mainHandler.post { onResult(false, null, e.message ?: "Chyba sítě") }
            }
        }.start()
    }

    actual fun scheduleMedicationNotification(
        id: String,
        catName: String,
        medName: String,
        dosage: String,
        timeHhMm: String
    ) {
        val ctx = appContext ?: return
        val parts = timeHhMm.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

        val intent = Intent(ctx, NotificationReceiver::class.java).apply {
            putExtra("title", "🐾 $catName – $medName")
            putExtra("message", "Čas ($timeHhMm): $dosage")
            putExtra("notifId", id.hashCode())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (_: Exception) {
        }
    }

    actual fun cancelMedicationNotification(id: String) {
        val ctx = appContext ?: return
        val intent = Intent(ctx, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent)
    }

    actual fun sendImmediateTestNotification(title: String, message: String) {
        val ctx = appContext ?: return
        showSystemNotification(ctx, (System.currentTimeMillis() % 100000).toInt(), title, message)
    }

    fun showSystemNotification(context: Context, notifId: Int, title: String, message: String) {
        createNotificationChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            notifId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }

    actual fun pickPhotoFromGallery(onResult: (String?) -> Unit) {
        pendingPhotoReceiver = onResult
        photoLauncherAction?.invoke()
    }

    actual fun decodeBase64ToBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bmp?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "🐾 Moji mazlíčci"
        val message = intent.getStringExtra("message") ?: "Připomínka péče o mazlíčka 💕"
        val notifId = intent.getIntExtra("notifId", 1001)
        PlatformManager.showSystemNotification(context, notifId, title, message)
    }
}

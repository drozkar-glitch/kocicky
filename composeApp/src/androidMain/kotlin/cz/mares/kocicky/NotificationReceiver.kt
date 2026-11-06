package cz.mares.kocicky

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("KOCICKY_TITLE") ?: "🐾 Terezčiny Kočičky"
        val message = intent.getStringExtra("KOCICKY_MESSAGE")
            ?: "Čas zkontrolovat léky a stav kočiček Safírky a Zlatína!"
        val notifId = intent.getIntExtra("KOCICKY_ID", 2026)

        showCatNotification(context, notifId, title, message)
    }

    companion object {
        private const val CHANNEL_ID = "terezka_kocicky_channel"

        fun showCatNotification(
            context: Context,
            notifId: Int,
            title: String,
            message: String
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Péče o kočičky a léky",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Upozornění na léky a péči pro Safírku a Zlatína"
                }
                manager.createNotificationChannel(channel)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val contentPending = PendingIntent.getActivity(context, notifId, openIntent, pendingFlags)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPending)
                .build()

            manager.notify(notifId, notification)
        }
    }
}

package cz.mares.kocicky

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "🐾 Moji mazlíčci"
        val message = intent.getStringExtra("message") ?: "Připomínka péče o mazlíčka"
        val notifId = intent.getIntExtra("notifId", 1001)
        val isDaily = intent.getBooleanExtra("isDaily", false)
        val hour = intent.getIntExtra("hour", -1)
        val minute = intent.getIntExtra("minute", -1)

        PlatformManager.showSystemNotification(context, notifId, title, message)

        if (isDaily && hour in 0..23 && minute in 0..59) {
            PlatformManager.scheduleNextDailyExactAlarm(context, notifId, title, message, hour, minute)
        }
    }
}

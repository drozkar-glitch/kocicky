package cz.mares.kocicky

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "🐾 Moji mazlíčci"
        val message = intent.getStringExtra("message") ?: "Připomínka péče o mazlíčka 💕"
        val notifId = intent.getIntExtra("notifId", 1001)
        PlatformManager.showSystemNotification(context, notifId, title, message)
    }
}

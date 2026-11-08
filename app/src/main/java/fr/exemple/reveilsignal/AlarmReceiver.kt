package fr.exemple.reveilsignal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmEngine.ACTION_STOP -> {
                Store.journal(context, "⏹ Arrêtée depuis la notification")
                AlarmEngine.arreter(context)
            }
            AlarmEngine.ACTION_SNOOZE -> AlarmEngine.snoozer(context)
        }
    }
}

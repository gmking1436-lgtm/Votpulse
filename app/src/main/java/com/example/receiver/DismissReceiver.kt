package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarm.AlarmManagerHelper

/**
 * BroadcastReceiver responsible for stopping the 100% full-charge continuous alarm.
 * Invoked by notification actions, dismissal swipes, or system triggers.
 */
class DismissReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS_ALARM = "com.example.voltpulse.ACTION_DISMISS_ALARM"
        private const val TAG = "DismissReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action
        Log.d(TAG, "DismissReceiver triggered with action: $action")

        if (action == ACTION_DISMISS_ALARM || action == Intent.ACTION_DELETE) {
            AlarmManagerHelper.stopAlarm(context)
        }
    }
}

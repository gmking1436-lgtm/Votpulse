package com.example

import android.app.Application
import com.example.alarm.AlarmManagerHelper

class VoltPulseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmManagerHelper.createNotificationChannels(this)
    }
}

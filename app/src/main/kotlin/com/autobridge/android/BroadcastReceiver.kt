package com.autobridge.android

import android.content.Context
import android.content.Intent

class BroadcastReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startService(Intent(context, Service::class.java))
    }
}

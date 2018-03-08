package com.autobridge.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.applicationContext.startService(Intent(this.applicationContext, Service::class.java))
    }
}

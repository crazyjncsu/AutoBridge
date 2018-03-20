package com.autobridge.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MainActivity : Activity() {
    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ifApiLevel(23) { this.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1) }

        this.applicationContext.startService(Intent(this.applicationContext, Service::class.java))
    }
}

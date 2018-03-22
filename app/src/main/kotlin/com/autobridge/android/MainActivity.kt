package com.autobridge.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.FileProvider
import kotlinx.android.synthetic.main.main.*
import java.io.File

class MainActivity : Activity() {
    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ifApiLevel(23) { this.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1) }

        this.applicationContext.startService(Intent(this.applicationContext, Service::class.java))

        this.setContentView(R.layout.main)

        floatingActionButton2.setOnClickListener { test() }
    }

    fun test() {
        this.stopService(Intent(this, Service::class.java))
        this.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                        .setType("*/*")
                        .putExtra(
                                Intent.EXTRA_STREAM,
                                FileProvider.getUriForFile(
                                        this.applicationContext,
                                        this.javaClass.`package`.name,
                                        File(this.filesDir, com.autobridge.android.STATE_FILE_NAME)
                                )
                        ),
                "Export"
        ))
    }
}

package com.autobridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.util.Log
import android.widget.TextView
import kotlinx.android.synthetic.main.main.*
import java.io.File

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            this.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA), 1)

        this.applicationContext.startService(Intent(this.applicationContext, Service::class.java))

        this.setContentView(R.layout.main)

        if (this.intent.action == Intent.ACTION_SEND) {
            val uri = this.intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

            this.contentResolver.openInputStream(uri).use { fromStream ->
                File(this.filesDir, CONFIGURATION_FILE_NAME).outputStream().use { toStream ->
                    fromStream.copyTo(toStream)
                }
            }

            Log.i(TAG, "URI: " + uri.toString())
        }

        val file = File(this.filesDir, CONFIGURATION_FILE_NAME)

        if (file.exists())
            editText.setText(file.readText(), TextView.BufferType.EDITABLE)

        exportButton.setOnClickListener {
            this.stopService(Intent(this, Service::class.java))
            this.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                            .setType("*/*")
                            .putExtra(
                                    Intent.EXTRA_STREAM,
                                    FileProvider.getUriForFile(
                                            this.applicationContext,
                                            this.javaClass.`package`.name,
                                            File(this.filesDir, STATE_FILE_NAME)
                                    )
                            ),
                    "Export"
            ))
        }

        saveButton.setOnClickListener {
            // Log.v(TAG, editText.text.toString())
            File(this.filesDir, CONFIGURATION_FILE_NAME).writeText(editText.text.toString())
        }
    }
}

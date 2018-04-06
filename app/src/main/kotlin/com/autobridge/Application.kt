package com.autobridge

import android.app.Application
import android.databinding.ObservableArrayList
import android.os.Handler

class Application : Application() {
    val handler = Handler()
    val logEntries = ObservableArrayList<LogEntry>()

    fun addLogEntry(entry: LogEntry) {
        this.handler.post {
            this.logEntries.add(entry)
        }
    }
}
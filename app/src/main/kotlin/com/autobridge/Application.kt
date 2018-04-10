package com.autobridge

import android.app.Application
import android.databinding.ObservableArrayList
import android.os.Handler

class Application : Application() {
    val handler = Handler()
    val logEntries = ObservableArrayListEx<LogEntry>()

    fun addLogEntry(entry: LogEntry) {
        this.handler.post {
            this.logEntries.add(entry)

            if (this.logEntries.size > 1000)
                this.logEntries.removeRangeEx(0, 100)
        }
    }
}

class ObservableArrayListEx<T> : ObservableArrayList<T>() {
    fun removeRangeEx(from: Int, to: Int) = this.removeRange(from, to)
}
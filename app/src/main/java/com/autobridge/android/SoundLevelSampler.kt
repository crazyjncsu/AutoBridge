package com.autobridge.android

import android.content.Context
import android.media.MediaRecorder

class SoundLevelSampler {
    companion object {
        val instance = SoundLevelSampler()
    }

    private val syncLock = java.lang.Object()
    private val listeners = ArrayList<Listener>()
    private val mediaRecorder = MediaRecorder()

    private val thread = Thread(object : Runnable {
        override fun run() {
            synchronized(this@SoundLevelSampler.syncLock) {
                while (this@SoundLevelSampler.listeners.count() != 0) {
                    val spl = Math.round(20 * Math.log10((this@SoundLevelSampler.mediaRecorder.maxAmplitude / 51805.5336) / 0.00002)).toDouble();
                    this@SoundLevelSampler.listeners.forEach { it.onSampleProduced(spl) }
                    this@SoundLevelSampler.syncLock.wait(250);
                }
            }
        }
    })

    fun registerListener(listener: Listener) {
        synchronized(this.syncLock) {
            this.listeners.add(listener);

            if (this.listeners.count() == 1) {
                this.mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                this.mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
                this.mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                this.mediaRecorder.setOutputFile("/dev/null")
                this.mediaRecorder.prepare()
                this.mediaRecorder.start()

                this.thread.start()
            }
        }
    }

    fun unregisterListener(listener: Listener) {
        synchronized(this.syncLock) {
            this.listeners.remove(listener)
            this.syncLock.notifyAll()
        }
    }

    interface Listener {
        fun onSampleProduced(sampleValue: Double)
    }
}

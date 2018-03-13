package com.autobridge.android

import android.media.MediaRecorder

class SoundLevelSampler {
    private val mediaRecorder = MediaRecorder()

    fun start() {
        this.mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        this.mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
        this.mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
        this.mediaRecorder.setOutputFile("/dev/null")
        this.mediaRecorder.prepare()
        this.mediaRecorder.start()
    }

    fun stop() {
        this.mediaRecorder.stop()
    }

    fun sampleMaxAmplitude(): Double = 20 * Math.log10(this.mediaRecorder.maxAmplitude.toDouble() / 32767)
}


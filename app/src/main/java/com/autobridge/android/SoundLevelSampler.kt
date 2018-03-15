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

    fun sampleMaxAmplitude() = Math.round(20 * Math.log10((this.mediaRecorder.maxAmplitude / 51805.5336) / 0.00002)).toDouble()
}

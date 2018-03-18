package com.autobridge.android.source

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.autobridge.android.*

class OnboardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : ConfigurationDeviceSourceRuntime(parameters, listener) {
    protected override fun createDeviceRuntime(deviceID: String, deviceType: String?, parameters: DeviceRuntimeParameters, listener: DeviceRuntime.Listener): DeviceRuntime =
            when (deviceID) {
                "speechSynthesizer" -> SpeechSynthesizerRuntime(parameters, listener)

                "flashlight" -> FlashlightRuntime(parameters, listener)

                "soundPressureLevelSensor" -> object : SoundAmplitudeSensorRuntime<Double>(parameters, listener, DeviceType.SOUND_PRESSURE_LEVEL_SENSOR, 0.0) {
                    override fun onSampleProduced(sampleValue: Double) = this.onValueSampled(sampleValue)
                }

                "soundSensor" -> object : SoundAmplitudeSensorRuntime<Boolean>(parameters, listener, DeviceType.SOUND_SENSOR, false) {
                    private val threshold = this.parameters.configuration.getDouble("threshold")
                    override fun onSampleProduced(sampleValue: Double) = this.onValueSampled(sampleValue > this.threshold)
                }

                "atmosphericPressureSensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.ATMOSPHERIC_PRESSURE_SENSOR, Sensor.TYPE_PRESSURE)

                "relativeHumiditySensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.HUMIDITY_SENSOR, 12) // Sensor.TYPE_RELATIVE_HUMIDITY won't compile

                "illuminanceSensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.LIGHT_SENSOR, Sensor.TYPE_LIGHT)

                "accelerationSensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.ACCELERATION_SENSOR, Sensor.TYPE_LINEAR_ACCELERATION)

                "frontCamera" -> CameraRuntime(parameters, listener, true)

                "backCamera" -> CameraRuntime(parameters, listener, false)

                else -> throw IllegalArgumentException()
            }
}

class CameraRuntime(parameters: DeviceRuntimeParameters, listener: Listener, val frontOrBack: Boolean) : DeviceRuntime(parameters, listener, DeviceType.CAMERA) {
    override fun startDiscoverState() {}

    override fun startSetState(propertyName: String, propertyValue: String) {
        if (propertyValue == "")
            Log.v("Camera", "Take Picture")
    }
}

class SpeechSynthesizerRuntime(parameters: DeviceRuntimeParameters, listener: Listener) : DeviceRuntime(parameters, listener, DeviceType.SPEECH_SYNTHESIZER), TextToSpeech.OnInitListener {
    private lateinit var textToSpeech: TextToSpeech

    override fun startOrStop(startOrStop: Boolean, context: Context) {
        if (startOrStop)
            this.textToSpeech = TextToSpeech(context, this)
        else
            this.textToSpeech.shutdown()

    }

    override fun startDiscoverState() {}

    override fun startSetState(propertyName: String, propertyValue: String) {
        @Suppress("DEPRECATION")
        this.textToSpeech.speak(propertyValue, TextToSpeech.QUEUE_ADD, null)
        this.listener.onStateDiscovered(this, propertyName, propertyValue)
    }

    @Suppress("DEPRECATION")
    override fun onInit(p0: Int) {
        this.textToSpeech.setSpeechRate(0.8f)

        this.textToSpeech.setOnUtteranceCompletedListener(object : TextToSpeech.OnUtteranceCompletedListener {
            override fun onUtteranceCompleted(p0: String?) =
                    this@SpeechSynthesizerRuntime.listener.onStateDiscovered(this@SpeechSynthesizerRuntime, "utterance", "")
        })
    }
}

@Suppress("DEPRECATION")
class FlashlightRuntime(parameters: DeviceRuntimeParameters, listener: Listener) : DeviceRuntime(parameters, listener, DeviceType.LIGHT) {
    //private val surfaceTexture = SurfaceTexture(1)
    //private var camera: Camera? = null
    private lateinit var cameraManager: CameraManager
    private var onOrOff = false

    @SuppressLint("NewApi")
    override fun startOrStop(startOrStop: Boolean, context: Context) {
        ifApiLevel(23) {
            if (startOrStop)
                this.cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        }
    }

    override fun startDiscoverState() {
        asyncTryLog {
            this@FlashlightRuntime.listener.onStateDiscovered(
                    this@FlashlightRuntime,
                    this@FlashlightRuntime.deviceType.resourceTypes[0].propertyNames[0],
                    if (this@FlashlightRuntime.onOrOff) "true" else "false")
        }
    }

    @SuppressLint("NewApi")
    override fun startSetState(propertyName: String, propertyValue: String) {
        asyncTryLog {
            ifApiLevel(23) {
                this@FlashlightRuntime.onOrOff = propertyValue == "true"

                this@FlashlightRuntime.cameraManager.cameraIdList
                        .filter { this@FlashlightRuntime.cameraManager.getCameraCharacteristics(it)[CameraCharacteristics.FLASH_INFO_AVAILABLE] }
                        .forEach { this@FlashlightRuntime.cameraManager.setTorchMode(it, this@FlashlightRuntime.onOrOff) }

                this@FlashlightRuntime.listener.onStateDiscovered(this@FlashlightRuntime, propertyName, if (this@FlashlightRuntime.onOrOff) "true" else "false")
            }
        }
//        if (propertyValue == "true" && this.camera == null) {
//            this.camera = Camera.open()
//            this.camera!!.parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
//            //this.camera!!.setPreviewTexture(this.surfaceTexture)
//            this.camera!!.startPreview()
//        } else if (propertyValue == "false" && this.camera != null) {
//            this.camera!!.stopPreview()
//            this.camera!!.parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
//            this.camera!!.release()
//            this.camera = null
//        }
    }
}

abstract class SensorRuntime<ValueType>(parameters: DeviceRuntimeParameters, listener: Listener, deviceType: DeviceType, var value: ValueType) : DeviceRuntime(parameters, listener, deviceType) {
    private val reportIntervalMillisecondCount = parameters.configuration.optLong("reportIntervalMillisecondCount")
    private val reportPercentageChange = parameters.configuration.optDouble("reportPercentageChange")
    private val reportValueChange = parameters.configuration.optDouble("reportValueChange")
    private var lastReportedDoubleValue = 0.0
    private var lastReportedTickCount = 0L

    protected fun onValueSampled(sampledValue: ValueType) {
        val currentTickCount = System.currentTimeMillis()
        var sampledDoubleValue = this.convertToDouble(sampledValue)

        if (this.lastReportedTickCount == 0L
                || (this.reportIntervalMillisecondCount != 0L && currentTickCount - this.lastReportedTickCount >= this.reportIntervalMillisecondCount)
                || (this.reportPercentageChange != 0.0 && Math.abs((this.lastReportedDoubleValue - sampledDoubleValue) / this.lastReportedDoubleValue) >= this.reportPercentageChange)
                || (this.reportValueChange != 0.0 && Math.abs(this.lastReportedDoubleValue - sampledDoubleValue) >= this.reportValueChange)) {
            this.lastReportedDoubleValue = sampledDoubleValue
            this.lastReportedTickCount = currentTickCount
            this.listener.onStateDiscovered(this, this.deviceType.resourceTypes[0].propertyNames[0], sampledValue.toString())
        }

        this.value = sampledValue
    }

    private fun convertToDouble(value: ValueType): Double {
        return when (value) {
            is Boolean -> if (value) 1.0 else 0.0
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            else -> throw IllegalArgumentException()
        }
    }

    override fun startDiscoverState() {
        this.lastReportedTickCount = 0L
    }

    override fun startSetState(propertyName: String, propertyValue: String) =
            throw IllegalAccessException()
}

abstract class SoundAmplitudeSensorRuntime<ValueType>(parameters: DeviceRuntimeParameters, listener: Listener, deviceType: DeviceType, value: ValueType) : SensorRuntime<ValueType>(parameters, listener, deviceType, value), SoundLevelSampler.Listener {
    override fun startOrStop(startOrStop: Boolean, context: Context) =
            if (startOrStop)
                SoundLevelSampler.instance.registerListener(this)
            else
                SoundLevelSampler.instance.unregisterListener(this)
}

open class HardwareSensorRuntime(parameters: DeviceRuntimeParameters, listener: Listener, deviceType: DeviceType, val sensorType: Int) : SensorRuntime<Double>(parameters, listener, deviceType, 0.0), SensorEventListener {
    override fun startOrStop(startOrStop: Boolean, context: Context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(this.sensorType)

        if (startOrStop)
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        else
            sensorManager.unregisterListener(this, sensor)
    }

    protected open fun adjustValue(value: Double): Double = value

    override fun onSensorChanged(sensorEvent: SensorEvent) =
            this.onValueSampled(this.adjustValue(sensorEvent.values[0].toDouble()))

    override fun onAccuracyChanged(sensor: Sensor, i: Int) = Unit
}

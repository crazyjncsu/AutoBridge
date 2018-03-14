package com.autobridge.android

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.*
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import org.json.JSONObject

class OnboardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceSourceRuntime(parameters, listener), OnboardDeviceRuntime.Listener {
    private val devices = this.parameters.configuration
            .getJSONArray("devices")
            .toJSONObjectSequence()
            .map {
                object {
                    private val id = it.getString("id")
                    val runtime = OnboardDeviceRuntime.createDevice(
                            id,
                            DeviceRuntimeParameters(
                                    this.id,
                                    it.getJSONObject("configuration"),
                                    JSONObject(),
                                    it.getString("name")
                            ),
                            this@OnboardSourceRuntime
                    )
                }
            }
            .toList()

    override fun startOrStop(startOrStop: Boolean, context: Context) =
            this.devices.forEach { it.runtime.startOrStop(startOrStop, context) }

    override fun onStateDiscovered(deviceRuntime: OnboardDeviceRuntime, propertyName: String, propertyValue: String) =
            this.listener.onDeviceStateDiscovered(this, deviceRuntime.parameters.id, propertyName, propertyValue)


    override fun startDiscoverDevices() =
            this.listener.onDevicesDiscovered(
                    this,
                    this.devices
                            .map { DeviceDefinition(it.runtime.parameters.id, it.runtime.deviceType, (it.runtime.parameters as DeviceRuntimeParameters).name) }
                            .toList()
            )

    override fun startDiscoverDeviceState(deviceID: String) =
            this.devices
                    .filter { it.runtime.parameters.id == deviceID }
                    .forEach { it.runtime.startDiscoverState() }


    override fun startSetDeviceState(deviceID: String, propertyName: String, propertyValue: String) =
            this.devices
                    .filter { it.runtime.parameters.id == deviceID }
                    .forEach { it.runtime.startSetState(propertyName, propertyValue) }
}

class DeviceRuntimeParameters(id: String, configuration: JSONObject, state: JSONObject, val name: String) : RuntimeParameters(id, configuration, state)

abstract class OnboardDeviceRuntime(parameters: DeviceRuntimeParameters, val listener: Listener, val deviceType: DeviceType) : RuntimeBase(parameters) {
    companion object {
        fun createDevice(deviceID: String, parameters: DeviceRuntimeParameters, listener: Listener) =
                when (deviceID) {
                    "speechSynthesizer" -> SpeechSynthesizerRuntime(parameters, listener)

                    "flashlight" -> FlashlightRuntime(parameters, listener)

                    "soundPressureLevelSensor" -> object : SoundAmplitudeSensorRuntime<Double>(parameters, listener, DeviceType.SOUND_PRESSURE_LEVEL_SENSOR, 0.0) {
                        override fun onSoundMaxAmplitudeSampled(value: Double) = this.onValueSampled(value)
                    }

                    "soundSensor" -> object : SoundAmplitudeSensorRuntime<Boolean>(parameters, listener, DeviceType.SOUND_SENSOR, false) {
                        private val threshold = this.parameters.configuration.getDouble("threshold")
                        override fun onSoundMaxAmplitudeSampled(value: Double) = this.onValueSampled(value > this.threshold)
                    }

                    "atmosphericPressureSensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.ATMOSPHERIC_PRESSURE_SENSOR, Sensor.TYPE_PRESSURE)

                //"relativeHumiditySensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.HUMIDITY_SENSOR, "humidity", 0.0, Sensor.TYPE_RELATIVE_HUMIDITY)

                    "illuminanceSensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.LIGHT_SENSOR, Sensor.TYPE_LIGHT)

                    "accelerationSensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.ACCELERATION_SENSOR, Sensor.TYPE_LINEAR_ACCELERATION)

                    "frontCamera" -> CameraRuntime(parameters, listener, true)

                    "backCamera" -> CameraRuntime(parameters, listener, false)

                    else -> throw IllegalArgumentException()
                }

    }

    interface Listener {
        fun onStateDiscovered(deviceRuntime: OnboardDeviceRuntime, propertyName: String, propertyValue: String)
    }

    open fun startDiscoverState() {}
    abstract fun startSetState(propertyName: String, propertyValue: String)
}

class CameraRuntime(parameters: DeviceRuntimeParameters, listener: Listener, val frontOrBack: Boolean) : OnboardDeviceRuntime(parameters, listener, DeviceType.CAMERA) {
    override fun startSetState(propertyName: String, propertyValue: String) {
        if (propertyValue == "")
            Log.v("Camera", "Take Picture")
    }
}

class SpeechSynthesizerRuntime(parameters: DeviceRuntimeParameters, listener: Listener) : OnboardDeviceRuntime(parameters, listener, DeviceType.SPEECH_SYNTHESIZER), TextToSpeech.OnInitListener {
    private lateinit var textToSpeech: TextToSpeech

    override fun startOrStop(startOrStop: Boolean, context: Context) {
        if (startOrStop)
            this.textToSpeech = TextToSpeech(context, this)
        else
            this.textToSpeech.shutdown()

    }

    override fun startSetState(propertyName: String, propertyValue: String) {
        @Suppress("DEPRECATION")
        this.textToSpeech.speak(propertyValue, TextToSpeech.QUEUE_ADD, null)
        this.listener.onStateDiscovered(this, propertyName, propertyValue)
    }

    override fun onInit(p0: Int) {
        this.textToSpeech.setSpeechRate(0.8f)

        this.textToSpeech.setOnUtteranceCompletedListener(object : TextToSpeech.OnUtteranceCompletedListener {
            override fun onUtteranceCompleted(p0: String?) =
                    this@SpeechSynthesizerRuntime.listener.onStateDiscovered(this@SpeechSynthesizerRuntime, "utterance", "")
        })
    }
}

@Suppress("DEPRECATION")
class FlashlightRuntime(parameters: DeviceRuntimeParameters, listener: Listener) : OnboardDeviceRuntime(parameters, listener, DeviceType.LIGHT) {
    private val surfaceTexture = SurfaceTexture(1)
    private var camera: Camera? = null

    override fun startDiscoverState() =
            this.listener.onStateDiscovered(this, this.deviceType.resourceTypes[0].propertyNames[0], if (this.camera == null) "false" else "true")

    override fun startSetState(propertyName: String, propertyValue: String) {
        ifApiLevel(23) {
            // TODO camera manager?
        }
        if (propertyValue == "true" && this.camera == null) {
            this.camera = Camera.open()
            this.camera!!.parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            //this.camera!!.setPreviewTexture(this.surfaceTexture)
            this.camera!!.startPreview()
        } else if (propertyValue == "false" && this.camera != null) {
            this.camera!!.stopPreview()
            this.camera!!.parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
            this.camera!!.release()
            this.camera = null
        }

        this.listener.onStateDiscovered(this, propertyName, propertyValue)
    }
}

abstract class SensorRuntime<ValueType>(parameters: DeviceRuntimeParameters, listener: Listener, deviceType: DeviceType, var value: ValueType) : OnboardDeviceRuntime(parameters, listener, deviceType) {
    private val reportIntervalMillisecondCount = parameters.configuration.optLong("reportIntervalMillisecondCount")
    private val reportPercentageChange = parameters.configuration.optDouble("reportPercentageChange")
    private val reportValueChange = parameters.configuration.optDouble("reportValueChange")
    private var lastReportedDoubleValue = 0.0
    private var lastReportedTickCount = 0L

    protected fun onValueSampled(sampledValue: ValueType) {
        val currentTickCount = System.currentTimeMillis()
        var sampledDoubleValue = this.convertToDouble(sampledValue)

        if ((this.reportIntervalMillisecondCount != 0L && currentTickCount - this.lastReportedTickCount > this.reportIntervalMillisecondCount)
                || (this.reportPercentageChange != 0.0 && Math.abs(this.lastReportedDoubleValue / (this.lastReportedDoubleValue - sampledDoubleValue)) > this.reportPercentageChange)
                || (this.reportValueChange != 0.0 && Math.abs(this.lastReportedDoubleValue - sampledDoubleValue) > this.reportValueChange)) {
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
        this.lastReportedDoubleValue = this.convertToDouble(this.value)
        this.lastReportedTickCount = System.currentTimeMillis()
        this.listener.onStateDiscovered(this, this.deviceType.resourceTypes[0].propertyNames[0], this.value.toString())
    }

    override fun startSetState(propertyName: String, propertyValue: String) =
            throw IllegalAccessException()
}

abstract class SoundAmplitudeSensorRuntime<ValueType>(parameters: DeviceRuntimeParameters, listener: Listener, deviceType: DeviceType, value: ValueType) : SensorRuntime<ValueType>(parameters, listener, deviceType, value) {
    private val soundLevelSampler = SoundLevelSampler()
    private var isDeactivating = false

    abstract fun onSoundMaxAmplitudeSampled(value: Double)

    private val thread = Thread(object : Runnable {
        override fun run() {
            while (!this@SoundAmplitudeSensorRuntime.isDeactivating) {
                this@SoundAmplitudeSensorRuntime.onSoundMaxAmplitudeSampled(this@SoundAmplitudeSensorRuntime.soundLevelSampler.sampleMaxAmplitude())
                Thread.sleep(250)
            }
        }
    })

    override fun startOrStop(startOrStop: Boolean, context: Context) {
        if (startOrStop) {
            this.soundLevelSampler.start()
            this.isDeactivating = false
            this.thread.start()
        } else {
            this.isDeactivating = true
            this.thread.join()
            this.soundLevelSampler.stop()
        }
    }
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

    override fun onSensorChanged(sensorEvent: SensorEvent) {
        async(CommonPool) { this@HardwareSensorRuntime.onValueSampled(this@HardwareSensorRuntime.adjustValue(sensorEvent.values[0].toDouble())) }
    }

    override fun onAccuracyChanged(sensor: Sensor, i: Int) = Unit
}

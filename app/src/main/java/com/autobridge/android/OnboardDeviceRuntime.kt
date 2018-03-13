package com.autobridge.android

import android.content.Context
import android.hardware.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

    override fun onStateDiscovered(deviceRuntime: OnboardDeviceRuntime, propertyName: String, propertyValue: String) =
            this.listener.onDeviceStateDiscovered(this, deviceRuntime.parameters.id, propertyName, propertyValue)


    override fun startDiscoverDevices() =
            this.listener.onDevicesDiscovered(
                    this,
                    this.devices
                            .map { DeviceDefinition(it.runtime.parameters.id, it.runtime.deviceType, (it.runtime.parameters as DeviceRuntimeParameters).name) }
                            .toList()
            )

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
                    "speechSynthesizer" -> SpeechSynthesizerDeviceRuntime(parameters, listener)

                    "flashlight" -> FlashlightDeviceRuntime(parameters, listener)

                    "soundPressureLevelSensor" -> object : SoundAmplitudeSensorRuntime<Double>(parameters, listener, DeviceType.SOUND_PRESSURE_LEVEL_SENSOR, "soundPressureLevel", 0.0) {
                        override fun onSoundMaxAmplitudeSampled(value: Double) = this.onValueSampled(value)
                    }

                    "soundSensor" -> object : SoundAmplitudeSensorRuntime<Boolean>(parameters, listener, DeviceType.SOUND_SENSOR, "value", true) {
                        private val threshold = this.parameters.configuration.getDouble("threshold")
                        override fun onSoundMaxAmplitudeSampled(value: Double) = this.onValueSampled(value > this.threshold)
                    }

                    "atmosphericPressureSensor" -> HardwareSensorDeviceRuntime<Double>(parameters, listener, DeviceType.ATMOSPHERIC_PRESSURE_SENSOR, "atmosphericPressure", 0.0, Sensor.TYPE_PRESSURE)

                    "relativeHumiditySensor" -> HardwareSensorDeviceRuntime<Double>(parameters, listener, DeviceType.HUMIDITY_SENSOR, "humidity", 0.0, Sensor.TYPE_RELATIVE_HUMIDITY)

                    "illuminanceSensor" -> HardwareSensorDeviceRuntime<Double>(parameters, listener, DeviceType.LIGHT_SENSOR, "illuminance", 0.0, Sensor.TYPE_LIGHT)

                    "accelerationSensor" -> HardwareSensorDeviceRuntime<Double>(parameters, listener, DeviceType.ACCELERATION_SENSOR, "acceleration", 0.0, Sensor.TYPE_LINEAR_ACCELERATION)

                    "frontCamera" -> CameraDeviceRuntime(parameters, listener, true)

                    "backCamera" -> CameraDeviceRuntime(parameters, listener, false)

                    else -> throw IllegalArgumentException()
                }

    }

    interface Listener {
        fun onStateDiscovered(deviceRuntime: OnboardDeviceRuntime, propertyName: String, propertyValue: String)
    }

    abstract fun startSetState(propertyName: String, propertyValue: String)
}

class CameraDeviceRuntime(parameters: DeviceRuntimeParameters, listener: Listener, val frontOrBack: Boolean) : OnboardDeviceRuntime(parameters, listener, DeviceType.CAMERA) {
    override fun startSetState(propertyName: String, propertyValue: String) {
        // TODO implement
    }
}

class SpeechSynthesizerDeviceRuntime(parameters: DeviceRuntimeParameters, listener: Listener) : OnboardDeviceRuntime(parameters, listener, DeviceType.SPEECH_SYNTHESIZER), TextToSpeech.OnInitListener {
    private val textToSpeech: TextToSpeech = TextToSpeech(null, this)

    override fun startSetState(propertyName: String, propertyValue: String) {
        this.textToSpeech.speak(propertyValue, TextToSpeech.QUEUE_ADD, null)
        this.listener.onStateDiscovered(this, propertyName, propertyValue)
    }

    override fun onInit(p0: Int) {
        this.textToSpeech.setSpeechRate(0.9f)

        this.textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(p0: String?) = this@SpeechSynthesizerDeviceRuntime.listener.onStateDiscovered(this@SpeechSynthesizerDeviceRuntime, "utterance", "")
            override fun onError(p0: String?) {}
            override fun onStart(p0: String?) {}
        })
    }
}

@Suppress("DEPRECATION")
class FlashlightDeviceRuntime(parameters: DeviceRuntimeParameters, listener: Listener) : OnboardDeviceRuntime(parameters, listener, DeviceType.LIGHT) {
    private var camera: Camera? = null

    override fun startSetState(propertyName: String, propertyValue: String) {
        if (propertyValue == "on" && this.camera == null) {
            this.camera = Camera.open()
            this.camera!!.parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            this.camera!!.startPreview()
        } else if (propertyValue == "off" && this.camera != null) {
            this.camera!!.stopPreview()
            this.camera!!.parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
            this.camera = null
        }

        this.listener.onStateDiscovered(this, propertyName, propertyValue)
    }
}

abstract class SensorRuntime<ValueType>(parameters: DeviceRuntimeParameters, listener: Listener, deviceType: DeviceType, val valuePropertyName: String, var value: ValueType) : OnboardDeviceRuntime(parameters, listener, deviceType) {
    protected fun onValueSampled(value: ValueType) {
        this.value = value
        this.listener.onStateDiscovered(this, this.valuePropertyName, value.toString())
    }

    override fun startSetState(propertyName: String, propertyValue: String) =
            throw IllegalAccessException()
}

abstract class SoundAmplitudeSensorRuntime<ValueType>(parameters: DeviceRuntimeParameters, listener: Listener, deviceType: DeviceType, valuePropertyName: String, value: ValueType) : SensorRuntime<ValueType>(parameters, listener, deviceType, valuePropertyName, value) {
    private val soundLevelSampler = SoundLevelSampler()
    private var isDeactivating = false

    abstract fun onSoundMaxAmplitudeSampled(value: Double)

    private val thread = Thread {
        fun run() {
            while (!this.isDeactivating) {
                this.onSoundMaxAmplitudeSampled(this.soundLevelSampler.sampleMaxAmplitude())
                Thread.sleep(250)
            }
        }
    }

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

open class HardwareSensorDeviceRuntime<ValueType>(parameters: DeviceRuntimeParameters, listener: Listener, deviceType: DeviceType, valuePropertyName: String, value: ValueType, val sensorType: Int) : SensorRuntime<ValueType>(parameters, listener, deviceType, valuePropertyName, value), SensorEventListener {
    override fun startOrStop(startOrStop: Boolean, context: Context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(this.sensorType)

        if (startOrStop)
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        else
            sensorManager.unregisterListener(this, sensor)
    }

    protected open fun adjustValue(value: Double): ValueType = value as ValueType

    override fun onSensorChanged(sensorEvent: SensorEvent) =
            this.onValueSampled(this.adjustValue(sensorEvent.values[0].toDouble()))

    override fun onAccuracyChanged(sensor: Sensor, i: Int) = Unit
}

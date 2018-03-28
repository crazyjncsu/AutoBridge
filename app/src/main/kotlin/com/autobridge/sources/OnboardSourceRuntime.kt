package com.autobridge.sources

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import com.android.*
import com.autobridge.*

class OnboardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : ConfigurationDeviceSourceRuntime(parameters, listener) {
    override fun createDeviceRuntime(deviceID: String, deviceType: String?, parameters: DeviceRuntimeParameters, listener: DeviceRuntime.Listener): DeviceRuntime =
            when (deviceID) {
                "speechSynthesizer" -> SpeechSynthesizerRuntime(parameters, listener)

                "flashlight" -> FlashlightDeviceRuntime(parameters, listener)

                "soundPressureLevelSensor" -> object : SoundAmplitudeSensorRuntime<Double>(parameters, listener, DeviceType.SOUND_PRESSURE_LEVEL_SENSOR, 0.0) {
                    override val defaultReportValueChange: Double get() = 40.0
                    override fun onSampleProduced(sampleValue: Double) = this.onValueSampled(sampleValue)
                }

                "soundSensor" -> object : SoundAmplitudeSensorRuntime<Boolean>(parameters, listener, DeviceType.SOUND_SENSOR, false) {
                    private val threshold = this.parameters.configuration.optDouble("threshold", 60.0)
                    override val defaultStickyValue: Double get() = 1.0
                    override val defaultReportValueChange: Double get() = 1.0
                    override fun onSampleProduced(sampleValue: Double) = this.onValueSampled(sampleValue > this.threshold)
                }

                "atmosphericPressureSensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.ATMOSPHERIC_PRESSURE_SENSOR, Sensor.TYPE_PRESSURE)

                "relativeHumiditySensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.HUMIDITY_SENSOR, Sensor.TYPE_RELATIVE_HUMIDITY)

                "illuminanceSensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.LIGHT_SENSOR, Sensor.TYPE_LIGHT)

                "accelerationSensor" -> HardwareSensorRuntime(parameters, listener, DeviceType.ACCELERATION_SENSOR, Sensor.TYPE_LINEAR_ACCELERATION)

                "frontCamera" -> CameraRuntime(parameters, listener, true)

                "backCamera" -> CameraRuntime(parameters, listener, false)

                else -> throw IllegalArgumentException()
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

@RequiresApi(21)
abstract class CameraManagerDeviceRuntime(parameters: DeviceRuntimeParameters, listener: Listener, deviceType: DeviceType) : DeviceRuntime(parameters, listener, deviceType) {
    protected lateinit var cameraManager: CameraManager
        private set

    override fun startOrStop(startOrStop: Boolean, context: Context) {
        if (startOrStop)
            this.cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
}

class CameraRuntime(parameters: DeviceRuntimeParameters, listener: Listener, val frontOrBack: Boolean) : CameraManagerDeviceRuntime(parameters, listener, DeviceType.CAMERA) {
    override fun startDiscoverState() {}

    @SuppressLint("NewApi", "MissingPermission")
    override fun startSetState(propertyName: String, propertyValue: String) {
        asyncTryLog {
            if (propertyValue == "") {
                var i = 0;
                val handler = Handler(Looper.getMainLooper())

                val cameraID = this.cameraManager.cameraIdList
                        .map { Pair(it, this.cameraManager.getCameraCharacteristics(it)) }
                        .filter { (it.second.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) == this.frontOrBack }
                        .map { it.first }
                        .firstOrNull()

                val imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 30)
                var objectsToClose = mutableListOf<AutoCloseable>(imageReader)

                fun cleanup() {
                    synchronized(objectsToClose) {
                        objectsToClose.forEach { it.close() }
                    }
                }

                imageReader.setOnImageAvailableListener(ImageReader.OnImageAvailableListener {
                    it.acquireLatestImage().use {
                        if (i++ > 30) {
                            val bytes = ByteArray(it.planes[0].buffer.remaining())
                            it.planes[0].buffer.get(bytes)
                            this.listener.onStateDiscovered(this, propertyName, Base64.encodeToString(bytes, Base64.DEFAULT))
                            cleanup()
                        }
                    }
                }, handler)

                cameraID.let {
                    this.cameraManager.openCamera(it, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice?) {
                            synchronized(objectsToClose) {
                                objectsToClose.add(camera!!)
                            }

                            camera!!.createCaptureSession(listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
                                override fun onConfigureFailed(session: CameraCaptureSession?) {
                                    Log.i(TAG, "onConfigureFailed")
                                    cleanup()
                                }

                                override fun onConfigured(session: CameraCaptureSession?) {
                                    synchronized(objectsToClose) {
                                        objectsToClose.add(session!!)
                                    }

                                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON) // auto-exposure
                                    builder.addTarget(imageReader.surface)
                                    session!!.setRepeatingRequest(builder.build(), null, handler)
                                }
                            }, handler)
                        }

                        override fun onDisconnected(camera: CameraDevice?) {
                            Log.i(TAG, "onDisconnected")
                            cleanup()
                        }

                        override fun onError(camera: CameraDevice?, error: Int) {
                            Log.i(TAG, "onError")
                            cleanup()
                        }
                    }, handler)
                }
            }
        }
    }
}

class FlashlightDeviceRuntime(parameters: DeviceRuntimeParameters, listener: Listener) : CameraManagerDeviceRuntime(parameters, listener, DeviceType.LIGHT) {
    private var onOrOff = false

    override fun startDiscoverState() {
        asyncTryLog {
            this@FlashlightDeviceRuntime.listener.onStateDiscovered(
                    this@FlashlightDeviceRuntime,
                    this@FlashlightDeviceRuntime.deviceType.resourceTypes[0].propertyNames[0],
                    if (this@FlashlightDeviceRuntime.onOrOff) "true" else "false")
        }
    }

    override fun startSetState(propertyName: String, propertyValue: String) {
        asyncTryLog {
            this@FlashlightDeviceRuntime.onOrOff = propertyValue == "true"

            this@FlashlightDeviceRuntime.cameraManager.cameraIdList
                    .filter { this@FlashlightDeviceRuntime.cameraManager.getCameraCharacteristics(it)[CameraCharacteristics.FLASH_INFO_AVAILABLE] }
                    .forEach { this@FlashlightDeviceRuntime.cameraManager.setTorchMode(it, this@FlashlightDeviceRuntime.onOrOff) }

            this@FlashlightDeviceRuntime.listener.onStateDiscovered(this@FlashlightDeviceRuntime, propertyName, if (this@FlashlightDeviceRuntime.onOrOff) "true" else "false")
        }
    }
}

abstract class SensorRuntime<ValueType>(parameters: DeviceRuntimeParameters, listener: Listener, deviceType: DeviceType, var value: ValueType) : DeviceRuntime(parameters, listener, deviceType) {
    private val reportIntervalMillisecondCount = parameters.configuration.optLong("reportIntervalMillisecondCount", this.defaultReportIntervalMillisecondCount)
    private val reportPercentageChange = parameters.configuration.optDouble("reportPercentageChange", this.defaultReportPercentageChange)
    private val reportValueChange = parameters.configuration.optDouble("reportValueChange", this.defaultReportValueChange)
    private val stickyValue = parameters.configuration.optDouble("stickyValue", this.defaultStickyValue)
    private val stickyMillisecondCount = parameters.configuration.optLong("stickyMillisecondCount", this.defaultStickyMillisecondCount)

    open val defaultReportIntervalMillisecondCount get() = 60_000L
    open val defaultReportPercentageChange get() = 0.0
    open val defaultReportValueChange get() = 0.0
    open val defaultStickyValue get() = Double.MAX_VALUE
    open val defaultStickyMillisecondCount get() = 30_000L

    private var lastReportedDoubleValue = 0.0
    private var lastReportedTickCount = 0L

    protected fun onValueSampled(sampledValue: ValueType) {
        val currentTickCount = System.currentTimeMillis()
        var sampledDoubleValue = this.convertToDouble(sampledValue)

        fun getReportValueAction(): Int {
            if (this.lastReportedTickCount == 0L)
                return 1;

            if (this.lastReportedDoubleValue == this.stickyValue && currentTickCount - lastReportedTickCount <= this.stickyMillisecondCount)
                return 2;

            if ((this.reportIntervalMillisecondCount != 0L && currentTickCount - this.lastReportedTickCount >= this.reportIntervalMillisecondCount)
                    || (this.reportPercentageChange != 0.0 && Math.abs((this.lastReportedDoubleValue - sampledDoubleValue) / this.lastReportedDoubleValue) >= this.reportPercentageChange)
                    || (this.reportValueChange != 0.0 && Math.abs(this.lastReportedDoubleValue - sampledDoubleValue) >= this.reportValueChange))
                return 1;

            return 0;
        }

        when (getReportValueAction()) {
            0 -> {
                this.value = sampledValue
            }
            1 -> {
                this.lastReportedDoubleValue = sampledDoubleValue
                this.lastReportedTickCount = currentTickCount
                this.value = sampledValue
                this.listener.onStateDiscovered(this, this.deviceType.resourceTypes[0].propertyNames[0], sampledValue.toString())
            }
            2 -> {
                if (sampledDoubleValue == this.stickyValue)
                    this.lastReportedTickCount = currentTickCount
            }
        }
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

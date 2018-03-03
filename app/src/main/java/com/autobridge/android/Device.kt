package com.autobridge.android

import android.content.Context
import android.hardware.*
import android.media.MediaRecorder
import kotlin.coroutines.experimental.buildSequence

abstract class Device(val name: String) {
    // flashlight
    // camera take pictures: front and back
    // camera motion sensor: front and back
    // alarm siren
    // tts: speaker and headphones
    // sound spl level
    // sound sensor alert
    // light level
    // light sensor alert

    // contact id sender
    // lutron integration
    open fun executeCommand(name: String, argument: String) {

    }

    companion object {
        fun createDevices(): Sequence<Device> {
            return buildSequence {
                yield(HardwareSensorDevice("accelerometer", "acceleration", Sensor.TYPE_LINEAR_ACCELERATION));
                yield(HardwareSensorDevice("thermometer", "temperature", Sensor.TYPE_AMBIENT_TEMPERATURE));
                yield(HardwareSensorDevice("lightMeter", "illumination", Sensor.TYPE_LIGHT));
                yield(HardwareSensorDevice("humidityMeter", "relativeHumidity", Sensor.TYPE_RELATIVE_HUMIDITY));
                yield(HardwareSensorDevice("barometer", "pressure", Sensor.TYPE_PRESSURE));
                yield(SoundLevelSensorDevice("soundLevelMeter", "soundLevel"));
            }
        }
    }

    internal class LightDevice(name: String): Device(name) {
        
    }

    internal abstract class SensorDevice(name: String) : Device(name) {
        private var isActive: Boolean = false

        fun setActive(context: Context, value: Boolean) {
            if (this.isActive != value)
                this.onActivateOrDeactivate(context, value)

            this.isActive = value
        }

        protected open fun onActivateOrDeactivate(context: Context, value: Boolean) {}
    }

    internal open class ValueSensorDevice(name: String, val valuePropertyName: String) : SensorDevice(name) {
        var value = 0.0
            private set

        protected open fun adjustValue(value: Double): Double {
            return value
        }

        protected fun setNewRawValue(millisecondCount: Long, value: Double) {
            this.value = this.adjustValue(value);
        }
    }

    internal class SoundLevelSensorDevice(name: String, valuePropertyName: String) : ValueSensorDevice(name, valuePropertyName) {
        private val mediaRecorder: MediaRecorder;
        private var isDeactivating = false;

        init {
            this.mediaRecorder = MediaRecorder()
            this.mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            this.mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
            this.mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            this.mediaRecorder.setOutputFile("/dev/null")
        }

        private val thread = Thread {
            fun run() {
                while (!this.isDeactivating) {
                    this.setNewRawValue(java.util.Date().getTime(), this.mediaRecorder.getMaxAmplitude().toDouble());
                    Thread.sleep(250);
                }
            }
        }

        override fun onActivateOrDeactivate(context: Context, value: Boolean) {
            if (value) {
                this.mediaRecorder.prepare()
                this.mediaRecorder.start()
                this.isDeactivating = false;
                this.thread.start();
            } else {
                this.isDeactivating = true;
                this.thread.join();
                this.mediaRecorder.stop();
            }
        }
    }

    internal class HardwareSensorDevice(name: String, valuePropertyName: String, val sensorType: Int) : ValueSensorDevice(name, valuePropertyName), SensorEventListener {
        override fun onActivateOrDeactivate(context: Context, value: Boolean) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sensorManager.getDefaultSensor(this.sensorType);

            if (value)
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            else
                sensorManager.unregisterListener(this, sensor);
        }

        override fun onSensorChanged(sensorEvent: SensorEvent) {
            this.setNewRawValue(sensorEvent.timestamp / 1000000, sensorEvent.values[0].toDouble())
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) = Unit
    }
}

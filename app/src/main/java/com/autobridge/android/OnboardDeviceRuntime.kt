package com.autobridge.android

import android.content.Context
import android.hardware.*
import android.media.MediaRecorder
import kotlin.coroutines.experimental.buildSequence

data class DeviceDefinition(val id: String, val type: DeviceType, val name: String);

class OnboardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceSourceRuntime(parameters, listener) {
    override fun setDeviceState(deviceID: String, propertyName: String, propertyValue: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

abstract class OnboardDeviceRuntime(val name: String) {
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

    companion object {
        fun createDevices(): Sequence<OnboardDeviceRuntime> {
            return buildSequence {
                yield(HardwareSensorDeviceRuntime("accelerometer", "acceleration", Sensor.TYPE_LINEAR_ACCELERATION));
                yield(HardwareSensorDeviceRuntime("thermometer", "temperature", Sensor.TYPE_AMBIENT_TEMPERATURE));
                yield(HardwareSensorDeviceRuntime("lightMeter", "illumination", Sensor.TYPE_LIGHT));
                yield(HardwareSensorDeviceRuntime("humidityMeter", "humidity", Sensor.TYPE_RELATIVE_HUMIDITY));
                yield(HardwareSensorDeviceRuntime("barometer", "pressure", Sensor.TYPE_PRESSURE));
                yield(SoundLevelSensorDeviceRuntime("soundLevelMeter", "soundPressureLevel"));
                yield(SpeechSynthesizerDeviceRuntime("speechSynthesizer"));
            }
        }
    }

    internal class SpeechSynthesizerDeviceRuntime(name: String) : OnboardDeviceRuntime(name) {

    }

    internal class LightDeviceRuntime(name: String) : OnboardDeviceRuntime(name) {

    }
    //private val textToSpeech: TextToSpeech = TextToSpeech(this, null);
    //this.textToSpeech.setSpeechRate(0.9f)
    //this.textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null)

    internal abstract class SensorDeviceRuntime(name: String) : OnboardDeviceRuntime(name) {
        private var isActive: Boolean = false

        fun setActive(context: Context, value: Boolean) {
            if (this.isActive != value)
                this.onActivateOrDeactivate(context, value)

            this.isActive = value
        }

        protected open fun onActivateOrDeactivate(context: Context, value: Boolean) {}
    }

    internal open class ValueSensorDeviceRuntime(name: String, val valuePropertyName: String) : SensorDeviceRuntime(name) {
        var value = 0.0
            private set

        protected open fun adjustValue(value: Double): Double {
            return value
        }

        protected fun setNewRawValue(millisecondCount: Long, value: Double) {
            this.value = this.adjustValue(value);
        }
    }

    internal class SoundLevelSensorDeviceRuntime(name: String, valuePropertyName: String) : ValueSensorDeviceRuntime(name, valuePropertyName) {
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

                    //val max = 20 * Math.log10(this@Service.mediaRecorder.maxAmplitude.toDouble() / 32767)
                    //Log.i("mediarecorder", "amplitude: $max")
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

    internal class HardwareSensorDeviceRuntime(name: String, valuePropertyName: String, val sensorType: Int) : ValueSensorDeviceRuntime(name, valuePropertyName), SensorEventListener {
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

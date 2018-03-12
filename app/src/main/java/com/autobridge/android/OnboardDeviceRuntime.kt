package com.autobridge.android

import android.content.Context
import android.hardware.*
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlin.coroutines.experimental.buildSequence

class OnboardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceSourceRuntime(parameters, listener), OnboardDeviceRuntime.Listener {
    override fun onStateDiscovered(deviceRuntime: OnboardDeviceRuntime, propertyName: String, propertyValue: String) {
        this.listener.onDeviceStateDiscovered(this, deviceRuntime, propertyName, propertyValue)
    }


    override fun startDiscoverDevices() {
        this.listener.onDevicesDiscovered(this, this.createDevices())
    }

    override fun startSetDeviceState(deviceID: String, propertyName: String, propertyValue: String) {

    }

    private fun createDevices(): Sequence<OnboardDeviceRuntime> {
        return buildSequence {
            //            yield(OnboardDeviceRuntime.HardwareSensorDeviceRuntime("accelerometer", "acceleration", Sensor.TYPE_LINEAR_ACCELERATION));
//            yield(OnboardDeviceRuntime.HardwareSensorDeviceRuntime("thermometer", "temperature", Sensor.TYPE_AMBIENT_TEMPERATURE));
//            yield(OnboardDeviceRuntime.HardwareSensorDeviceRuntime("lightMeter", "illumination", Sensor.TYPE_LIGHT));
//            yield(OnboardDeviceRuntime.HardwareSensorDeviceRuntime("humidityMeter", "humidity", Sensor.TYPE_RELATIVE_HUMIDITY));
//            yield(OnboardDeviceRuntime.HardwareSensorDeviceRuntime("barometer", "pressure", Sensor.TYPE_PRESSURE));
//            yield(OnboardDeviceRuntime.SoundLevelSensorDeviceRuntime("soundLevelMeter", "soundPressureLevel"));
            yield(OnboardDeviceRuntime.SpeechSynthesizerDeviceRuntime("speechSynthesizer", this@OnboardSourceRuntime))
        }
    }
}

abstract class OnboardDeviceRuntime(val name: String, val listener: Listener) {
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

    interface Listener {
        fun onStateDiscovered(deviceRuntime: OnboardDeviceRuntime, propertyName: String, propertyValue: String)
    }

    abstract fun startSetState(propertyName: String, propertyValue: String);

    internal class SpeechSynthesizerDeviceRuntime(name: String, listener: Listener) : OnboardDeviceRuntime(name, listener), TextToSpeech.OnInitListener {
        override fun onInit(p0: Int) {
            this.textToSpeech.setSpeechRate(0.9f)
            this.textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(p0: String?) = this@SpeechSynthesizerDeviceRuntime.listener.onStateDiscovered(this@SpeechSynthesizerDeviceRuntime, "utterance", "");
                override fun onError(p0: String?) {}
                override fun onStart(p0: String?) {}

            })
        }

        private val textToSpeech: TextToSpeech = TextToSpeech(null, this);

        override fun startSetState(propertyName: String, propertyValue: String) {
            this.textToSpeech.speak(propertyValue, TextToSpeech.QUEUE_ADD, null)
            this.listener.onStateDiscovered(this, propertyName, propertyValue)
        }
    }

    internal abstract class SensorDeviceRuntime(name: String) : OnboardDeviceRuntime(name) {
        private var isActive: Boolean = false

        fun setActive(context: Context, value: Boolean) {
            if (this.isActive != value)
                this.onActivateOrDeactivate(context, value)

            this.isActive = value
        }

        override fun startSetState(propertyName: String, propertyValue: String) {}

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

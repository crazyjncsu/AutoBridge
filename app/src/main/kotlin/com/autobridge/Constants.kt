package com.autobridge

import android.support.v4.content.FileProvider
import java.nio.charset.Charset
import java.util.concurrent.Executors

const val TAG = "AutoBridge"
const val USB_PERMISSION = "com.android.example.USB_PERMISSION"

val THREAD_POOL = Executors.newCachedThreadPool()

const val CONFIGURATION_FILE_NAME = "configuration.json"
const val STATE_FILE_NAME = "state.json"

const val USB_TIMEOUT = 1_000

var DEFAULT_CHARSET = Charset.forName("UTF-8")

enum class DeviceType(val ocfDeviceType: String, val displayName: String, val resourceTypes: Array<ResourceType>) {
    GARAGE_DOOR_OPENER("com.autobridge.d.garageDoorOpener", "Garage Door Opener", arrayOf(ResourceType.OPENER)),
    DOOR_OPENER("com.autobridge.d.doorOpener", "Door Opener", arrayOf(ResourceType.OPENER)),
    WINDOW_SHADE("com.autobridge.d.windowShade", "Window Shade", arrayOf(ResourceType.OPENER)),
    SPEECH_SYNTHESIZER("com.autobridge.d.speechSynthesizer", "Speech Synthesizer", arrayOf(ResourceType.SPEECH_TTS)),
    CAMERA("com.autobridge.d.camera", "Camera", arrayOf(ResourceType.IMAGE_CAPTURE)),
    LIGHT("com.autobridge.d.light", "Light", arrayOf(ResourceType.BINARY_SWITCH)),
    SIREN("com.autobridge.d.siren", "Siren", arrayOf(ResourceType.BINARY_SWITCH)),
    CONTACT_SENSOR("com.autobridge.d.contactSensor", "Contact Sensor", arrayOf(ResourceType.CONTACT_SENSOR)),
    LIGHT_SENSOR("com.autobridge.d.lightSensor", "Light Sensor", arrayOf(ResourceType.ILLUMINANCE_MEASUREMENT)),
    MOTION_SENSOR("com.autobridge.d.motionSensor", "Motion Sensor", arrayOf()),
    SOUND_SENSOR("com.autobridge.d.soundSensor", "Sound Sensor", arrayOf(ResourceType.SOUND_DETECTOR)),
    SOUND_PRESSURE_LEVEL_SENSOR("com.autobridge.d.soundPressureLevelSensor", "Sound Pressure Level Sensor", arrayOf(ResourceType.SOUND_PRESSURE_LEVEL_MEASUREMENT)),
    ATMOSPHERIC_PRESSURE_SENSOR("", "Atmospheric Pressure Sensor", arrayOf()),
    ACCELERATION_SENSOR("", "Acceleration Sensor", arrayOf()),
    HUMIDITY_SENSOR("", "Humidity Sensor", arrayOf())
}

enum class ResourceType(val ocfResourceType: String, val propertyNames: Array<String>) {
    OPENER("oic.r.opener", arrayOf("openState")),
    SPEECH_TTS("oic.r.speech.tts", arrayOf("utterance")),
    IMAGE_CAPTURE("com.autobridge.r.image.capture", arrayOf("image")),
    BINARY_SWITCH("oic.r.switch.binary", arrayOf("value")),
    ILLUMINANCE_MEASUREMENT("oic.r.sensor.illuminance", arrayOf("illuminance")),
    SOUND_PRESSURE_LEVEL_MEASUREMENT("", arrayOf("soundPressureLevel")),
    SOUND_DETECTOR("", arrayOf("sound")),
    CONTACT_SENSOR("oic.r.sensor.contact", arrayOf("value"))
}

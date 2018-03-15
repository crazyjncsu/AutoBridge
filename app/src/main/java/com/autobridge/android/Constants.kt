package com.autobridge.android

enum class DeviceType(val ocfDeviceType: String, val displayName: String, val resourceTypes: Array<ResourceType>) {
    GARAGE_DOOR_OPENER("com.autobridge.d.garageDoorOpener", "Garage Door Opener", arrayOf(ResourceType.DOOR)),
    SPEECH_SYNTHESIZER("com.autobridge.d.speechSynthesizer", "Speech Synthesizer", arrayOf(ResourceType.SPEECH_TTS)),
    CAMERA("com.autobridge.d.camera", "Camera", arrayOf(ResourceType.IMAGE_CAPTURE)),
    LIGHT("com.autobridge.d.light", "Light", arrayOf(ResourceType.BINARY_SWITCH)),
    LIGHT_SENSOR("com.autobridge.d.lightSensor", "Light Sensor", arrayOf(ResourceType.ILLUMINANCE_MEASUREMENT)),
    MOTION_SENSOR("com.autobridge.d.motionSensor", "Motion Sensor", arrayOf()),
    SOUND_SENSOR("com.autobridge.d.soundSensor", "Sound Sensor", arrayOf(ResourceType.SOUND_DETECTOR)),
    SOUND_PRESSURE_LEVEL_SENSOR("com.autobridge.d.soundPressureLevelSensor", "Sound Pressure Level Sensor", arrayOf(ResourceType.SOUND_PRESSURE_LEVEL_MEASUREMENT)),
    ATMOSPHERIC_PRESSURE_SENSOR("", "Atmospheric Pressure Sensor", arrayOf()),
    ACCELERATION_SENSOR("", "Acceleration Sensor", arrayOf()),
    HUMIDITY_SENSOR("", "Humidity Sensor", arrayOf())
}

enum class ResourceType(val ocfResourceType: String, val propertyNames: Array<String>) {
    DOOR("oic.r.door", arrayOf("openState")),
    SPEECH_TTS("oic.r.speech.tts", arrayOf("utterance")),
    IMAGE_CAPTURE("com.autobridge.r.image.capture", arrayOf("image")),
    BINARY_SWITCH("oic.r.switch.binary", arrayOf("value")),
    ILLUMINANCE_MEASUREMENT("oic.r.sensor.illuminance", arrayOf("illuminance")),
    SOUND_PRESSURE_LEVEL_MEASUREMENT("", arrayOf("soundPressureLevel")),
    SOUND_DETECTOR("", arrayOf("sound"))
}

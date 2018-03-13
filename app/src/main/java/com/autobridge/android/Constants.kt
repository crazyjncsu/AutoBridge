package com.autobridge.android

enum class DeviceType(val ocfDeviceType: String, val displayName: String, val resourceTypes: Array<ResourceType>) {
    GARAGE_DOOR_OPENER("com.autobridge.d.garageDoorOpener", "Garage Door Opener", arrayOf(ResourceType.DOOR)),
    SPEECH_SYNTHESIZER("com.autobridge.d.speechSynthesizer", "Speech Synthesizer", arrayOf(ResourceType.SPEECH_TTS)),
    CAMERA("com.autobridge.d.camera", "Camera", arrayOf(ResourceType.IMAGE_CAPTURE)),
    LIGHT("com.autobridge.d.light", "Light", arrayOf(ResourceType.BINARY_SWITCH)),
    LIGHT_SENSOR("com.autobridge.d.lightSensor", "Light Sensor", arrayOf(ResourceType.ILLUMINANCE_MEASUREMENT)),
    MOTION_SENSOR("com.autobridge.d.motionSensor", "Motion Sensor", arrayOf()),
    SOUND_SENSOR("com.autobridge.d.soundSensor", "Sound Sensor", arrayOf()),
    SOUND_PRESSURE_LEVEL_SENSOR("com.autobridge.d.soundPressureLevelSensor", "Sound Pressure Level Sensor", arrayOf()),
    ATMOSPHERIC_PRESSURE_SENSOR("", "Atmospheric Pressure Sensor", arrayOf()),
    ACCELERATION_SENSOR("", "Acceleration Sensor", arrayOf()),
    HUMIDITY_SENSOR("", "Humidity Sensor", arrayOf())
}

enum class ResourceType(val ocfResourceType: String) {
    DOOR("oic.r.door"),
    SPEECH_TTS("oic.r.speech.tts"),
    IMAGE_CAPTURE("com.autobridge.r.image.capture"),
    BINARY_SWITCH("oic.r.switch.binary"),
    ILLUMINANCE_MEASUREMENT("oic.r.sensor.illuminance"),
}

enum class ResourceTypeProperty(val resourceType: ResourceType, propertyName: String) {
    DOOR_OPEN_STATE(ResourceType.DOOR, "openState"),
    SPEECH_TTS_UTTERANCE(ResourceType.SPEECH_TTS, "utterance"),
    BINARY_SWITCH_VALUE(ResourceType.BINARY_SWITCH, "value"),
    ILLUMINANCE_MEASUREMENT_ILLUMINANCE(ResourceType.ILLUMINANCE_MEASUREMENT, "illuminance")
}

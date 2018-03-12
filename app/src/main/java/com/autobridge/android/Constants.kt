package com.autobridge.android

enum class DeviceType(val ocfDeviceType: String, val resourceTypes: Array<ResourceType>) {
    GARAGE_DOOR_OPENER("com.autobridge.d.garageDoorOpener", arrayOf(ResourceType.DOOR)),
    SPEECH_SYNTHESIZER("com.autobridge.d.speechSynthesizer", arrayOf(ResourceType.SPEECH_TTS))
//    LIGHT("com.autobridge.d.light"),
//    SWITCH("com.autobridge.d.switch"),
//    MOTION_SENSOR("com.autobridge.d.motionSensor"),
//    LIGHT_SENSOR("com.autobridge.d.lightSensor"),
//    SOUND_SENSOR("com.autobridge.d.soundSensor"),
//    SNAPSHOT_CAMERA("com.autobridge.d.snapshotCamera"),
}

enum class ResourceType(val ocfResourceType: String) {
    DOOR("oic.r.door"),
    SPEECH_TTS("oic.r.speech.tts")
    //ILLUMINANCE_SENSSOR("oic.r.sensor.illuminance"),
}

enum class ResourceTypeProperty(val resourceType: ResourceType, propertyName: String) {
    DOOR_OPEN_STATE(ResourceType.DOOR, "openState"),
    SPEECH_TTS_UTTERANCE(ResourceType.SPEECH_TTS, "utterance")
}

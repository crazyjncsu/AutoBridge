package com.autobridge.targets

import com.autobridge.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress

private val standardToSmartThingsMap = mapOf(
        Triple(DeviceType.DOOR_OPENER, "openState", "Open") to Pair("door", "open"),
        Triple(DeviceType.DOOR_OPENER, "openState", "Closed") to Pair("door", "closed"),
        Triple(DeviceType.DOOR_OPENER, "value", "false") to Pair("contact", "open"),
        Triple(DeviceType.DOOR_OPENER, "value", "true") to Pair("contact", "closed"),

        Triple(DeviceType.GARAGE_DOOR_OPENER, "openState", "Open") to Pair("door", "open"),
        Triple(DeviceType.GARAGE_DOOR_OPENER, "openState", "Closed") to Pair("door", "closed"),
        Triple(DeviceType.GARAGE_DOOR_OPENER, "value", "false") to Pair("contact", "open"),
        Triple(DeviceType.GARAGE_DOOR_OPENER, "value", "true") to Pair("contact", "closed"),

        Triple(DeviceType.WINDOW_SHADE, "openState", "Open") to Pair("windowShade", "open"),
        Triple(DeviceType.WINDOW_SHADE, "openState", "Closed") to Pair("windowShade", "closed"),
        Triple(DeviceType.WINDOW_SHADE, "value", "false") to Pair("contact", "open"),
        Triple(DeviceType.WINDOW_SHADE, "value", "true") to Pair("contact", "closed"),

        Triple(DeviceType.LIGHT, "value", "true") to Pair("switch", "on"),
        Triple(DeviceType.LIGHT, "value", "false") to Pair("switch", "off"),

        Triple(DeviceType.SIREN, "value", "true") to Pair("alarm", "siren"),
        Triple(DeviceType.SIREN, "value", "false") to Pair("alarm", "off"),

        Triple(DeviceType.SOUND_SENSOR, "sound", "true") to Pair("sound", "detected"),
        Triple(DeviceType.SOUND_SENSOR, "sound", "false") to Pair("sound", "not detected"),

        Triple(DeviceType.CONTACT_SENSOR, "value", "true") to Pair("contact", "open"),
        Triple(DeviceType.CONTACT_SENSOR, "value", "false") to Pair("contact", "closed")
)

private val smartThingsToStandardMap = standardToSmartThingsMap.entries.associateBy({ it.value }) { it.key }

class SmartThingsTargetRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceTargetRuntime(parameters, listener) {
    override fun processMessage(message: JSONObject) {
        val sourceID = message.getString("sourceID")
        val deviceID = message.getString("deviceID")
        val propertyName = message.optString("propertyName")
        val propertyValue = message.optString("propertyValue")

        if (propertyName.isNullOrEmpty())
            this.listener.onDeviceRefreshRequest(this, sourceID, deviceID)
        else
            smartThingsToStandardMap[Pair(propertyName, propertyValue)].let {
                this.listener.onDeviceStateChangeRequest(
                        this,
                        message.getString("sourceID"),
                        message.getString("deviceID"),
                        it?.second ?: propertyName,
                        it?.third ?: propertyValue
                )
            }
    }

    override fun processMacAddressDiscovered(ipAddress: InetAddress, macAddress: ByteArray) {
        if (macAddress[0].toUnsignedInt() == 0x24 && macAddress[1].toUnsignedInt() == 0xFD && macAddress[2].toUnsignedInt() == 0x5B) { // smartthings has own MAC prefix
            this.parameters.state.put("hubIPAddress", ipAddress.hostAddress)
            this.listener.onRejuvenated(this)
        }
    }

    override fun startSyncSources(sourceIDs: List<String>) {
        this.startPerformSmartThingsRequestWithHandling(
                JSONObject(mapOf(
                        "autoBridgeOperation" to "syncSources",
                        "targetID" to this@SmartThingsTargetRuntime.parameters.id,
                        "sourceIDs" to JSONArray(sourceIDs)
                ))
        )
    }

    override fun startSyncSourceDevices(sourceID: String, devices: List<DeviceDefinition>) {
        this.startPerformSmartThingsRequestWithHandling(
                JSONObject(mapOf(
                        "autoBridgeOperation" to "syncSourceDevices",
                        "targetID" to this@SmartThingsTargetRuntime.parameters.id,
                        "sourceID" to sourceID,
                        "devices" to devices.map {
                            JSONObject(mapOf(
                                    "deviceID" to it.id,
                                    "namespace" to "autobridge/child",
                                    "typeName" to it.type.displayName,
                                    "name" to it.name
                            ))
                        }
                ))
        )
    }

    override fun startSyncDeviceState(sourceID: String, deviceID: String, deviceType: DeviceType, propertyName: String, propertyValue: String) {
        val mappedProperty = standardToSmartThingsMap[Triple(deviceType, propertyName, propertyValue)]

        this.startPerformSmartThingsRequestWithHandling(
                JSONObject(mapOf(
                        "autoBridgeOperation" to "syncDeviceState",
                        "targetID" to this@SmartThingsTargetRuntime.parameters.id,
                        "sourceID" to sourceID,
                        "deviceID" to deviceID,
                        "propertyName" to (mappedProperty?.first ?: propertyName),
                        "propertyValue" to (mappedProperty?.second ?: propertyValue)
                ))
        )
    }

    private fun startPerformSmartThingsRequestWithHandling(bodyObject: JSONObject) {
        async {
            try {
                performJsonHttpRequest(
                        "POST",
                        "http",
                        this.parameters.state.optString("hubIPAddress") + ":39500",
                        "/auto-bridge",
                        bodyObject = bodyObject
                )
            } catch (ex: Exception) { // could we catch more specific exception for this?
                this.listener.onSyncError(this, true)
            }
        }
    }
}

package com.autobridge.android.targets

import com.autobridge.android.*
import org.json.JSONObject
import org.json.JSONArray
import java.net.InetAddress

private val smartThingsToStandardMap = mapOf(
        Pair("door", "open") to Triple(ResourceType.DOOR, "openState", "Open"),
        Pair("door", "closed") to Triple(ResourceType.DOOR, "openState", "Closed"),
        Pair("switch", "on") to Triple(ResourceType.BINARY_SWITCH, "value", "true"),
        Pair("switch", "off") to Triple(ResourceType.BINARY_SWITCH, "value", "false"),
        Pair("sound", "detected") to Triple(ResourceType.SOUND_DETECTOR, "sound", "true"),
        Pair("sound", "not detected") to Triple(ResourceType.SOUND_DETECTOR, "sound", "false"),
        Pair("contact", "open") to Triple(ResourceType.CONTACT_SENSOR, "value", "true"),
        Pair("contact", "closed") to Triple(ResourceType.CONTACT_SENSOR, "value", "false")
)

private val standardToSmartThingsMap = smartThingsToStandardMap.entries.associateBy({ it.value }) { it.key }

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
        val mappedProperty = deviceType.resourceTypes
                .map { standardToSmartThingsMap[Triple(it, propertyName, propertyValue)] }
                .firstOrNull { it != null }

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

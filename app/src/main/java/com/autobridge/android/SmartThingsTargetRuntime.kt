package com.autobridge.android

import org.json.JSONObject
import org.json.JSONArray

private val propertyNameValueToSmartThingsMap = mapOf(
        Pair("openState", "Open") to Pair("door", "open"),
        Pair("openState", "Closed") to Pair("door", "closed"),
        Pair("value", "true") to Pair("switch", "on"),
        Pair("value", "false") to Pair("swtich", "off")
)

private val smartThingsToPropertyNameValueMap = propertyNameValueToSmartThingsMap.entries.associateBy({ it.value }) { it.key }

class SmartThingsTargetRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceTargetRuntime(parameters, listener) {
    override fun processMessage(message: JSONObject) {
        val propertyName = message.getString("propertyName")
        val propertyValue = message.getString("propertyValue")
        val mappedProperty = smartThingsToPropertyNameValueMap[Pair(propertyName, propertyValue)]

        this.listener.onDeviceStateChangeRequest(
                this,
                message.getString("sourceID"),
                message.getString("deviceID"),
                mappedProperty?.first ?: propertyName,
                mappedProperty?.second ?: propertyValue
        )
    }

    override fun syncSources(sourceIDs: List<String>) {
        this.performSmartThingsRequest(
                JSONObject(mapOf(
                        "autoBridgeOperation" to "syncSources",
                        "targetID" to this.parameters.id,
                        "sourceIDs" to JSONArray(sourceIDs)
                ))
        )
    }

    override fun syncSourceDevices(sourceID: String, devices: List<DeviceDefinition>) {
        this.performSmartThingsRequest(
                JSONObject(mapOf(
                        "autoBridgeOperation" to "syncSourceDevices",
                        "targetID" to this.parameters.id,
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

    override fun syncDeviceState(sourceID: String, deviceID: String, propertyName: String, propertyValue: String) {
        val mappedProperty = propertyNameValueToSmartThingsMap[Pair(propertyName, propertyValue)]

        this.performSmartThingsRequest(
                JSONObject(mapOf(
                        "autoBridgeOperation" to "syncDeviceState",
                        "targetID" to this.parameters.id,
                        "sourceID" to sourceID,
                        "deviceID" to deviceID,
                        "propertyName" to (mappedProperty?.first ?: propertyName),
                        "propertyValue" to (mappedProperty?.second ?: propertyValue)
                ))
        )
    }

    private fun performSmartThingsRequest(bodyObject: JSONObject) = performJsonHttpRequest(
            "POST",
            "http",
            this.parameters.state.getString("authority"),
            //"10.0.2.2:1000",
            "/auto-bridge",
            bodyObject = bodyObject
    )
}

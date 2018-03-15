package com.autobridge.android

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import org.json.JSONObject
import org.json.JSONArray

private val propertyNameValueToSmartThingsMap = mapOf(
        Pair("openState", "Open") to Pair("door", "open"),
        Pair("openState", "Closed") to Pair("door", "closed"),
        Pair("value", "true") to Pair("switch", "on"),
        Pair("value", "false") to Pair("switch", "off")
)

private val smartThingsToPropertyNameValueMap = propertyNameValueToSmartThingsMap.entries.associateBy({ it.value }) { it.key }

class SmartThingsTargetRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceTargetRuntime(parameters, listener) {
    override fun processMessage(message: JSONObject) {
        val sourceID = message.getString("sourceID")
        val deviceID = message.getString("deviceID")
        val propertyName = message.optString("propertyName")
        val propertyValue = message.optString("propertyValue")

        if (propertyName.isNullOrEmpty())
            this.listener.onDeviceRefreshRequest(this, sourceID, deviceID)
        else
            smartThingsToPropertyNameValueMap[Pair(propertyName, propertyValue)].let {
                this.listener.onDeviceStateChangeRequest(
                        this,
                        message.getString("sourceID"),
                        message.getString("deviceID"),
                        it?.first ?: propertyName,
                        it?.second ?: propertyValue
                )
            }
    }

    override fun startSyncSources(sourceIDs: List<String>) {
        async(CommonPool) {
            this@SmartThingsTargetRuntime.performSmartThingsRequest(
                    JSONObject(mapOf(
                            "autoBridgeOperation" to "syncSources",
                            "targetID" to this@SmartThingsTargetRuntime.parameters.id,
                            "sourceIDs" to JSONArray(sourceIDs)
                    ))
            )
        }
    }

    override fun startSyncSourceDevices(sourceID: String, devices: List<DeviceDefinition>) {
        async(CommonPool) {
            this@SmartThingsTargetRuntime.performSmartThingsRequest(
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
    }

    override fun startSyncDeviceState(sourceID: String, deviceID: String, propertyName: String, propertyValue: String) {
        async(CommonPool) {
            val mappedProperty = propertyNameValueToSmartThingsMap[Pair(propertyName, propertyValue)]

            this@SmartThingsTargetRuntime.performSmartThingsRequest(
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

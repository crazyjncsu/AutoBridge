package com.autobridge.android

import org.json.JSONObject

class SmartThingsTargetRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceTargetRuntime(parameters, listener) {
    override fun processMessage(message: JSONObject) {
        this.listener.onDeviceStateChangeRequest(
                this,
                message.getString("sourceID"),
                message.getString("deviceID"),
                message.getString("propertyName"),
                message.getString("propertyValue")
        )
    }

    override fun syncDevices(sourceID: String, devices: List<DeviceDefinition>) {
        this.performSmartThingsRequest(
                JSONObject(mapOf(
                        "autoBridgeOperation" to SmartThingsTargetRuntime::syncDevices.name,
                        "targetID" to this.parameters.id,
                        "sourceID" to sourceID,
                        "devices" to devices.map {
                            JSONObject(mapOf(
                                    "deviceID" to it.id,
                                    "ocfDeviceType" to it.ocfDeviceType,
                                    "name" to it.name
                            ))
                        }
                ))
        );
    }

    override fun syncDeviceState(sourceID: String, deviceID: String, propertyName: String, propertyValue: String) {
        this.performSmartThingsRequest(
                JSONObject(mapOf(
                        "autoBridgeOperation" to SmartThingsTargetRuntime::syncDeviceState.name,
                        "targetID" to this.parameters.id,
                        "sourceID" to sourceID,
                        "deviceID" to deviceID,
                        "propertyName" to propertyName,
                        "propertyValue" to propertyValue
                ))
        )
    };

    fun performSmartThingsRequest(bodyObject: JSONObject) = performJsonHttpRequest(
            "POST",
            "http",
            this.parameters.state.getString("authority"),
            //"10.0.2.2:1000",
            "auto-bridge",
            bodyObject = bodyObject
    );
}

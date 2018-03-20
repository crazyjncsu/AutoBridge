package com.autobridge.android.source

import com.autobridge.android.*
import org.json.JSONObject
import java.io.IOException
import java.lang.IllegalArgumentException


class MyQSourceRuntime(parameters: RuntimeParameters, listener: Listener) : PollingDeviceSourceRuntime(parameters, listener) {
    private val applicationID: String = "NWknvuBd7LoFHfXmKNMBcgajXtZEgKUh4V7WNzMidrpUUluDpVYVZx+xT4PCM5Kx"

    override fun startDiscoverDevices(shouldIncludeState: Boolean) {
        asyncTryLog {
            fun getAttributeValueFunc(jsonObject: JSONObject, displayName: String): String = jsonObject
                    .getJSONArray("Attributes")
                    .toJSONObjectSequence()
                    .first { it.getString("AttributeDisplayName") == displayName }
                    .getString("Value")

            val deviceInfos = this@MyQSourceRuntime.performMyQRequestWithLoginHandling("GET", "UserDeviceDetails/Get")
                    .getJSONArray("Devices")
                    .toJSONObjectSequence()
                    .filter { it.getString("MyQDeviceTypeId") == "2" }
                    .map {
                        object {
                            val definition = DeviceDefinition(
                                    it.getString("MyQDeviceId"),
                                    DeviceType.GARAGE_DOOR_OPENER,
                                    getAttributeValueFunc(it, "desc")
                            )

                            val isClosed = getAttributeValueFunc(it, "doorstate") == "2"

                            val state = mapOf(
                                    "openState" to if (isClosed) "Closed" else "Open",
                                    "value" to if (isClosed) "false" else "true"
                            )
                        }
                    }
                    .toList()

            this@MyQSourceRuntime.listener.onDevicesDiscovered(
                    this@MyQSourceRuntime,
                    deviceInfos.map { it.definition }.toList()
            )

            if (shouldIncludeState)
                deviceInfos.forEach { deviceInfo ->
                    deviceInfo.state.forEach {
                        this@MyQSourceRuntime.listener.onDeviceStateDiscovered(this@MyQSourceRuntime, deviceInfo.definition.id, deviceInfo.definition.type, it.key, it.value)
                    }
                }
        }
    }

    override fun poll() = this.startDiscoverDevices(true)

    override fun startDiscoverDeviceState(deviceID: String) = this.startDiscoverDevices(true)

    override fun startSetDeviceState(deviceID: String, propertyName: String, propertyValue: String) {
        val attributeName = if (propertyName == "openState") "desireddoorstate" else throw IllegalArgumentException()
        val attributeValue = if (propertyValue == "Open") "1" else if (propertyValue == "Closed") "0" else throw IllegalArgumentException()

        this.performMyQRequestWithLoginHandling(
                "PUT",
                "DeviceAttribute/PutDeviceAttribute",
                mapOf(
                        "MyQDeviceId" to deviceID,
                        "AttributeName" to attributeName,
                        "AttributeValue" to attributeValue
                )
        )
    }

    private fun performMyQRequestWithLoginHandling(
            method: String,
            pathPart: String,
            bodyMap: Map<String, String>? = null
    ): JSONObject {
        var attemptCount = 0

        while (true) {
            val responseObject = this.performMyQRequest(method, pathPart, bodyMap)

            if (responseObject.optString("error") != "-33336" && responseObject.optString("ReturnCode") != "216")
                return responseObject

            if (attemptCount++ > 4)
                throw IOException()

            val loginResponseObject = this.performMyQRequest(
                    "POST",
                    "User/Validate",
                    mapOf(
                            "username" to this.parameters.configuration.getString("username"),
                            "password" to this.parameters.configuration.getString("password")
                    )
            )

            this.parameters.state.put("securityToken", loginResponseObject.getString("SecurityToken"))

            Thread.sleep(4000) // TODO let's try to see if necessary?
        }
    }

    private fun performMyQRequest(
            method: String,
            pathPart: String,
            bodyMap: Map<String, String>? = null
    ) = performJsonHttpRequest(
            method,
            "https",
            "myqexternal.myqdevice.com",
            //"10.0.2.2:1000",
            "/api/v4/$pathPart",
            mapOf(
                    "User-Agent" to "Chamberlain/3.73",
                    "BrandId" to "2",
                    "ApiVersion" to "4.1",
                    "Culture" to "en",
                    "MyQApplicationId" to applicationID,
                    "SecurityToken" to this.parameters.state.optString("securityToken", "")
            ),
            mapOf(),
            bodyMap?.let { JSONObject(it) }
    )
}

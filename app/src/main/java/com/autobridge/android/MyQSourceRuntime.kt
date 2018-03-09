package com.autobridge.android

import android.util.Log
import org.json.JSONObject
import java.lang.IllegalArgumentException


class MyQSourceRuntime(parameters: RuntimeParameters, listener: Listener) : PollingDeviceSourceRuntime(parameters, listener) {
    private val applicationID: String = "NWknvuBd7LoFHfXmKNMBcgajXtZEgKUh4V7WNzMidrpUUluDpVYVZx+xT4PCM5Kx";

    override fun poll() {
        fun getAttributeValueFunc(jsonObject: JSONObject, displayName: String): String = jsonObject
                .getJSONArray("Attributes")
                .toJSONObjectSequence()
                .first { it.getString("AttributeDisplayName") == "desc" }
                .getString("Value");

        val deviceInfos = this.performMyQRequestWithLoginHandling("GET", "UserDeviceDetails/Get")
                .getJSONArray("Devices")
                .toJSONObjectSequence()
                .filter { it.getString("MyQDeviceTypeId") == "2" }
                .map {
                    object {
                        val definition = DeviceDefinition(
                                it.getString("MyQDeviceId"),
                                "com.autobridge.garageDoor",
                                getAttributeValueFunc(it, "desc")
                        )

                        val state = mapOf("openState" to if (getAttributeValueFunc(it, "doorstate") == "2") "Closed" else "Open")
                    }
                }
                .toList();

        this.listener.onDevicesDiscovered(
                this,
                deviceInfos.map { it.definition }.toList()
        );

        deviceInfos.forEach { deviceInfo ->
            deviceInfo.state.forEach {
                this.listener.onDeviceStateDiscovered(this, deviceInfo.definition.id, it.key, it.value);
            }
        }
    }

    override fun setDeviceState(deviceID: String, propertyName: String, propertyValue: String) {
        var attributeName = if (propertyName == "openState") "desireddoorstate" else throw IllegalArgumentException();
        var attributeValue = if (propertyValue == "Opened") "1" else if (propertyValue == "Closed") "0" else throw IllegalArgumentException();

        // TODO value could need to be actual Int rather than String according to reference

        this.performMyQRequestWithLoginHandling(
                "PUT",
                "DeviceAttribute/PutDeviceAttribute",
                mapOf(
                        //"ApplicationId" to applicationID,
                        //"SecurityToken" to this.parameters.state.optString("securityToken", ""),
                        "MyQDeviceId" to deviceID,
                        "AttributeName" to attributeName,
                        "AttributeValue" to attributeValue
                )
        );
    }

    private fun performMyQRequestWithLoginHandling(
            method: String,
            pathPart: String,
            bodyMap: Map<String, String>? = null
    ): JSONObject {
        while (true) {
            var responseObject = this.performMyQRequest(method, pathPart, bodyMap);

            if (responseObject.optString("error") != "-33336" && responseObject.optString("ReturnCode") != "216")
                return responseObject;

            var loginResponseObject = this.performMyQRequest(
                    "POST",
                    "User/Validate",
                    mapOf(
                            "username" to this.parameters.configuration.getString("username"),
                            "password" to this.parameters.configuration.getString("password")
                    )
            );

            this.parameters.state.put("securityToken", loginResponseObject.getString("SecurityToken"));

            Thread.sleep(4000); // TODO let's try to see if necessary?
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
            "/api/v4/" + pathPart,
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
    );
}

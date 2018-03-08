package com.autobridge.android

import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI


class MyQSourceRuntime(parameters: RuntimeParameters, listener: Listener) : PollingDeviceSourceRuntime(parameters, listener) {
    private val applicationID: String = "NWknvuBd7LoFHfXmKNMBcgajXtZEgKUh4V7WNzMidrpUUluDpVYVZx+xT4PCM5Kx";

    override fun poll() {
        var devices = this.performMyQHttpRequestWithLoginHandling("GET", "UserDeviceDetails/Get")
                .getJSONArray("Devices")
                .toJSONObjectSequence()
                .filter { it.getString("MyQDeviceTypeId") == "2" }
                .map {
                    object {
                        val deviceID = it.getString("MyQDeviceId")
                        val name = it.getJSONArray("Attributes")
                                .toJSONObjectSequence()
                                .first { it.getString("AttributeDisplayName") == "desc" }
                                .getString("Value")
                        val openState = it.getJSONArray("Attributes")
                                .toJSONObjectSequence()
                                .first { it.getString("AttributeDisplayName") == "doorstate" }
                                .getString("Value")
                    }
                }
                .toList();

        //this.listener.onDeviceStateChanged(this.parameters.id, )
    }

    override fun setDeviceState(deviceID: String, propertyName: String, propertyValue: String) {
        var attributeName = ""; // openState, Open or Closed
        var attributeValue = "";

        this.performMyQHttpRequestWithLoginHandling(
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

    private fun performMyQHttpRequestWithLoginHandling(method: String, pathPart: String, bodyMap: Map<String, String>? = null): JSONObject {
        while (true) {
            var responseObject = this.performMyQHttpRequest(method, pathPart, bodyMap);

            if (responseObject.optString("error") != "-33336")
                return responseObject;

            var loginResponseObject = this.performMyQHttpRequest(
                    "POST",
                    "User/Validate",
                    mapOf(
                            "username" to this.parameters.configuration.getString("username"),
                            "password" to this.parameters.configuration.getString("password")
                    )
            );

            this.parameters.state.put("securityToken", loginResponseObject.getString("SecurityToken"));
        }
    }

    private fun performMyQHttpRequest(method: String, pathPart: String, bodyMap: Map<String, String>? = null): JSONObject {
        return this.performJsonHttpRequest(
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
                mapOf(
                        //"appId" to applicationID,
                        //"filterOn" to "true", // not sure what this is
                        //"securityToken" to securityToken
                ),
                if (bodyMap == null) null else JSONObject(bodyMap)
        );
    }

    private fun performJsonHttpRequest(
            method: String,
            scheme: String,
            authority: String,
            path: String,
            headerMap: Map<String, String>,
            queryStringMap: Map<String, String>,
            bodyObject: JSONObject?
    ): JSONObject {
        with(URI(scheme, authority, path, queryStringMap.toQueryString(), null).toURL().openConnection() as HttpURLConnection) {
            try {
                requestMethod = method;
                doInput = true;

                headerMap.forEach { addRequestProperty(it.key, it.value); };

                if (bodyObject != null) {
                    addRequestProperty("Content-Type", "application/json");
                    outputStream.use { it.write(bodyObject.toString().toByteArray()); }
                }

                if (responseCode != 200)
                    throw IOException();

                (if (responseCode >= 400) errorStream else inputStream).use { return JSONObject(InputStreamReader(it).readText()) }
            } finally {
                disconnect();
            }
        }
    }
}

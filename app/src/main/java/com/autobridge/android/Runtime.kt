package com.autobridge.android

import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.*

class DeviceSource

class DeviceTarget

class BridgeRuntime

class RuntimeParameters(val id: String, val configuration: JSONObject, val state: JSONObject);

abstract class RuntimeBase(val parameters: RuntimeParameters) {
    open fun start() {}
    open fun stop() {}
    open fun processExternalCommand(commandName: String, commandArgument: Any?) {}
}

abstract class DeviceSourceRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun setDeviceState(deviceID: String, propertyName: String, propertyValue: String);

    interface Listener {
        fun onDeviceStateChanged(runtimeID: String, deviceID: String, propertyName: String, propertyValue: String);
        fun onDeviceCollectionChanged();
    }
}

abstract class DeviceTargetRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun syncDevices(sourceRuntimeID: String);
    abstract fun syncDeviceState(sourceRuntimeID: String, deviceID: String, propertyName: String, propertyValue: String);

    interface Listener {
        fun onDeviceSyncRequest(runtimeID: String);
        fun onDeviceStateChangeRequest(runtimeID: String, deviceID: String, propertyName: String, propertyValue: String);
    }
}

abstract class PollingDeviceSourceRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceSourceRuntime(parameters, listener) {
    private val timer = Timer();

    override fun start() {
        this.timer.schedule(object : TimerTask() {
            override fun run() {
                poll();
            }
        }, 10_000)
    }

    override fun stop() {
        this.timer.cancel();
    }

    abstract fun poll();
}

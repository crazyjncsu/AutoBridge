package com.autobridge.android

import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.*

data class DeviceDefinition(val id: String, val type: DeviceType, val name: String)

data class RuntimeParameters(val id: String, val configuration: JSONObject, val state: JSONObject);

abstract class RuntimeBase(val parameters: RuntimeParameters) {
    open fun start() {}
    open fun stop() {}
    open fun processMessage(message: JSONObject) {}
}

abstract class DeviceSourceRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun startDiscoverDevices();
    abstract fun startSetDeviceState(deviceID: String, propertyName: String, propertyValue: String);

    interface Listener {
        fun onDeviceStateDiscovered(sourceRuntime: DeviceSourceRuntime, deviceID: String, propertyName: String, propertyValue: String);
        fun onDevicesDiscovered(sourceRuntime: DeviceSourceRuntime, devices: List<DeviceDefinition>);
    }
}

abstract class DeviceTargetRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun syncSources(sourceIDs: List<String>);
    abstract fun syncSourceDevices(sourceID: String, devices: List<DeviceDefinition>);
    abstract fun syncDeviceState(sourceID: String, deviceID: String, propertyName: String, propertyValue: String);

    interface Listener {
        fun onDeviceSyncRequest(targetRuntime: DeviceTargetRuntime, sourceID: String);
        fun onDeviceStateChangeRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String, propertyName: String, propertyValue: String);
    }
}

abstract class PollingDeviceSourceRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceSourceRuntime(parameters, listener) {
    private val timer = Timer();

    override fun start() = this.timer.schedule(
            object : TimerTask() {
                override fun run() {
                    tryLog { poll() }
                };
            },
            0,
            20_000
    )

    override fun stop() = this.timer.cancel();

    abstract fun poll();
}

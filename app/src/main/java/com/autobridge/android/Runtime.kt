package com.autobridge.android

import android.content.Context
import org.json.JSONObject
import java.util.*

class DeviceDefinition(val id: String, val type: DeviceType, val name: String)

open class RuntimeParameters(val id: String, val configuration: JSONObject, val state: JSONObject)

abstract class RuntimeBase(val parameters: RuntimeParameters) {
    open fun startOrStop(startOrStop: Boolean, context: Context) {}
    open fun processMessage(message: JSONObject) {}
}

abstract class DeviceSourceRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun startDiscoverDevices()
    abstract fun startDiscoverDeviceState(deviceID: String)
    abstract fun startSetDeviceState(deviceID: String, propertyName: String, propertyValue: String)

    interface Listener {
        fun onDeviceStateDiscovered(sourceRuntime: DeviceSourceRuntime, deviceID: String, propertyName: String, propertyValue: String)
        fun onDevicesDiscovered(sourceRuntime: DeviceSourceRuntime, devices: List<DeviceDefinition>)
    }
}

abstract class DeviceTargetRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun startSyncSources(sourceIDs: List<String>)
    abstract fun startSyncSourceDevices(sourceID: String, devices: List<DeviceDefinition>)
    abstract fun startSyncDeviceState(sourceID: String, deviceID: String, propertyName: String, propertyValue: String)

    interface Listener {
        fun onDeviceSyncRequest(targetRuntime: DeviceTargetRuntime, sourceID: String)
        fun onDeviceRefreshRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String)
        fun onDeviceStateChangeRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String, propertyName: String, propertyValue: String)
    }
}

abstract class PollingDeviceSourceRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceSourceRuntime(parameters, listener) {
    private val timer = Timer()

    private val task = object : TimerTask() {
        override fun run() {
            tryLog { poll() }
        }
    }

    override fun startOrStop(startOrStop: Boolean, context: Context) {
        if (startOrStop)
            this.timer.schedule(this.task, 0, 30_000)
        else
            this.timer.cancel()
    }

    abstract fun poll()
}

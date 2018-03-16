package com.autobridge.android

import android.content.Context
import android.hardware.Sensor
import org.json.JSONObject
import java.util.*

class DeviceDefinition(val id: String, val type: DeviceType, val name: String)

open class RuntimeParameters(val id: String, val configuration: JSONObject, val state: JSONObject)
open class DeviceRuntimeParameters(id: String, configuration: JSONObject, state: JSONObject, val name: String) : RuntimeParameters(id, configuration, state)

abstract class RuntimeBase(val parameters: RuntimeParameters) {
    open fun startOrStop(startOrStop: Boolean, context: Context) {}
    open fun processMessage(message: JSONObject) {}
}

abstract class DeviceRuntime(parameters: DeviceRuntimeParameters, val listener: Listener, val deviceType: DeviceType) : RuntimeBase(parameters) {
    interface Listener {
        fun onStateDiscovered(deviceRuntime: DeviceRuntime, propertyName: String, propertyValue: String)
    }

    abstract fun startDiscoverState()
    abstract fun startSetState(propertyName: String, propertyValue: String)
}

abstract class DeviceSourceRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun startDiscoverDevices()
    abstract fun startDiscoverDeviceState(deviceID: String)
    abstract fun startSetDeviceState(deviceID: String, propertyName: String, propertyValue: String)

    interface Listener {
        fun onDevicesDiscovered(sourceRuntime: DeviceSourceRuntime, devices: List<DeviceDefinition>)
        fun onDeviceStateDiscovered(sourceRuntime: DeviceSourceRuntime, deviceID: String, deviceType: DeviceType, propertyName: String, propertyValue: String)
    }
}

abstract class ConfigurationDeviceSourceRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceSourceRuntime(parameters, listener), DeviceRuntime.Listener {
    private val deviceRuntimes = this.parameters.configuration
            .getJSONArray("devices")
            .toJSONObjectSequence()
            .map {
                this.createDeviceRuntime(
                        it.getString("id"),
                        it.optString("type"),
                        DeviceRuntimeParameters(
                                it.getString("id"),
                                it.getJSONObject("configuration"),
                                JSONObject(),
                                it.getString("name")
                        ),
                        this
                )
            }
            .toList()

    override fun startOrStop(startOrStop: Boolean, context: Context) =
            this.deviceRuntimes.forEach { it.startOrStop(startOrStop, context) }

    override fun onStateDiscovered(deviceRuntime: DeviceRuntime, propertyName: String, propertyValue: String) =
            this.listener.onDeviceStateDiscovered(this, deviceRuntime.parameters.id, deviceRuntime.deviceType, propertyName, propertyValue)

    override fun startDiscoverDevices() {
        asyncTryLog {
            this@ConfigurationDeviceSourceRuntime.listener.onDevicesDiscovered(
                    this@ConfigurationDeviceSourceRuntime,
                    this@ConfigurationDeviceSourceRuntime.deviceRuntimes
                            .map { DeviceDefinition(it.parameters.id, it.deviceType, (it.parameters as DeviceRuntimeParameters).name) }
                            .toList()
            )
        }
    }

    override fun startDiscoverDeviceState(deviceID: String) =
            this.deviceRuntimes
                    .filter { it.parameters.id == deviceID }
                    .forEach { it.startDiscoverState() }


    override fun startSetDeviceState(deviceID: String, propertyName: String, propertyValue: String) =
            this.deviceRuntimes
                    .filter { it.parameters.id == deviceID }
                    .forEach { it.startSetState(propertyName, propertyValue) }

    protected abstract fun createDeviceRuntime(deviceID: String, deviceType: String?, parameters: DeviceRuntimeParameters, listener: DeviceRuntime.Listener): DeviceRuntime;
}

abstract class DeviceTargetRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun startSyncSources(sourceIDs: List<String>)
    abstract fun startSyncSourceDevices(sourceID: String, devices: List<DeviceDefinition>)
    abstract fun startSyncDeviceState(sourceID: String, deviceID: String, deviceType: DeviceType, propertyName: String, propertyValue: String)

    interface Listener {
        fun onDevicesSyncRequest(targetRuntime: DeviceTargetRuntime, sourceID: String)
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
            this.timer.schedule(this.task, 0, 600_000)
        else
            this.timer.cancel()
    }

    abstract fun poll()
}

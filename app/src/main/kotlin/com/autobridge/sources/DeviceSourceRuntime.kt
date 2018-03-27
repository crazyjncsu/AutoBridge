package com.autobridge.sources

import android.content.Context
import com.android.*
import com.autobridge.*
import org.json.JSONObject
import java.util.*

abstract class DeviceSourceRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun startDiscoverDevices(shouldIncludeState: Boolean)
    abstract fun startDiscoverDeviceState(deviceID: String)
    abstract fun startSetDeviceState(deviceID: String, propertyName: String, propertyValue: String)

    interface Listener {
        fun onRejuvenated(sourceRuntime: DeviceSourceRuntime)
        fun onDevicesDiscovered(sourceRuntime: DeviceSourceRuntime, devices: List<DeviceDefinition>)
        fun onDeviceStateDiscovered(sourceRuntime: DeviceSourceRuntime, deviceID: String, deviceType: DeviceType, propertyName: String, propertyValue: String)
    }
}

open class DeviceRuntimeParameters(id: String, configuration: JSONObject, state: JSONObject, val name: String) : RuntimeParameters(id, configuration, state)

abstract class DeviceRuntime(parameters: DeviceRuntimeParameters, val listener: Listener, val deviceType: DeviceType) : RuntimeBase(parameters) {
    interface Listener {
        fun onStateDiscovered(deviceRuntime: DeviceRuntime, propertyName: String, propertyValue: String)
    }

    abstract fun startDiscoverState()
    abstract fun startSetState(propertyName: String, propertyValue: String)
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
                                this.getOrCreateState(it.getString("id")),
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

    override fun startDiscoverDevices(shouldIncludeState: Boolean) {
        asyncTryLog {
            this@ConfigurationDeviceSourceRuntime.listener.onDevicesDiscovered(
                    this@ConfigurationDeviceSourceRuntime,
                    this@ConfigurationDeviceSourceRuntime.deviceRuntimes
                            .map { DeviceDefinition(it.parameters.id, it.deviceType, (it.parameters as DeviceRuntimeParameters).name) }
                            .toList()
            )

            if (shouldIncludeState)
                this@ConfigurationDeviceSourceRuntime.deviceRuntimes.forEach { it.startDiscoverState() }
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

    protected abstract fun createDeviceRuntime(deviceID: String, deviceType: String?, parameters: DeviceRuntimeParameters, listener: DeviceRuntime.Listener): DeviceRuntime
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

package com.autobridge.android

import android.content.Context
import org.json.JSONObject

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

package com.autobridge.targets

import com.autobridge.DeviceDefinition
import com.autobridge.DeviceType
import com.autobridge.RuntimeBase
import com.autobridge.RuntimeParameters

abstract class DeviceTargetRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun startSyncSources(sourceIDs: List<String>)
    abstract fun startSyncSourceDevices(sourceID: String, devices: List<DeviceDefinition>)
    abstract fun startSyncDeviceState(sourceID: String, deviceID: String, deviceType: DeviceType, propertyName: String, propertyValue: String)

    interface Listener {
        fun onRejuvenated(targetRuntime: DeviceTargetRuntime)
        fun onSyncError(targetRuntime: DeviceTargetRuntime, mayNeedDiscovery: Boolean)
        fun onDevicesSyncRequest(targetRuntime: DeviceTargetRuntime, sourceID: String)
        fun onDeviceRefreshRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String)
        fun onDeviceStateChangeRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String, propertyName: String, propertyValue: String)
    }
}
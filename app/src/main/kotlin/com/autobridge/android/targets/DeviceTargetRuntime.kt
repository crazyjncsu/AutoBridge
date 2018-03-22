package com.autobridge.android.targets

import com.autobridge.android.DeviceDefinition
import com.autobridge.android.DeviceType
import com.autobridge.android.RuntimeBase
import com.autobridge.android.RuntimeParameters

abstract class DeviceTargetRuntime(parameters: RuntimeParameters, val listener: Listener) : RuntimeBase(parameters) {
    abstract fun startSyncSources(sourceIDs: List<String>)
    abstract fun startSyncSourceDevices(sourceID: String, devices: List<DeviceDefinition>)
    abstract fun startSyncDeviceState(sourceID: String, deviceID: String, deviceType: DeviceType, propertyName: String, propertyValue: String)

    interface Listener {
        fun onRejuvenated(targetRuntime: DeviceTargetRuntime);
        fun onSyncError(targetRuntime: DeviceTargetRuntime, mayNeedDiscovery: Boolean)
        fun onDevicesSyncRequest(targetRuntime: DeviceTargetRuntime, sourceID: String)
        fun onDeviceRefreshRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String)
        fun onDeviceStateChangeRequest(targetRuntime: DeviceTargetRuntime, sourceID: String, deviceID: String, propertyName: String, propertyValue: String)
    }
}
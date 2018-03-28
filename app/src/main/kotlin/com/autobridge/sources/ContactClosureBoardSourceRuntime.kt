package com.autobridge.sources

import com.android.*
import com.autobridge.DeviceType
import com.autobridge.RuntimeParameters

abstract class ContactClosureBoardSourceRuntime(parameters: RuntimeParameters, listener: Listener) : ConfigurationDeviceSourceRuntime(parameters, listener) {
    override fun createDeviceRuntime(deviceID: String, deviceType: String?, parameters: DeviceRuntimeParameters, listener: DeviceRuntime.Listener): DeviceRuntime =
            when (deviceType) {
                DeviceType.DOOR_OPENER.ocfDeviceType -> DoorOpenerDeviceRuntime(parameters)
                DeviceType.LIGHT.ocfDeviceType -> LightDeviceRuntime(parameters)
                else -> throw IllegalArgumentException()
            }

    protected abstract fun tryGetContactStateAsync(contactID: String, callback: (openOrClosed: Boolean) -> Unit)
    protected abstract fun trySetContactStateAsync(contactID: String, openOrClosed: Boolean, callback: () -> Unit)

    inner class DoorOpenerDeviceRuntime(parameters: DeviceRuntimeParameters) : DeviceRuntime(parameters, this@ContactClosureBoardSourceRuntime, DeviceType.DOOR_OPENER) {
        private val openStatePropertyName = this.deviceType.resourceTypes[0].propertyNames[0]
        private fun getOpenState() = this.parameters.state.optString("openState", "Closed")
        private fun isOpen(openState: String) = openState == "Open"

        override fun startDiscoverState() =
                this.onOpenStateDiscovered(this.getOpenState())

        override fun startSetState(propertyName: String, propertyValue: String) {
            val openState = this.getOpenState()
            val isOpen = isOpen(openState)
            val contactID = this.parameters.configuration.getString(if (isOpen) "closeContactID" else "openContactID")

            // TODO work on something to make sure two of these can't run at once
            if (propertyValue != openState)
                this@ContactClosureBoardSourceRuntime.trySetContactStateAsync(contactID, false) {
                    Thread.sleep(this.parameters.configuration.getInt(if (isOpen(openState)) "closeDurationSeconds" else "openDurationSeconds") * 1000L)
                    this@ContactClosureBoardSourceRuntime.trySetContactStateAsync(contactID, true) {
                        val newOpenState = if (isOpen) "Closed" else "Open"
                        this.parameters.state.put(this.openStatePropertyName, newOpenState)
                        this.onOpenStateDiscovered(newOpenState)
                    }
                }
        }

        private fun onOpenStateDiscovered(openState: String) {
            this.listener.onStateDiscovered(this, this.openStatePropertyName, openState)
            this.listener.onStateDiscovered(this, "value", if (openState == "Open") "true" else "false")
        }
    }

    inner class LightDeviceRuntime(parameters: DeviceRuntimeParameters) : DeviceRuntime(parameters, this@ContactClosureBoardSourceRuntime, DeviceType.LIGHT) {
        private val contactID = this.parameters.configuration.getString("contactID")

        override fun startDiscoverState() =
                this@ContactClosureBoardSourceRuntime.tryGetContactStateAsync(contactID) {
                    this.listener.onStateDiscovered(this, this.deviceType.resourceTypes[0].propertyNames[0], if (it) "false" else "true")
                }

        override fun startSetState(propertyName: String, propertyValue: String) =
                this@ContactClosureBoardSourceRuntime.trySetContactStateAsync(contactID, propertyValue == "false") {
                    this.listener.onStateDiscovered(this, propertyName, propertyValue)
                }
    }
}
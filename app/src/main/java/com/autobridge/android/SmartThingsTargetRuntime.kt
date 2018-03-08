package com.autobridge.android

class SmartThingsTargetRuntime(parameters: RuntimeParameters, listener: Listener) : DeviceTargetRuntime(parameters, listener) {
    override fun processExternalCommand(commandName: String, commandArgument: Any?) {
        when {
            commandName == "SetCallbackUrl" && commandArgument is String -> this.parameters.state.put("callbackUrl", commandArgument)
            else -> throw IllegalArgumentException()
        }
    }

    override fun syncDeviceState(sourceRuntimeID: String, deviceID: String, propertyName: String, propertyValue: String) {
        
    }
}

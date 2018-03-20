package com.autobridge.android

import android.content.Context
import org.json.JSONObject

class DeviceDefinition(val id: String, val type: DeviceType, val name: String)

open class RuntimeParameters(val id: String, val configuration: JSONObject, val state: JSONObject)

abstract class RuntimeBase(val parameters: RuntimeParameters) {
    open fun startOrStop(startOrStop: Boolean, context: Context) {}
    open fun processMessage(message: JSONObject) {}
}
